"""
Data Reference Resolver
========================
Resolves string references (e.g., "step_1", "step_2.over_generation", "step_1.actual_generation")
to actual StandardDataFrame objects from the Context Store.
"""

import logging
import re
from difflib import SequenceMatcher
from typing import Any, Optional
import threading

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata
from system_b.models.exceptions import ReferenceResolveError

logger = logging.getLogger(__name__)


def _fuzzy_find_column(name: str, available: list, threshold: float = 0.5) -> Optional[str]:
    """Find the best matching column name from available columns.

    Tries (in order): exact, case-insensitive, substring, and SequenceMatcher
    fuzzy ratio. Returns None when no sufficiently good candidate exists.
    """
    if not name or not available:
        return None
    if name in available:
        return name
    lower_map = {c.lower(): c for c in available}
    if name.lower() in lower_map:
        return lower_map[name.lower()]
    # Substring match (both directions)
    candidates = [ac for ac in available
                  if name.lower() in ac.lower() or ac.lower() in name.lower()]
    if len(candidates) == 1:
        return candidates[0]
    # Pick best fuzzy match among all (including substring candidates)
    best_match, best_score = None, 0.0
    for ac in available:
        score = SequenceMatcher(None, name.lower(), ac.lower()).ratio()
        if score > best_score:
            best_score, best_match = score, ac
    if best_match and best_score >= threshold:
        return best_match
    return None


def _find_column_in_sdf(sdf: StandardDataFrame, name: str) -> Optional[str]:
    """Find a column in a StandardDataFrame by name, case-insensitive match,
    source_ref match, or fuzzy match. Returns the canonical column name or
    None if nothing matches well.
    """
    if sdf.has_column(name):
        return name
    # source_ref match: a join may have renamed a column while keeping the
    # original name in ColumnDef.source_ref. Match that first as it is exact.
    for c in sdf.columns:
        if c.source_ref and c.source_ref == name:
            return c.name
    # 0723 问题44/问题3(2nd) fix: refs like 'daypowersupply_right' appear when
    # the plan assumes join renamed the right side — but if BOTH join inputs
    # were aggregate outputs that renamed the measure (weekly_supply_2026 /
    # weekly_supply_2025, each with source_ref='daypowersupply'), neither the
    # name nor a single source_ref matches. Resolve X / X_right / X_rightN by
    # ordinal position among the columns sharing source_ref == X.
    m = re.match(r"^(.+?)_right(\d*)$", name)
    if m:
        base, idx_s = m.group(1), m.group(2)
        ordinal = int(idx_s) if idx_s else 1  # X_right -> 2nd, X_right2 -> 3rd
        same_src = [c.name for c in sdf.columns
                    if (c.source_ref and c.source_ref == base) or c.name == base]
        if len(same_src) > ordinal:
            logger.info(
                f"[Resolver] '{name}' resolved by source_ref ordinal -> "
                f"'{same_src[ordinal]}' (columns sharing source '{base}': {same_src})"
            )
            return same_src[ordinal]
    available = [c.name for c in sdf.columns]
    return _fuzzy_find_column(name, available)


