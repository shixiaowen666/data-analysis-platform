"""
Function Dispatcher
====================
Routes computation operations to the correct function with resolved parameters.
Includes column name fuzzy matching to handle LLM-generated column name mismatches.
"""

import logging
import re
from difflib import SequenceMatcher
from typing import Any, Optional

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata
from system_b.models.exceptions import ComputationError
from system_b.computation.function_registry import get_function, get_function_info
from system_b.execution.reference_resolver import ContextStore, resolve_reference, resolve_input_param

logger = logging.getLogger(__name__)


def _find_best_column_match(col_name: str, available_columns: list[str], threshold: float = 0.5) -> Optional[str]:
    """Find the best matching column name from available columns using fuzzy matching."""
    if col_name in available_columns:
        return col_name
    
    # Exact case-insensitive match
    lower_map = {c.lower(): c for c in available_columns}
    if col_name.lower() in lower_map:
        return lower_map[col_name.lower()]
    
    # Substring matching (e.g., 'load_rate' matches 'avg_load_rate')
    for ac in available_columns:
        if col_name.lower() in ac.lower() or ac.lower() in col_name.lower():
            return ac
    
    # Fuzzy matching using SequenceMatcher
    best_match = None
    best_score = 0
    for ac in available_columns:
        score = SequenceMatcher(None, col_name.lower(), ac.lower()).ratio()
        if score > best_score:
            best_score = score
            best_match = ac
    
    if best_match and best_score >= threshold:
        logger.info(f"[Dispatcher] Column fuzzy match: '{col_name}' -> '{best_match}' (score={best_score:.2f})")
        return best_match
    
    return None


def _fix_column_refs_in_sdf(sdf: StandardDataFrame, expected_col: str) -> str:
    """Try to find the actual column name in the SDF for an expected column name.

    Resolution order:
    1. exact name
    2. source_ref match (a rename by aggregate/join/ratio keeps the original
       name in ColumnDef.source_ref — this is an EXACT semantic match and must
       win over any fuzzy guess). Fixes 问题10/问题12 where sort_by referenced
       the pre-aggregation column name (e.g. 'monthpowersupply') after
       aggregate renamed the measure.
    3. fuzzy matching
    """
    if sdf.has_column(expected_col):
        return expected_col
    # source_ref exact match (renamed columns keep their original name here)
    for c in sdf.columns:
        if c.source_ref and c.source_ref == expected_col:
            logger.info(
                f"[Dispatcher] Column '{expected_col}' resolved via source_ref -> '{c.name}'"
            )
            return c.name
    available = [c.name for c in sdf.columns]
    match = _find_best_column_match(expected_col, available)
    if match:
        logger.info(f"[Dispatcher] Auto-corrected column reference: '{expected_col}' -> '{match}'")
        return match
    return expected_col  # Return original if no match found


def _resolve_chinese_column_name(resolved_inputs: dict, fallback: str) -> str:
    """从输入 SDF 的列描述中获取中文列名，用于 source_operation"""
    data_sdf = resolved_inputs.get('data')
    if not isinstance(data_sdf, StandardDataFrame):
        return fallback

    # 找到第一个列参数值
    column = None
    for cp in ('column', 'value_column', 'a', 'sort_by', 'columns'):
        val = resolved_inputs.get(cp)
        if isinstance(val, str) and val and ',' not in val:
            column = val
            break
    if column is None:
        return fallback

    col_def = data_sdf.get_column_def(column)
    if col_def and col_def.description:
        # 剥离聚合描述的 "= 数值" 尾部（如 "医疗收入的求和 = 46092857.96" -> "医疗收入的求和"）
        desc = re.split(r"\s*=\s*", col_def.description, maxsplit=1)[0].strip()
        if desc:
            logger.info(
                f"[Dispatcher] source_operation: '{fallback}' -> '{desc}'"
            )
            return desc
    return fallback


