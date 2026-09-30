"""
Sorting and Filtering Functions: top_n, bottom_n, filter_data, rank
"""

import logging
import operator as op_module
import re
from typing import Any, Optional

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata

logger = logging.getLogger(__name__)

OPERATORS = {
    ">": op_module.gt,
    "<": op_module.lt,
    ">=": op_module.ge,
    "<=": op_module.le,
    "==": op_module.eq,
    "=": op_module.eq,
    "!=": op_module.ne,
    "<>": op_module.ne,
}

# Set-membership operators handled separately (issue #5).
_IN_OPS = {"in", "IN", "In"}
_NOT_IN_OPS = {"not_in", "not in", "NOT IN", "NOT_IN", "nin", "NotIn"}


def _like_match(row_val, pattern) -> bool:
    """SQL LIKE 匹配 (% = 任意长度, _ = 单字符)。错误问题汇总 问题46 fix:
    LLM 计划会生成 operator='not like', value='%-%-01' 这样的条件，之前
    OPERATORS 不支持 like 导致所有行返回 False、结果集被清空。"""
    if row_val is None or pattern is None:
        return False
    regex = re.escape(str(pattern)).replace("%", ".*").replace("_", ".")
    return re.fullmatch(regex, str(row_val)) is not None


def _to_comparable(val):
    """Convert value to comparable type."""
    if val is None:
        return float("-inf")
    try:
        if isinstance(val, str):
            val = val.replace(",", "")
        return float(val)
    except (ValueError, TypeError):
        return val


def _ensure_sort_column(data: StandardDataFrame, sort_by: str, func_label: str) -> str:
    """问题10/问题12 fix: resolve sort_by via exact name -> source_ref ->
    fuzzy match. Previously a missing sort_by silently sorted rows by -inf,
    returning ARBITRARY rows as the 'top/bottom' answer.
    """
    if data.has_column(sort_by):
        return sort_by
    from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
    corrected = _fix_column_refs_in_sdf(data, sort_by)
    if corrected != sort_by and data.has_column(corrected):
        logger.info(f"[{func_label}] sort_by '{sort_by}' corrected to '{corrected}'")
        return corrected
    # Last resort: sort by the last measure column instead of returning
    # arbitrary rows (all keys would be -inf otherwise).
    measures = data.get_measure_columns()
    if measures:
        logger.warning(
            f"[{func_label}] sort_by '{sort_by}' not found; "
            f"falling back to measure column '{measures[-1]}'"
        )
        return measures[-1]
    logger.warning(f"[{func_label}] sort_by '{sort_by}' not found and no measure column exists")
    return sort_by