class ContextStore:
    """
    Global context store for DAG execution.
    Maintains step-level and operation-level outputs.
    """

    def __init__(self):
        # step_id -> StandardDataFrame (final output of each step)
        self.step_outputs: dict[str, StandardDataFrame] = {}
        # "step_id.operation_name" -> StandardDataFrame (intermediate outputs)
        self.operation_outputs: dict[str, StandardDataFrame] = {}
        # step_id -> status (success/failed/skipped/running)
        self.step_statuses: dict[str, str] = {}
        # step_id -> error message
        self.step_errors: dict[str, str] = {}
        # step_id -> threading.Event (set when step finishes)
        self._events: dict[str, threading.Event] = {}

    def store(self, step_id: str, data: StandardDataFrame, operation_name: Optional[str] = None):
        """Store data into context."""
        if operation_name:
            key = f"{step_id}.{operation_name}"
            self.operation_outputs[key] = data
            logger.debug(f"[ContextStore] Stored operation output: {key} (rows={data.metadata.row_count})")
        # Always update step-level output (last write wins for compute steps)
        self.step_outputs[step_id] = data
        logger.debug(f"[ContextStore] Stored step output: {step_id} (rows={data.metadata.row_count})")

    def get(self, reference: str) -> StandardDataFrame:
        """
        Resolve a reference string to a StandardDataFrame.

        Supported formats:
        - "step_N" -> step N's final output
        - "step_N.output_name" -> step N's operation output (priority) or column subset
        - "step_N.output_name.column_name" -> nested column reference
        - numeric literal (100, 0.05) -> scalar StandardDataFrame
        """
        return resolve_reference(reference, self)

    def get_step_status(self, step_id: str) -> str:
        return self.step_statuses.get(step_id, "pending")

    def register_step(self, step_id: str):
        """Register a step and create its completion event."""
        if step_id not in self._events:
            self._events[step_id] = threading.Event()

    def wait_for_steps(self, step_ids: list[str]) -> bool:
        """Wait for all specified steps to finish. Returns True if all succeeded."""
        for sid in step_ids:
            ev = self._events.get(sid)
            if ev:
                ev.wait()
        return all(self.step_statuses.get(sid) == "success" for sid in step_ids)

    def reset_steps(self, step_ids) -> None:
        """Remove context entries for the given step ids.

        Used by the drain barrier between decomposition retries: retry rounds
        reuse the same step ids, so stale outputs/statuses/events from the
        failed round must be dropped, otherwise wait_for_steps passes
        instantly on the previous round's events.
        """
        for step_id in step_ids:
            self.step_outputs.pop(step_id, None)
            self.step_statuses.pop(step_id, None)
            self.step_errors.pop(step_id, None)
            self._events.pop(step_id, None)
            for key in [k for k in self.operation_outputs if k.startswith(f"{step_id}.")]:
                del self.operation_outputs[key]

    def set_step_status(self, step_id: str, status: str, error: Optional[str] = None):
        self.step_statuses[step_id] = status
        if error:
            self.step_errors[step_id] = error
        if status in ("success", "failed", "skipped"):
            ev = self._events.get(step_id)
            if ev:
                ev.set()

    def get_all_outputs(self) -> dict[str, StandardDataFrame]:
        return dict(self.step_outputs)

    def has_step(self, step_id: str) -> bool:
        return step_id in self.step_outputs

    def has_operation(self, key: str) -> bool:
        return key in self.operation_outputs