# Functions whose ``output`` parameter names the NEW COLUMN they append (a
# fixed, documented column name from prompt rule 13) — NOT the operation
# output alias. For these, the dispatcher must NOT overwrite ``output`` with
# the plan's operation name, otherwise the appended column gets an arbitrary
# name (e.g. 'monthly_data') and downstream group_by/sort_by references to the
# canonical name ('year_month') break. Root cause of 问题2/问题11.
_CANONICAL_COLUMN_FUNCS = {
    "extract_year_month": "year_month",
    "extract_year": "year",
    "extract_month": "month",
    "extract_week": "week",
    "extract_weekday": "weekday_type",
    "cumulative_sum": "cumulative_sum",
    # 0723 问题16/21/96 fix: mark_holidays 的 output 参数是新增列名
    # (固定文档名 day_type)。之前 dispatcher 把它覆盖成 operation 名
    # (如 'with_holiday_flag')，导致下游 filter(column='day_type') 找不到列、
    # 又被模糊纠错成 ptdate ("Filtered by ptdate = 工作日") → 0 行。
    "mark_holidays": "day_type",
}

# Pseudo time dimensions listed in DB metadata (ptweek()/ptyear()/...) that are
# NOT real queryable columns. They must never be fuzzy-corrected to a real
# column (ptweek -> ptdate scored exactly 0.5 and silently changed weekly
# aggregation into daily aggregation — root cause of 问题8). Instead the
# aggregate function derives them from ptdate.
_PSEUDO_TIME_DIMS = {"ptweek", "ptyear", "ptmonth", "ptquarter", "ptdate"}