def top_n(data: StandardDataFrame, sort_by: str, n: int, output: str = "top_n_result", **kwargs) -> StandardDataFrame:
    """Take top N rows sorted by sort_by column in descending order."""
    n = int(n)
    sort_by = _ensure_sort_column(data, sort_by, "top_n")
    sorted_rows = sorted(data.rows, key=lambda r: _to_comparable(r.get(sort_by)), reverse=True)
    result_rows = sorted_rows[:n]

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Top {n} by {sort_by}"
    cols = [c.copy() for c in data.columns]
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def bottom_n(data: StandardDataFrame, sort_by: str, n: int, output: str = "bottom_n_result", **kwargs) -> StandardDataFrame:
    """Take bottom N rows sorted by sort_by column in ascending order."""
    n = int(n)
    sort_by = _ensure_sort_column(data, sort_by, "bottom_n")
    sorted_rows = sorted(data.rows, key=lambda r: _to_comparable(r.get(sort_by)), reverse=False)
    # Filter out None values at the beginning
    non_none = [r for r in sorted_rows if r.get(sort_by) is not None]
    none_rows = [r for r in sorted_rows if r.get(sort_by) is None]
    result_rows = non_none[:n]

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Bottom {n} by {sort_by}"
    cols = [c.copy() for c in data.columns]
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def filter_data(
    data: StandardDataFrame,
    column: str,
    operator_str: str = None,
    value: Any = None,
    logic: str = "AND",
    conditions: Optional[list] = None,
    output: str = "filter_result",
    operator: str = None,
    **kwargs,
) -> StandardDataFrame:
    """
    Filter rows by condition(s).
    Single condition: column operator value
    Multiple conditions: use conditions array with logic (AND/OR)
    """
    # Handle both operator_str and operator parameter names
    if operator_str is None and operator is not None:
        operator_str = operator
    if operator_str is None:
        operator_str = "=="

    # Resolve value if it's a StandardDataFrame.
    # For set membership operators we want the full list of values; for
    # comparison operators we still need a scalar.
    is_set_op = isinstance(operator_str, str) and operator_str.strip().lower() in {
        "in", "not_in", "not in"
    }
    _value_from_sdf = isinstance(value, StandardDataFrame)
    if isinstance(value, StandardDataFrame):
        if value.metadata.is_scalar:
            value = value.get_scalar_value()
        elif is_set_op:
            # Use the first measure or dimension column's values as the set.
            measure_cols = value.get_measure_columns()
            dim_cols = value.get_dimension_columns()
            target_col = (measure_cols + dim_cols + [c.name for c in value.columns])[0] if value.columns else None
            if target_col:
                value = [r.get(target_col) for r in value.rows if r.get(target_col) is not None]
            else:
                value = []
        else:
            # Single/multi-row value with a scalar operator — degrade to a
            # scalar. 0723 问题30 fix: get_scalar_value() returns None when
            # metadata.is_scalar is False, which is exactly the shape of a
            # column subset like `step_3.most_volatile_week.week` (1 row,
            # 1 dimension column). Take the first non-null value of the
            # matching column (prefer a column with the same name as the
            # filter column, else last column) instead of silently None.
            scalar_v = value.get_scalar_value()
            if scalar_v is None and value.rows:
                pick_col = None
                if column and value.has_column(column):
                    pick_col = column
                else:
                    meas = value.get_measure_columns()
                    if meas:
                        pick_col = meas[-1]
                    elif value.columns:
                        pick_col = value.columns[-1].name
                if pick_col:
                    for r in value.rows:
                        if r.get(pick_col) is not None:
                            scalar_v = r.get(pick_col)
                            break
            value = scalar_v

    # Auto-correct column name if it doesn't exist in data.
    # 0723 问题16/21/96 fix: the fuzzy correction must be VALUE-AWARE for
    # equality/membership filters. Previously 'day_type' fuzzy-matched
    # 'ptdate' (score 0.5) and the filter became "ptdate = 工作日" -> 0 rows.
    # If the corrected column's values don't contain the filter value, the
    # correction is bogus; and if no usable column exists at all, DROP the
    # condition (pass rows through with a loud warning) instead of silently
    # emptying the result set via row.get(missing)=None.
    def _values_contain(col_name, val):
        """For =/in filters check whether val appears in the column's values."""
        vals = {str(r.get(col_name)) for r in data.rows if r.get(col_name) is not None}
        if isinstance(val, (list, tuple, set)):
            return any(str(v) in vals for v in val)
        return str(val) in vals

    _op_norm0 = str(operator_str).strip().lower() if operator_str else "=="
    _is_eq_like = _op_norm0 in ("=", "==", "in")
    available_cols = [c.name for c in data.columns]
    _column_dropped = False
    if column not in available_cols and not conditions:
        from system_b.computation.function_dispatcher import _find_best_column_match
        # source_ref exact match first (rename by aggregate/join keeps it)
        src_match = None
        for c in data.columns:
            if c.source_ref and c.source_ref == column:
                src_match = c.name
                break
        match = src_match or _find_best_column_match(column, available_cols)
        if match and (not _is_eq_like or isinstance(value, StandardDataFrame)
                      or _values_contain(match, value)):
            logger.info(f"[filter_data] Auto-corrected column: '{column}' -> '{match}'")
            column = match
        else:
            if match:
                logger.warning(
                    f"[filter_data] Fuzzy candidate '{match}' for missing column "
                    f"'{column}' rejected: value {value!r} not present in its "
                    f"values (would empty the result). Condition DROPPED."
                )
            else:
                logger.warning(
                    f"[filter_data] Column '{column}' not found in data "
                    f"(available: {available_cols}); condition DROPPED "
                    f"(rows pass through)."
                )
            _column_dropped = True
    elif column not in available_cols:
        from system_b.computation.function_dispatcher import _find_best_column_match
        match = _find_best_column_match(column, available_cols)
        if match:
            logger.info(f"[filter_data] Auto-corrected column: '{column}' -> '{match}'")
            column = match

    if _column_dropped:
        meta = Metadata.from_dict(data.metadata.to_dict())
        meta.description = (
            f"Filter skipped (column '{column}' unusable; condition dropped)"
        )
        cols = [c.copy() for c in data.columns]
        return StandardDataFrame(metadata=meta, columns=cols,
                                 rows=[dict(r) for r in data.rows])

    # 问题8/问题10 fix: LLM plans sometimes pass EXPRESSION STRINGS as filter
    # values, e.g. "max(step_3.volatility_by_week.change_amount)" or
    # "step_5.mean + step_5.std * 0.5". These are not evaluable references and
    # previously compared as strings, silently producing 0 rows. Try to
    # evaluate a simple agg(...) expression against the current data; if that
    # is impossible, DROP the condition with a loud warning instead of
    # emptying the result set. (The prompt now forbids such expressions.)
    def _try_eval_expression(v):
        if not isinstance(v, str):
            return v, True
        s = v.strip()
        m = re.match(r"^(max|min|mean|avg|sum)\((.+)\)$", s, re.IGNORECASE)
        if m:
            agg_method = m.group(1).lower()
            inner = m.group(2).strip()
            # Use the last dotted component as the column name
            col_candidate = inner.split(".")[-1]
            target = col_candidate
            if not data.has_column(target):
                from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
                target = _fix_column_refs_in_sdf(data, col_candidate)
            if data.has_column(target):
                vals = []
                for r in data.rows:
                    rv = r.get(target)
                    try:
                        vals.append(float(str(rv).replace(",", "")))
                    except (ValueError, TypeError):
                        pass
                if vals:
                    if agg_method == "max":
                        return max(vals), True
                    if agg_method == "min":
                        return min(vals), True
                    if agg_method in ("mean", "avg"):
                        return sum(vals) / len(vals), True
                    if agg_method == "sum":
                        return sum(vals), True
        # Date / week / month literals ("2026-06-22", "2026-W26", "2026-06")
        # contain '-' plus letters/digits but are plain values, not
        # expressions.
        if re.match(r"^\d{4}[-/]?(W?\d{1,2})([-/]\d{1,2})?$", s, re.IGNORECASE):
            return v, True
        # Detect other non-evaluable expressions (function calls, arithmetic
        # over step references)
        looks_like_expr = (
            ("(" in s and ")" in s)
            or (re.search(r"step_\d+", s) is not None)
            or (re.search(r"[+\-*/]", s) and re.search(r"[a-zA-Z_]", s))
        )
        if looks_like_expr:
            logger.warning(
                f"[filter_data] Unsupported expression value '{s}'; "
                f"the condition is DROPPED (rows pass through). "
                f"Plans must use top_n/bottom_n or precomputed scalar steps instead."
            )
            return None, False
        return v, True

    # Values already resolved from a StandardDataFrame are literal data
    # values (e.g. "2026-W26"), never plan expressions — skip the expression
    # heuristic for them (0723 问题30: "2026-W26" contains '-' + letters and
    # would otherwise be misclassified as arithmetic and dropped).
    if _value_from_sdf:
        value_ok = True
    else:
        value, value_ok = _try_eval_expression(value)
    if not value_ok:
        # Condition unusable -> pass everything through, keep columns intact
        meta = Metadata.from_dict(data.metadata.to_dict())
        meta.description = f"Filter skipped (unsupported expression value)"
        cols = [c.copy() for c in data.columns]
        return StandardDataFrame(metadata=meta, columns=cols, rows=[dict(r) for r in data.rows])
    if conditions:
        cleaned_conditions = []
        for cond in conditions:
            cv, ok = _try_eval_expression(cond.get("value"))
            if ok:
                cond = dict(cond)
                cond["value"] = cv
                cleaned_conditions.append(cond)
        conditions = cleaned_conditions or None

    if conditions:
        # Multiple conditions
        all_conditions = conditions
    else:
        all_conditions = [{"column": column, "operator": operator_str, "value": value}]

    def row_matches(row):
        results = []
        for cond in all_conditions:
            col = cond["column"]
            op_str = cond["operator"]
            cond_val = cond["value"]
            if isinstance(cond_val, StandardDataFrame) and cond_val.metadata.is_scalar:
                cond_val = cond_val.get_scalar_value()
            row_val = row.get(col)

            # Handle set-membership operators ('in' / 'not_in') first (issue #5).
            op_norm = str(op_str).strip().lower() if op_str is not None else "=="

            # 错误问题汇总 问题46 fix: SQL LIKE / NOT LIKE 支持。
            if op_norm in ("like", "not like", "not_like"):
                matched = _like_match(row_val, cond_val)
                results.append(matched if op_norm == "like" else not matched)
                continue

            if op_norm in ("in", "not_in", "not in"):
                if not isinstance(cond_val, (list, tuple, set)):
                    # If a single scalar was supplied, wrap into a list.
                    cond_val_iter = [cond_val]
                else:
                    cond_val_iter = list(cond_val)
                # Compare both as raw and as string to be tolerant of types.
                cond_val_str = {str(v) for v in cond_val_iter if v is not None}
                row_val_str = str(row_val) if row_val is not None else None
                is_in = (row_val in cond_val_iter) or (row_val_str in cond_val_str)
                if op_norm == "in":
                    results.append(bool(is_in))
                else:
                    results.append(not is_in)
                continue

            if row_val is None:
                results.append(False)
                continue
            try:
                row_val_str = str(row_val).replace(",", "") if isinstance(row_val, str) else row_val
                cond_val_str = str(cond_val).replace(",", "") if isinstance(cond_val, str) else cond_val
                row_val_f = float(row_val_str)
                cond_val_f = float(cond_val_str)
                op_func = OPERATORS.get(op_str)
                if op_func:
                    results.append(op_func(row_val_f, cond_val_f))
                else:
                    results.append(False)
            except (ValueError, TypeError):
                # String comparison
                op_func = OPERATORS.get(op_str)
                if op_func:
                    results.append(op_func(str(row_val), str(cond_val)))
                else:
                    results.append(False)
        if logic.upper() == "OR":
            return any(results)
        return all(results)

    filtered_rows = [row for row in data.rows if row_matches(row)]

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(filtered_rows)
    meta.description = f"Filtered by {column} {operator_str} {value}"
    cols = [c.copy() for c in data.columns]
    return StandardDataFrame(metadata=meta, columns=cols, rows=filtered_rows)


def rank(
    data: StandardDataFrame,
    sort_by: str,
    order: str = "desc",
    group_by: Optional[list] = None,
    output: str = "rank_result",
    **kwargs,
) -> StandardDataFrame:
    """Add a rank column to data based on sort_by column."""
    if isinstance(group_by, str):
        group_by = [group_by]

    reverse = order.lower() == "desc"

    if group_by:
        groups = {}
        for row in data.rows:
            key = tuple(row.get(g) for g in group_by)
            groups.setdefault(key, []).append(row)

        result_rows = []
        for key, group_rows in groups.items():
            sorted_group = sorted(group_rows, key=lambda r: _to_comparable(r.get(sort_by)), reverse=reverse)
            for i, row in enumerate(sorted_group, 1):
                new_row = dict(row)
                new_row["rank"] = i
                result_rows.append(new_row)
    else:
        sorted_rows = sorted(data.rows, key=lambda r: _to_comparable(r.get(sort_by)), reverse=reverse)
        result_rows = []
        for i, row in enumerate(sorted_rows, 1):
            new_row = dict(row)
            new_row["rank"] = i
            result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    cols.append(ColumnDef(name="rank", role="measure", data_type="int", description="排名", is_computed=True))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