def resolve_reference(reference: Any, context: ContextStore) -> StandardDataFrame:
    """
    Master reference resolver. Handles all reference formats.
    """
    # Case 1: Already a StandardDataFrame
    if isinstance(reference, StandardDataFrame):
        return reference

    # Case 2: Numeric constant -> scalar SDF
    if isinstance(reference, (int, float)):
        return StandardDataFrame.from_scalar(value=reference, name="constant", description=f"Constant value {reference}")

    if not isinstance(reference, str):
        raise ReferenceResolveError(f"Unsupported reference type: {type(reference)}")

    ref_str = reference.strip()

    # Case 3: Numeric string -> scalar SDF
    try:
        val = float(ref_str)
        if val == int(val) and "." not in ref_str:
            val = int(ref_str)
        return StandardDataFrame.from_scalar(value=val, name="constant", description=f"Constant value {val}")
    except (ValueError, OverflowError):
        pass

    # Case 4: Parse step reference patterns
    parts = ref_str.split(".")

    if len(parts) == 1:
        # "step_N" -> full step output
        step_id = parts[0]
        if context.has_step(step_id):
            return context.step_outputs[step_id]
        raise ReferenceResolveError(f"Step output not found: {step_id}")

    elif len(parts) == 2:
        step_id, second = parts[0], parts[1]

        # Priority 1: operation output "step_N.operation_name"
        op_key = f"{step_id}.{second}"
        if context.has_operation(op_key):
            return context.operation_outputs[op_key]

        # Priority 2: column reference "step_N.column_name" (with fuzzy fallback)
        if context.has_step(step_id):
            sdf = context.step_outputs[step_id]
            resolved_col = _find_column_in_sdf(sdf, second)
            if resolved_col:
                if resolved_col != second:
                    logger.info(
                        f"[Resolver] Matched column '{second}' -> "
                        f"'{resolved_col}' in {step_id}"
                    )
                return _extract_column_with_dimensions(sdf, resolved_col)

        # If step doesn't exist at all
        if not context.has_step(step_id):
            raise ReferenceResolveError(f"Step output not found: {step_id}")

        raise ReferenceResolveError(
            f"Cannot resolve reference '{ref_str}': neither an operation output "
            f"nor a column name in {step_id}"
        )

    elif len(parts) == 3:
        step_id, op_name, col_name = parts

        # "step_N.operation_name.column_name"
        op_key = f"{step_id}.{op_name}"
        if context.has_operation(op_key):
            sdf = context.operation_outputs[op_key]
            resolved_col = _find_column_in_sdf(sdf, col_name)
            if resolved_col:
                if resolved_col != col_name:
                    logger.info(
                        f"[Resolver] Matched column '{col_name}' -> "
                        f"'{resolved_col}' in {op_key}"
                    )
                return _extract_column_with_dimensions(sdf, resolved_col)

            # 问题1 fix: the step-level wide table is checked BEFORE the
            # blind single-measure fallback. Previously, two DIFFERENT column
            # refs (step_3.combined.usercount and step_3.combined.ds_user_count)
            # could BOTH fall through to the op's single measure column, so an
            # add() summed the same column with itself and doubled the total.
            # The wide table (merged sibling operation outputs) usually still
            # carries both original columns, resolving each ref exactly.
            if context.has_step(step_id):
                step_sdf = context.step_outputs[step_id]
                step_resolved = _find_column_in_sdf(step_sdf, col_name)
                if step_resolved:
                    logger.info(
                        f"[Resolver] Matched '{col_name}' -> '{step_resolved}' "
                        f"via step-level wide table {step_id}"
                    )
                    return _extract_column_with_dimensions(step_sdf, step_resolved)

            # Also look for the column in SIBLING operation outputs of the
            # same step before falling back blindly.
            for other_key, other_sdf in context.operation_outputs.items():
                if other_key == op_key or not other_key.startswith(f"{step_id}."):
                    continue
                if other_sdf.has_column(col_name):
                    logger.info(
                        f"[Resolver] Column '{col_name}' found in sibling "
                        f"operation output '{other_key}'"
                    )
                    return _extract_column_with_dimensions(other_sdf, col_name)

            # Fallback A: aggregate/compute operations frequently *rename* the
            # measure column to the operation's output name (e.g. aggregate with
            # output="current_month_total" turns column "pap_all" into
            # "current_month_total"). When the caller still refers to the
            # original measure name (step_3.current_month_total.pap_all), resolve
            # to the single (renamed) measure column instead of failing.
            measures = sdf.get_measure_columns()
            if len(measures) == 1:
                logger.info(
                    f"[Resolver] Column '{col_name}' not in '{op_key}', "
                    f"falling back to its single measure column '{measures[0]}'"
                )
                return _extract_column_with_dimensions(sdf, measures[0])
            # Fallback B: try fuzzy / case-insensitive match against measures
            fuzzy = _fuzzy_measure_match(col_name, measures)
            if fuzzy:
                logger.info(f"[Resolver] Column '{col_name}' fuzzy-matched to '{fuzzy}' in '{op_key}'")
                return _extract_column_with_dimensions(sdf, fuzzy)

            # 0723 问题104 fix (last resort): the referenced column may be
            # produced by a LATER sibling operation that hasn't run yet, or
            # was lost by a union of heterogeneous inputs. Hard-failing here
            # killed the whole step ("Failed to resolve input 'data'").
            # Return the operation output itself with a loud warning so the
            # consuming function can degrade gracefully (e.g. filter now
            # drops the unusable condition instead of failing).
            logger.warning(
                f"[Resolver] Column '{col_name}' not found in operation "
                f"output '{op_key}' (measures: {measures}); falling back to "
                f"the operation output dataset itself"
            )
            return sdf

        # operation output doesn't exist -> try step-level wide table
        if context.has_step(step_id):
            step_sdf = context.step_outputs[step_id]
            step_resolved = _find_column_in_sdf(step_sdf, col_name)
            if step_resolved:
                logger.info(
                    f"[Resolver] Operation '{op_key}' missing, resolved "
                    f"'{col_name}' -> '{step_resolved}' via step-level wide table"
                )
                return _extract_column_with_dimensions(step_sdf, step_resolved)

        # Fallback: maybe "step_N.column" where column itself contains a dot is
        # not expected; try resolving step_N.op_name and ignore the trailing part
        if context.has_step(step_id):
            sdf = context.step_outputs[step_id]
            if sdf.has_column(op_name):
                return _extract_column_with_dimensions(sdf, op_name)

        raise ReferenceResolveError(f"Cannot resolve nested reference: {ref_str}")

    else:
        raise ReferenceResolveError(f"Invalid reference format: {ref_str}")


def _fuzzy_measure_match(col_name: str, measures: list) -> Optional[str]:
    """Find a measure column matching col_name by case-insensitive / substring."""
    if not measures:
        return None
    lower_map = {m.lower(): m for m in measures}
    if col_name.lower() in lower_map:
        return lower_map[col_name.lower()]
    for m in measures:
        if col_name.lower() in m.lower() or m.lower() in col_name.lower():
            return m
    return None