class FunctionDispatcher:
    """
    Dispatches computation operations based on step decomposition definitions.
    Resolves references, calls functions, and packages results.
    """

    def __init__(self, context: ContextStore):
        self.context = context

    def execute_operation(
        self,
        step_id: str,
        operation: dict,
        depends_on: Optional[list] = None,
    ) -> StandardDataFrame:
        """
        Execute a single computation operation.

        Args:
            step_id: Current step ID
            operation: Operation dict with function, inputs, output, output_unit
            depends_on: The step's depends_on list (fallback source for a
                missing required ``data`` input — 错误问题汇总 问题44 fix)
        """
        func_name = operation.get("function")
        # Copy so we can safely pop misplaced "output"/"output_unit" entries
        # that LLM sometimes embeds inside inputs (they must not be forwarded
        # to the target function as kwargs, and must still act as the real
        # output name / unit).
        inputs = dict(operation.get("inputs", {}))

        # Priority: top-level -> inputs-level -> default.
        # Keep the inputs-level name as an ALIAS when both exist and differ:
        # LLM plans sometimes set inputs.output='monthly_total' AND top-level
        # output='monthly_agg', then downstream steps reference EITHER name.
        # Discarding the inner name broke those references (问题9).
        inner_output = inputs.pop("output", None)
        output_name = operation.get("output")
        if output_name is None:
            output_name = inner_output
            inner_output = None
        if output_name is None:
            output_name = "result"
        alias_output = inner_output if (inner_output and inner_output != output_name) else None

        output_unit = operation.get("output_unit")
        if output_unit is None:
            output_unit = inputs.pop("output_unit", None)
        else:
            inputs.pop("output_unit", None)

        logger.info(f"[Dispatcher] Executing: {func_name} -> {output_name}")

        # =====================================================================
        # 新问题3/新问题4 fix: LLM YoY plans frequently write
        #   subtract(col_a="step_4.combined_yoy.daypowersupply",
        #            col_b="step_4.combined_yoy.daypowersupply")
        # i.e. the SAME reference on both sides — the intent is
        # "current-period column minus base-period column", but after a join
        # the base-period column was renamed (monthly_2025 /
        # daypowersupply_right) and the plan still repeats the original name.
        # Subtracting a column from itself is always 0 and never meaningful,
        # so rewrite col_a/col_b to the FIRST TWO columns of the referenced
        # dataset that match the name (exact name, `_right` rename, or
        # source_ref). Column order puts the left/current side first.
        # =====================================================================
        if func_name in ("subtract", "divide", "add", "multiply"):
            _ca, _cb = inputs.get("col_a"), inputs.get("col_b")
            if (isinstance(_ca, str) and _ca == _cb
                    and _ca.startswith("step_") and "." in _ca):
                _parts = _ca.split(".")
                _col = _parts[-1]
                _ds_ref = ".".join(_parts[:-1])
                try:
                    _base = resolve_reference(_ds_ref, self.context)
                except Exception:
                    _base = None
                if isinstance(_base, StandardDataFrame):
                    import re as _re
                    _cands = []
                    for c in _base.columns:
                        if (c.name == _col
                                or (c.source_ref and c.source_ref == _col)
                                or _re.match(rf"^{_re.escape(_col)}_right\d*$", c.name)
                                or (c.source_ref and _re.match(
                                    rf"^{_re.escape(_col)}_right\d*$", c.source_ref))):
                            _cands.append(c.name)
                    if len(_cands) < 2:
                        # Fallback: the referenced dataset is typically a
                        # joined current-vs-base wide table. If it has
                        # exactly two measure columns, the self-arithmetic
                        # unambiguously means "measure1 <op> measure2"
                        # (e.g. monthly_2026 - monthly_2025 after a
                        # year_month join renamed both sides).
                        _measures = [c.name for c in _base.columns
                                     if c.role == "measure"]
                        if len(_measures) == 2:
                            # Keep the referenced column first if it is one
                            # of the two measures (current period first).
                            if _col in _measures and _measures[0] != _col:
                                _measures = [_col] + [m for m in _measures
                                                      if m != _col]
                            _cands = _measures
                    if len(_cands) >= 2:
                        inputs["col_a"] = f"{_ds_ref}.{_cands[0]}"
                        inputs["col_b"] = f"{_ds_ref}.{_cands[1]}"
                        logger.warning(
                            f"[Dispatcher] {func_name}: col_a == col_b "
                            f"('{_ca}') would be self-{func_name}; rewritten "
                            f"to col_a='{inputs['col_a']}', "
                            f"col_b='{inputs['col_b']}' (current vs base "
                            f"period columns)"
                        )

        # Look up function
        func = get_function(func_name)
        if func is None:
            raise ComputationError(f"Unknown function: {func_name}")

        func_info = get_function_info(func_name)

        # =====================================================================
        # 错误问题汇总 问题44 fix: LLM 计划偶尔漏写必填的 ``data`` 输入
        # (e.g. trend_analysis 只给了 value_column/time_column)，之前会直接抛
        # "missing 1 required positional argument: 'data'" 导致整个 step 失败、
        # 下游全部 0 行。这里做默认回退：
        #   1) 本 step 内上一个 operation 的输出（compute step 内 last-write-wins
        #      正好存于 step_outputs[step_id]）
        #   2) 唯一的 depends_on step 的输出
        # =====================================================================
        if (func_info and "data" in func_info.get("required_params", [])
                and "data" not in inputs):
            fallback_ref = None
            if step_id in self.context.step_outputs:
                # A previous operation in this same compute step already
                # produced an output — use it as the implicit data input.
                fallback_ref = self.context.step_outputs[step_id]
            elif depends_on:
                for dep in depends_on:
                    if dep in self.context.step_outputs:
                        fallback_ref = self.context.step_outputs[dep]
                        break
            if fallback_ref is not None:
                inputs["data"] = fallback_ref
                logger.warning(
                    f"[Dispatcher] {func_name}: required input 'data' missing "
                    f"from plan; defaulted to previous operation/step output "
                    f"(rows={fallback_ref.metadata.row_count})"
                )

        # Resolve all input parameters (current_step enables resolving bare
        # operation-output names produced earlier in the same compute step)
        resolved_inputs = {}
        for key, value in inputs.items():
            try:
                resolved = resolve_input_param(value, self.context, current_step=step_id, param_key=key)
                resolved_inputs[key] = resolved
            except Exception as e:
                logger.error(f"[Dispatcher] Failed to resolve input '{key}': {value} -> {e}")
                raise ComputationError(f"Failed to resolve input '{key}' = '{value}': {e}")

        # Add output and output_unit to params.
        # For canonical-column functions (extract_year_month etc.), the
        # function's ``output`` parameter is the NEW COLUMN name with a fixed
        # documented default — do NOT overwrite it with the operation name
        # (问题2/问题11 root cause). The operation name is only used for
        # context storage / lineage.
        #
        # 新问题2 fix: when the plan supplies BOTH an inputs-level output
        # ('monthly_daypowersupply') AND a top-level output
        # ('aggregated_monthly'), the inputs-level name is what downstream
        # operations reference as a COLUMN (shift's column=, sort_by=, ...),
        # while the top-level name is the operation/dataset alias. Previously
        # only dataset-level aliasing was handled (问题9 fix) but the COLUMN
        # produced by aggregate/arithmetic still took the top-level name, so
        # shift(column='monthly_daypowersupply') found nothing and every
        # shifted value became None. Use the inner name as the column name;
        # both names remain valid dataset references via the alias store.
        if func_name in _CANONICAL_COLUMN_FUNCS:
            resolved_inputs["output"] = _CANONICAL_COLUMN_FUNCS[func_name]
        elif alias_output:
            resolved_inputs["output"] = alias_output
        else:
            resolved_inputs["output"] = output_name
        if output_unit:
            resolved_inputs["output_unit"] = output_unit

        # Column name correction: for functions that reference column names,
        # check if the referenced columns exist in the data and try to auto-correct
        column_params = ['column', 'sort_by', 'value_column', 'time_column',
                        'start_column', 'end_column']
        for cp in column_params:
            if cp in resolved_inputs and isinstance(resolved_inputs[cp], str):
                # BUGFIX (issue #9): If the value is a comma-separated list of
                # column names (LLM-generated multi-column aggregation), do NOT
                # fuzzy-match the whole string to one column - the called
                # function will handle the list itself.
                raw_val = resolved_inputs[cp]
                if "," in raw_val:
                    continue
                # Find the data SDF in resolved_inputs
                data_sdf = resolved_inputs.get('data')
                if data_sdf is None:
                    # Some functions use other param names for data
                    for dk in ['current_data', 'left', 'right']:
                        if dk in resolved_inputs and isinstance(resolved_inputs[dk], StandardDataFrame):
                            data_sdf = resolved_inputs[dk]
                            break
                if isinstance(data_sdf, StandardDataFrame):
                    corrected = _fix_column_refs_in_sdf(data_sdf, raw_val)
                    if corrected != raw_val:
                        logger.info(f"[Dispatcher] Column '{raw_val}' corrected to '{corrected}' for param '{cp}'")
                        resolved_inputs[cp] = corrected

        # Also fix group_by column references.
        # IMPORTANT: pseudo time dimensions (ptweek/ptyear/...) are NEVER
        # fuzzy-corrected — ptweek fuzzy-matched ptdate (score 0.5) and
        # silently turned weekly aggregation into daily aggregation (问题8).
        # They are passed through unchanged so aggregate._resolve_group_by can
        # derive them from the real date column.
        if 'group_by' in resolved_inputs and isinstance(resolved_inputs['group_by'], list):
            data_sdf = resolved_inputs.get('data')
            if isinstance(data_sdf, StandardDataFrame):
                new_group_by = []
                for g in resolved_inputs['group_by']:
                    if isinstance(g, str):
                        if g.lower() in _PSEUDO_TIME_DIMS and not data_sdf.has_column(g):
                            logger.info(
                                f"[Dispatcher] group_by '{g}' is a pseudo time "
                                f"dimension; passing through for derivation"
                            )
                            new_group_by.append(g)
                            continue
                        corrected = _fix_column_refs_in_sdf(data_sdf, g)
                        new_group_by.append(corrected)
                    else:
                        new_group_by.append(g)
                resolved_inputs['group_by'] = new_group_by

        # Fix join "on" keys: must exist in BOTH left and right SDFs
        if func_name == 'join' and 'on' in resolved_inputs:
            left_sdf = resolved_inputs.get('left')
            right_sdf = resolved_inputs.get('right')
            on_val = resolved_inputs['on']
            if isinstance(on_val, str):
                on_val = [on_val]
            if isinstance(on_val, list) and isinstance(left_sdf, StandardDataFrame) \
                    and isinstance(right_sdf, StandardDataFrame):
                new_on = []
                for key in on_val:
                    if not isinstance(key, str):
                        new_on.append(key)
                        continue
                    # Only auto-correct if the key is missing on at least one side
                    if left_sdf.has_column(key) and right_sdf.has_column(key):
                        new_on.append(key)
                        continue
                    # Try to find a name that exists on both sides
                    left_cand = _fix_column_refs_in_sdf(left_sdf, key)
                    right_cand = _fix_column_refs_in_sdf(right_sdf, key)
                    if left_cand == right_cand and left_sdf.has_column(left_cand) \
                            and right_sdf.has_column(right_cand):
                        if left_cand != key:
                            logger.info(
                                f"[Dispatcher] join 'on' key '{key}' "
                                f"corrected to '{left_cand}' (both sides)"
                            )
                        new_on.append(left_cand)
                    else:
                        # Fallback: pick any name present on both sides
                        common = (set(c.name for c in left_sdf.columns)
                                  & set(c.name for c in right_sdf.columns))
                        if common:
                            # Prefer dimension columns
                            dim_common = [n for n in common
                                          if (left_sdf.get_column_def(n)
                                              and left_sdf.get_column_def(n).role == "dimension")]
                            chosen = dim_common[0] if dim_common else list(common)[0]
                            logger.warning(
                                f"[Dispatcher] join 'on' key '{key}' not present "
                                f"on both sides; using '{chosen}' from intersection"
                            )
                            new_on.append(chosen)
                        else:
                            new_on.append(key)
                resolved_inputs['on'] = new_on

        # Fix operation_params for group_apply
        if func_name == 'group_apply' and 'operation_params' in resolved_inputs:
            data_sdf = resolved_inputs.get('data')
            if isinstance(data_sdf, StandardDataFrame) and isinstance(resolved_inputs['operation_params'], dict):
                op_params = resolved_inputs['operation_params']
                for cp in ['sort_by', 'column', 'value_column']:
                    if cp in op_params and isinstance(op_params[cp], str):
                        corrected = _fix_column_refs_in_sdf(data_sdf, op_params[cp])
                        if corrected != op_params[cp]:
                            logger.info(f"[Dispatcher] operation_params.{cp} corrected: '{op_params[cp]}' -> '{corrected}'")
                            op_params[cp] = corrected

        # Execute function
        try:
            result = func(**resolved_inputs)
        except Exception as e:
            logger.error(f"[Dispatcher] Function {func_name} failed: {e}", exc_info=True)
            raise ComputationError(f"Function {func_name} execution error: {e}")

        # Ensure result is StandardDataFrame
        if not isinstance(result, StandardDataFrame):
            result = StandardDataFrame.from_scalar(
                value=result, name=output_name, unit=output_unit,
                source_step=step_id, source_operation=output_name,
            )

        # Update metadata
        result.metadata.source_step = step_id
        result.metadata.source_operation = _resolve_chinese_column_name(resolved_inputs, output_name)
        if step_id not in result.metadata.lineage:
            result.metadata.lineage.append(step_id)
        lineage_key = f"{step_id}.{output_name}"
        if lineage_key not in result.metadata.lineage:
            result.metadata.lineage.append(lineage_key)

        # Store in context (also under the inputs-level alias name when the
        # plan supplied a second name — downstream steps may reference either;
        # 问题9 fix).
        self.context.store(step_id, result, operation_name=output_name)
        if alias_output:
            self.context.operation_outputs[f"{step_id}.{alias_output}"] = result
            logger.info(
                f"[Dispatcher] Stored alias operation output: "
                f"{step_id}.{alias_output} -> {step_id}.{output_name}"
            )

        logger.info(f"[Dispatcher] {func_name} -> {output_name}: {result.metadata.row_count} rows")
        return result