def _extract_column_with_dimensions(sdf: StandardDataFrame, measure_name: str) -> StandardDataFrame:
    """
    Extract a measure column along with all dimension columns.
    This preserves dimensional context for alignment operations.
    """
    dim_cols = sdf.get_dimension_columns()
    col_names = dim_cols + [measure_name]

    # Check if the requested column is actually a dimension
    col_def = sdf.get_column_def(measure_name)
    if col_def and col_def.role == "dimension":
        col_names = dim_cols if measure_name in dim_cols else dim_cols + [measure_name]

    new_cols = [c.copy() for c in sdf.columns if c.name in col_names]
    new_rows = [{k: v for k, v in row.items() if k in col_names} for row in sdf.rows]

    meta = Metadata.from_dict(sdf.metadata.to_dict())
    meta.row_count = len(new_rows)
    meta.description = f"Column subset: {measure_name} with dimensions from {sdf.metadata.source_step}"

    return StandardDataFrame(metadata=meta, columns=new_cols, rows=new_rows)


# Parameter keys that carry a *dataset reference* (and therefore may use the
# bare operation-output shorthand). Column-name / config params (sort_by,
# column, group_by, on, method, ...) must NOT be turned into dataframes even if
# their string value coincides with an operation output name.
_DATA_PARAM_KEYS = {
    "data", "left", "right", "datasets", "col_a", "col_b",
    "current_data", "previous_data",
}


def resolve_input_param(
    param_value: Any,
    context: ContextStore,
    current_step: Optional[str] = None,
    param_key: Optional[str] = None,
    allow_bare: bool = False,
) -> Any:
    """
    Resolve a single input parameter value.
    If it's a string reference, resolve it.
    If it's a list, resolve each element.
    Otherwise, return as-is.

    current_step: when provided, a *bare* output name (e.g. "result_pap_all_yoy")
        that matches an operation output produced earlier in the SAME compute
        step (i.e. "{current_step}.result_pap_all_yoy") will be resolved to that
        intermediate result. This lets compute plans reference prior operation
        outputs by their short name without the "step_N." prefix.

    param_key / allow_bare: bare-name resolution is only enabled for parameters
        known to carry dataset references (see _DATA_PARAM_KEYS) so that column
        names like sort_by="over_generation" are never coerced into dataframes.
    """
    bare_ok = allow_bare or (param_key in _DATA_PARAM_KEYS)

    if isinstance(param_value, str):
        # Bare operation-output name produced earlier in the current step.
        # Checked before the regex because such names (e.g. "step_1_ym",
        # "step_4_agg") may themselves start with "step_" yet are NOT real
        # step references. Only applied to data-bearing parameters.
        if current_step and bare_ok:
            op_key = f"{current_step}.{param_value}"
            if context.has_operation(op_key):
                return context.operation_outputs[op_key]

        # Check if it looks like a fully-qualified step reference
        if re.match(r"^step_\d+", param_value):
            try:
                return resolve_reference(param_value, context)
            except ReferenceResolveError:
                # 0723 问题100 fix: plans sometimes prefix an operation name
                # with the WRONG step id (e.g. "step_3.filtered_pop" when
                # filtered_pop was defined in the CURRENT step_5). Before
                # failing, retry the tail under the current step's operation
                # outputs.
                if current_step and bare_ok and "." in param_value:
                    tail = param_value.split(".", 1)[1]
                    # tail may itself be "op" or "op.column"
                    tail_parts = tail.split(".")
                    op_key = f"{current_step}.{tail_parts[0]}"
                    if context.has_operation(op_key):
                        logger.warning(
                            f"[Resolver] '{param_value}' not resolvable; "
                            f"op '{tail_parts[0]}' found in current step "
                            f"'{current_step}' — using '{current_step}.{tail}'"
                        )
                        return resolve_reference(f"{current_step}.{tail}", context)
                raise

        # Try numeric
        try:
            val = float(param_value)
            if val == int(val) and "." not in param_value:
                return int(param_value)
            return val
        except (ValueError, OverflowError):
            pass
        return param_value
    elif isinstance(param_value, list):
        # Inherit the data-bearing flag for list elements (e.g. datasets=[...])
        return [resolve_input_param(v, context, current_step, allow_bare=bare_ok) for v in param_value]
    elif isinstance(param_value, (int, float)):
        return param_value
    elif isinstance(param_value, dict):
        return {k: resolve_input_param(v, context, current_step, param_key=k) for k, v in param_value.items()}
    return param_value
