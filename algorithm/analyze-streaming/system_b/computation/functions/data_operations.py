"""
Data Operation Functions: extract_dimension, extract_year_month, union, join,
group_apply, shift, time_diff
"""

import logging
import re
from datetime import datetime
from typing import Optional

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata

logger = logging.getLogger(__name__)


def _to_float(val):
    if val is None:
        return None
    try:
        if isinstance(val, str):
            val = val.replace(",", "")
        return float(val)
    except (ValueError, TypeError):
        return None


def extract_dimension(
    data: StandardDataFrame,
    column: str,
    distinct: bool = True,
    keep_all_columns: bool = False,
    output: str = "dimension_values",
    **kwargs,
) -> StandardDataFrame:
    """Extract a dimension column, optionally deduplicated.

    C2-FIX: If keep_all_columns=True, preserves all columns from the original data
    (useful when downstream steps need to join/match on other columns like year).

    BUGFIX (issue #5): When ``keep_all_columns=True`` is requested by the plan
    but ``column`` does not exist in the data, return the data unchanged (all
    columns preserved) instead of an empty 1-column SDF — previously this
    produced 0 rows and broke downstream joins.
    """
    # Auto-correct column name if it doesn't exist
    if not data.has_column(column):
        from system_b.computation.function_dispatcher import _find_best_column_match
        available = [c.name for c in data.columns]
        # source_ref exact match first
        src_match = None
        for c in data.columns:
            if c.source_ref and c.source_ref == column:
                src_match = c.name
                break
        match = src_match or _find_best_column_match(column, available)
        # 0723 问题101 fix: plans reference the aggregation METHOD name
        # ('sum'/'mean'/'avg') as if it were the output column after an
        # upstream aggregate renamed the measure. If no match was found, the
        # requested name is method-like and the data has exactly one measure
        # column, that is the intent — previously this produced rows of
        # {"sum": null} and the whole ratio chain downstream became null.
        _method_like = {"sum", "mean", "avg", "average", "min", "max",
                        "total", "count", "value", "agg_result", "std", "median"}
        if not match and str(column).strip().lower() in _method_like:
            measures = data.get_measure_columns()
            if len(measures) == 1:
                logger.warning(
                    f"[extract_dimension] Column '{column}' not found; "
                    f"falling back to the sole measure column '{measures[0]}'"
                )
                match = measures[0]
        if match:
            logger.info(f"[extract_dimension] Auto-corrected column: '{column}' -> '{match}'")
            column = match
        elif keep_all_columns:
            # Issue #5: when caller wants all columns preserved and the requested
            # column doesn't exist (and no fuzzy match), DON'T blank out the
            # dataset — just return it unchanged.
            logger.warning(
                f"[extract_dimension] keep_all_columns=True but column '{column}' "
                f"not in data; returning data unchanged."
            )
            result_rows = [dict(r) for r in data.rows]
            cols = [c.copy() for c in data.columns]
            meta = Metadata.from_dict(data.metadata.to_dict())
            meta.row_count = len(result_rows)
            meta.description = f"Extracted dimension: {column} (all columns kept; column missing)"
            return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)

    if keep_all_columns:
        # C2-FIX: Return full rows with optional dedup on the target column
        if distinct:
            seen = set()
            unique_rows = []
            for row in data.rows:
                v = row.get(column)
                if v not in seen:
                    seen.add(v)
                    unique_rows.append(dict(row))
            rows = unique_rows
        else:
            rows = [dict(row) for row in data.rows]
        cols = [c.copy() for c in data.columns]
    else:
        values = [row.get(column) for row in data.rows]
        if distinct:
            seen = set()
            unique_values = []
            for v in values:
                if v not in seen:
                    seen.add(v)
                    unique_values.append(v)
            values = unique_values

        rows = [{column: v} for v in values]
        col_def = data.get_column_def(column)
        cols = [col_def.copy() if col_def else ColumnDef(name=column, role="dimension", data_type="string")]

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(rows)
    meta.description = f"Extracted dimension: {column}" + (" (all columns kept)" if keep_all_columns else "")
    return StandardDataFrame(metadata=meta, columns=cols, rows=rows)


def union(
    datasets: list,
    output: str = "union_result",
    **kwargs,
) -> StandardDataFrame:
    """Vertically union multiple StandardDataFrames (like SQL UNION ALL).
    
    Supports both direct SDF inputs and dict-wrapped inputs like:
    {"data": <SDF>, "label_key": "label_value"} 
    where extra keys are added as new columns to each row.
    """
    if not datasets:
        return StandardDataFrame.empty()

    # Normalize inputs: extract SDFs and any label columns from dict wrappers
    normalized = []
    for ds in datasets:
        if isinstance(ds, StandardDataFrame):
            normalized.append((ds, {}))
        elif isinstance(ds, dict):
            sdf = ds.get("data")
            if isinstance(sdf, StandardDataFrame):
                labels = {k: v for k, v in ds.items() if k != "data" and isinstance(v, str)}
                normalized.append((sdf, labels))
            else:
                logger.warning(f"[union] Skipping non-SDF dict entry: {type(sdf)}")
        else:
            logger.warning(f"[union] Skipping non-SDF entry: {type(ds)}")

    if not normalized:
        return StandardDataFrame.empty()

    first_sdf = normalized[0][0]

    all_col_defs = {}
    for sdf, labels in normalized:
        for c in sdf.columns:
            if c.name not in all_col_defs:
                all_col_defs[c.name] = c.copy()
        # Add label columns as dimension columns
        for label_name in labels:
            if label_name not in all_col_defs:
                all_col_defs[label_name] = ColumnDef(
                    name=label_name, role="dimension", data_type="string",
                    description=f"Label column from union"
                )

    combined_rows = []
    for sdf, labels in normalized:
        for row in sdf.rows:
            new_row = dict(row)
            new_row.update(labels)  # Add label columns
            combined_rows.append(new_row)

    # 错误问题汇总 问题46 fix: LLM 计划会把互相重叠的过滤子集 union 起来
    # (如 周末数据 ∪ 每月非首日数据，后者是前者子集)，导致同一原始行被重复
    # 计入下游 aggregate 求和，同比/环比数值被放大。这里对"完全相同的行"
    # (所有列值一致) 去重 —— 语义等价于 SQL UNION（而非 UNION ALL），对
    # 无重叠的正常拼接不受影响。
    deduped_rows = []
    seen = set()
    for row in combined_rows:
        key = tuple(sorted((k, str(v)) for k, v in row.items()))
        if key in seen:
            continue
        seen.add(key)
        deduped_rows.append(row)
    if len(deduped_rows) < len(combined_rows):
        logger.warning(
            f"[union] Removed {len(combined_rows) - len(deduped_rows)} exact "
            f"duplicate rows (overlapping input subsets would double-count "
            f"in downstream aggregation)"
        )
    combined_rows = deduped_rows

    cols = list(all_col_defs.values())
    meta = Metadata.from_dict(first_sdf.metadata.to_dict())
    meta.row_count = len(combined_rows)
    meta.description = f"Union of {len(normalized)} datasets"
    return StandardDataFrame(metadata=meta, columns=cols, rows=combined_rows)


def join(
    left: StandardDataFrame,
    right: StandardDataFrame,
    on: list,
    how: str = "inner",
    output: str = "join_result",
    **kwargs,
) -> StandardDataFrame:
    """Join two StandardDataFrames on specified keys.

    When the right side carries a measure column whose name collides with
    the left side, it is renamed to ``<name>_right`` and ``source_ref`` is
    annotated with the original name so downstream fuzzy lookups can still
    resolve the intended column.

    Supports how in {inner, left, right, outer, cross}. A cross join (or any
    join with an empty ``on`` key list) produces the cartesian product of the
    two sides, which is the correct behaviour for "attach a single total value
    to every row" patterns.
    """
    if isinstance(on, str):
        on = [on]
    on = on or []

    how = (how or "inner").lower()

    # Defensive: guard against non-SDF inputs (e.g. an unresolved string ref)
    if not isinstance(left, StandardDataFrame) or not isinstance(right, StandardDataFrame):
        raise ValueError(
            f"join requires StandardDataFrame inputs, got left={type(left).__name__}, "
            f"right={type(right).__name__}"
        )

    # Cross join, or join with no keys -> cartesian product.
    # 问题6 fix: right-side columns whose names collide with the left side are
    # renamed to <name>_right (source_ref keeps the original name). Previously
    # colliding right values were silently DROPPED, so e.g. joining 2026 data
    # with 2025 data on [] lost the entire 2025 measure column.
    if how == "cross" or not on:
        left_names = {c.name for c in left.columns}
        cross_rename = {}
        for c in right.columns:
            if c.name in left_names:
                new_name = f"{c.name}_right"
                idx = 2
                while new_name in left_names or new_name in cross_rename.values():
                    new_name = f"{c.name}_right{idx}"
                    idx += 1
                cross_rename[c.name] = new_name
        result_rows = []
        for l_row in left.rows:
            for r_row in right.rows:
                merged = dict(l_row)
                for k, v in r_row.items():
                    merged[cross_rename.get(k, k)] = v
                result_rows.append(merged)
        all_cols = {}
        for c in left.columns:
            all_cols[c.name] = c.copy()
        for c in right.columns:
            new_name = cross_rename.get(c.name, c.name)
            if new_name not in all_cols:
                col_copy = c.copy()
                if new_name != c.name:
                    col_copy.name = new_name
                    col_copy.source_ref = c.source_ref or c.name
                    col_copy.description = (
                        f"{col_copy.description} (renamed from {c.name})"
                        if col_copy.description else f"Renamed from {c.name} after cross join"
                    )
                all_cols[new_name] = col_copy
        meta = Metadata(
            source_step=left.metadata.source_step,
            description=f"cross join" + (f" on {on}" if on else ""),
            row_count=len(result_rows),
            lineage=list(set(left.metadata.lineage + right.metadata.lineage)),
        )
        return StandardDataFrame(metadata=meta, columns=list(all_cols.values()), rows=result_rows)

    # Robustness: an INNER join against an empty side would wipe out ALL data,
    # which is almost never the user's intent when one upstream query simply
    # returned no rows (e.g. a metric stored in a table that has no data for the
    # requested date). Degrade such inner joins to a LEFT join so the populated
    # side's data is preserved (the missing measures become null).
    effective_how = how
    if how == "inner":
        if not right.rows and left.rows:
            logger.warning(
                f"[join] right side is empty; degrading inner join to left join "
                f"to preserve {len(left.rows)} left rows"
            )
            effective_how = "left"
        elif not left.rows and right.rows:
            logger.warning(
                f"[join] left side is empty; degrading inner join to right join "
                f"to preserve {len(right.rows)} right rows"
            )
            effective_how = "right"
    how = effective_how

    # Build rename map for right-side collisions (dimensions on key stay shared)
    on_set = set(on)
    left_col_names = {c.name for c in left.columns}
    right_rename = {}  # original_name -> new_name
    for c in right.columns:
        if c.name in on_set:
            continue  # join keys keep original name
        if c.name in left_col_names:
            # Disambiguate with _right suffix
            new_name = f"{c.name}_right"
            # Further disambiguate if _right itself collides
            idx = 2
            while new_name in left_col_names or new_name in right_rename.values():
                new_name = f"{c.name}_right{idx}"
                idx += 1
            right_rename[c.name] = new_name

    def _apply_right_rename(row: dict) -> dict:
        if not right_rename:
            return dict(row)
        new_row = {}
        for k, v in row.items():
            new_row[right_rename.get(k, k)] = v
        return new_row

    # Build index on right (using original row keys)
    right_index = {}
    for row in right.rows:
        key = tuple(row.get(k) for k in on)
        right_index.setdefault(key, []).append(row)

    # =========================================================================
    # 新问题3/新问题4 fix: YoY joins routinely join "current period" rows with
    # "prior period" rows ON A TIME KEY whose values can NEVER overlap
    # (year_month '2026-01' vs '2025-01', or ptdate '2026-06-07' vs
    # '2025-06-01'). A plain left join then fills every right-side column
    # with None and the whole同比 computation silently degrades to 0/None.
    #
    # When we detect zero key overlap AND the key values look date-like:
    #   1) Year-stripped alignment: strip the leading YYYY from each date-like
    #      key component ('2026-01' -> '-01') and re-key the right side. This
    #      aligns the SAME period across different years (month vs month).
    #   2) Positional alignment fallback: if stripping still yields no
    #      overlap (e.g. Sundays of June 2026 vs Sundays of June 2025 fall on
    #      different dates), sort both sides by key and pair row-by-row
    #      ("第N个周期 对 第N个周期"), mirroring the positional fallback in
    #      arithmetic._align_and_compute.
    # =========================================================================
    left_keys = {tuple(r.get(k) for k in on) for r in left.rows}
    if left.rows and right.rows and not (left_keys & set(right_index.keys())):
        def _strip_year_part(v):
            if isinstance(v, str):
                s = v.strip()
                m = re.match(r"^(\d{4})([-/]\d{1,2}([-/]\d{1,2})?)\s*$", s)
                if m:
                    return m.group(2)
                # 错误问题汇总 问题44 fix: ISO week keys like "2026-W23" must
                # also align with "2025-W23" for 同比 joins on week keys.
                m = re.match(r"^(\d{4})(-?W\d{1,2})\s*$", s, re.IGNORECASE)
                if m:
                    return m.group(2).upper().lstrip("-")
            return None

        def _strip_key(key_tuple):
            out, any_date = [], False
            for v in key_tuple:
                s = _strip_year_part(v)
                if s is not None:
                    any_date = True
                    out.append(s)
                else:
                    out.append(v)
            return tuple(out) if any_date else None

        left_by_stripped = {}
        for r in left.rows:
            sk = _strip_key(tuple(r.get(k) for k in on))
            if sk is not None:
                left_by_stripped.setdefault(sk, []).append(r)
        right_stripped_keys = {
            sk for sk in (_strip_key(k) for k in right_index.keys()) if sk is not None
        }

        if left_by_stripped and (set(left_by_stripped) & right_stripped_keys):
            # Year-stripped alignment: re-key each right row to the RAW key of
            # the left row(s) sharing the same stripped (year-less) period.
            logger.warning(
                f"[join] keys {on} have zero overlapping values across sides "
                f"(cross-year comparison pattern); aligning by year-stripped "
                f"period part"
            )
            new_index = {}
            for row in right.rows:
                sk = _strip_key(tuple(row.get(k) for k in on))
                l_rows = left_by_stripped.get(sk) if sk is not None else None
                if l_rows:
                    raw_left_key = tuple(l_rows[0].get(k) for k in on)
                    new_index.setdefault(raw_left_key, []).append(row)
                else:
                    new_index.setdefault(
                        tuple(row.get(k) for k in on), []).append(row)
            right_index = new_index
        else:
            # Positional alignment: only when every key component on both
            # sides is date-like (avoid garbage pairing of categorical keys).
            def _all_date_like(rows):
                for r in rows:
                    for k in on:
                        if _strip_year_part(r.get(k)) is None:
                            return False
                return True

            if _all_date_like(left.rows) and _all_date_like(right.rows):
                logger.warning(
                    f"[join] keys {on} have zero overlap even after year "
                    f"stripping; falling back to positional alignment "
                    f"(row N of left <-> row N of right, both sorted by key)"
                )
                left_sorted = sorted(
                    left.rows, key=lambda r: tuple(str(r.get(k, "")) for k in on))
                right_sorted = sorted(
                    right.rows, key=lambda r: tuple(str(r.get(k, "")) for k in on))
                new_index = {}
                for pos, r_row in enumerate(right_sorted):
                    if pos < len(left_sorted):
                        raw_left_key = tuple(left_sorted[pos].get(k) for k in on)
                        new_index.setdefault(raw_left_key, []).append(r_row)
                    else:
                        new_index.setdefault(
                            tuple(r_row.get(k) for k in on), []).append(r_row)
                right_index = new_index

    result_rows = []
    if how in ("inner", "left"):
        for l_row in left.rows:
            key = tuple(l_row.get(k) for k in on)
            r_rows = right_index.get(key, [])
            if r_rows:
                for r_row in r_rows:
                    merged = dict(l_row)
                    r_row_renamed = _apply_right_rename(r_row)
                    for k, v in r_row_renamed.items():
                        if k not in merged:
                            merged[k] = v
                    result_rows.append(merged)
            elif how == "left":
                merged = dict(l_row)
                for c in right.columns:
                    new_name = right_rename.get(c.name, c.name)
                    if new_name not in merged:
                        merged[new_name] = None
                result_rows.append(merged)

    elif how == "right":
        left_index = {}
        for row in left.rows:
            key = tuple(row.get(k) for k in on)
            left_index.setdefault(key, []).append(row)
        for r_row in right.rows:
            key = tuple(r_row.get(k) for k in on)
            l_rows = left_index.get(key, [])
            r_row_renamed = _apply_right_rename(r_row)
            if l_rows:
                for l_row in l_rows:
                    merged = dict(l_row)
                    for k, v in r_row_renamed.items():
                        if k not in merged:
                            merged[k] = v
                    result_rows.append(merged)
            else:
                merged = dict(r_row_renamed)
                for c in left.columns:
                    if c.name not in merged:
                        merged[c.name] = None
                result_rows.append(merged)

    elif how == "outer":
        left_keys_used = set()
        for l_row in left.rows:
            key = tuple(l_row.get(k) for k in on)
            r_rows = right_index.get(key, [])
            if r_rows:
                left_keys_used.add(key)
                for r_row in r_rows:
                    merged = dict(l_row)
                    r_row_renamed = _apply_right_rename(r_row)
                    for k, v in r_row_renamed.items():
                        if k not in merged:
                            merged[k] = v
                    result_rows.append(merged)
            else:
                merged = dict(l_row)
                for c in right.columns:
                    new_name = right_rename.get(c.name, c.name)
                    if new_name not in merged:
                        merged[new_name] = None
                result_rows.append(merged)
        for r_row in right.rows:
            key = tuple(r_row.get(k) for k in on)
            if key not in left_keys_used:
                merged = _apply_right_rename(r_row)
                for c in left.columns:
                    if c.name not in merged:
                        merged[c.name] = None
                result_rows.append(merged)

    # Merge column definitions
    all_cols = {}
    for c in left.columns:
        all_cols[c.name] = c.copy()
    for c in right.columns:
        if c.name in on_set:
            # Already present from left; skip
            if c.name not in all_cols:
                all_cols[c.name] = c.copy()
            continue
        new_name = right_rename.get(c.name, c.name)
        if new_name in all_cols:
            continue
        col_copy = c.copy()
        if new_name != c.name:
            # Record the original name so resolver / fuzzy matcher can find it
            col_copy.name = new_name
            col_copy.source_ref = c.source_ref or c.name
            if col_copy.description:
                col_copy.description = f"{col_copy.description} (renamed from {c.name})"
            else:
                col_copy.description = f"Renamed from {c.name} after join"
        all_cols[new_name] = col_copy

    meta = Metadata(
        source_step=left.metadata.source_step,
        description=f"{how} join on {on}",
        row_count=len(result_rows),
        lineage=list(set(left.metadata.lineage + right.metadata.lineage)),
    )
    return StandardDataFrame(metadata=meta, columns=list(all_cols.values()), rows=result_rows)


def group_apply(
    data: StandardDataFrame,
    group_by: list,
    operation: str,
    operation_params: dict,
    output: str = "group_apply_result",
    **kwargs,
) -> StandardDataFrame:
    """Apply an operation within each group."""
    if isinstance(group_by, str):
        group_by = [group_by]

    # Import here to avoid circular import
    from system_b.computation.function_registry import get_function

    groups = {}
    for row in data.rows:
        key = tuple(row.get(g) for g in group_by)
        groups.setdefault(key, []).append(row)

    func = get_function(operation)
    if func is None:
        raise ValueError(f"Unknown function for group_apply: {operation}")

    result_rows = []
    for key, group_rows in groups.items():
        group_cols = [c.copy() for c in data.columns]
        group_sdf = StandardDataFrame(
            metadata=Metadata.from_dict(data.metadata.to_dict()),
            columns=group_cols,
            rows=group_rows,
        )
        group_sdf.metadata.row_count = len(group_rows)

        params = dict(operation_params)
        params["data"] = group_sdf
        sub_result = func(**params)
        if isinstance(sub_result, StandardDataFrame):
            result_rows.extend(sub_result.rows)

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"group_apply({operation}) by {group_by}"
    cols = [c.copy() for c in data.columns]
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def shift(
    data: StandardDataFrame,
    column: str,
    offset: int,
    group_by: Optional[list] = None,
    time_column: Optional[str] = None,
    output: str = "shift_result",
    **kwargs,
) -> StandardDataFrame:
    """Shift a column by offset rows. Generates a new column with shifted values."""
    offset = int(offset)
    if isinstance(group_by, str):
        group_by = [group_by]

    shift_col_name = f"{column}_shift_{offset}"

    if group_by:
        groups = {}
        row_indices = {}
        for i, row in enumerate(data.rows):
            key = tuple(row.get(g) for g in group_by)
            groups.setdefault(key, []).append(i)

        result_rows = [dict(row) for row in data.rows]
        for key, indices in groups.items():
            if time_column:
                indices = sorted(indices, key=lambda i: str(data.rows[i].get(time_column, "")))
            for pos, idx in enumerate(indices):
                source_pos = pos - offset
                if 0 <= source_pos < len(indices):
                    result_rows[idx][shift_col_name] = data.rows[indices[source_pos]].get(column)
                else:
                    result_rows[idx][shift_col_name] = None
    else:
        rows_sorted = list(range(len(data.rows)))
        if time_column:
            rows_sorted = sorted(rows_sorted, key=lambda i: str(data.rows[i].get(time_column, "")))
        result_rows = [dict(row) for row in data.rows]
        for pos, idx in enumerate(rows_sorted):
            source_pos = pos - offset
            if 0 <= source_pos < len(rows_sorted):
                result_rows[idx][shift_col_name] = data.rows[rows_sorted[source_pos]].get(column)
            else:
                result_rows[idx][shift_col_name] = None

    cols = [c.copy() for c in data.columns]
    orig_col = data.get_column_def(column)
    # 新问题6 fix: when the shifted column is itself a RENAME of an original
    # column (e.g. aggregate renamed 'daypowersupply' -> 'monthly_cumulative'
    # keeping source_ref='daypowersupply'), downstream plans reference the
    # shifted column by the ORIGINAL name + suffix ('daypowersupply_shift_1').
    # Annotate source_ref accordingly so resolver / dispatcher exact
    # source_ref matching finds it instead of raising ReferenceResolveError.
    shift_source_ref = None
    if orig_col is not None and orig_col.source_ref and orig_col.source_ref != column:
        shift_source_ref = f"{orig_col.source_ref}_shift_{offset}"
    _shift_label = (orig_col.description if orig_col and orig_col.description else column)
    cols.append(ColumnDef(
        name=shift_col_name, role="measure",
        data_type=orig_col.data_type if orig_col else "float",
        unit=orig_col.unit if orig_col else None,
        description=f"{_shift_label}（平移{offset}期）",
        is_computed=True,
        source_ref=shift_source_ref,
    ))

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def extract_year_month(
    data: StandardDataFrame,
    date_column: str,
    output: str = "year_month",
    **kwargs,
) -> StandardDataFrame:
    """
    Extract YYYY-MM from a date column, appending a new dimension column.

    Accepts common date formats: 'YYYY-MM-DD', 'YYYY/MM/DD',
    'YYYY-MM-DD HH:MM:SS', datetime objects, etc.
    Rows whose date cannot be parsed receive None in the output column.
    """
    new_rows = []
    for row in data.rows:
        new_row = dict(row)
        raw = row.get(date_column)
        ym = None
        if raw is not None and raw != "":
            # Handle datetime objects
            if hasattr(raw, "strftime"):
                try:
                    ym = raw.strftime("%Y-%m")
                except Exception:
                    ym = None
            else:
                s = str(raw).strip().replace("/", "-")
                # Try to locate YYYY-MM prefix
                if len(s) >= 7 and s[4] == "-":
                    candidate = s[:7]
                    # Quick sanity validation
                    try:
                        year_part, month_part = candidate.split("-")
                        if (len(year_part) == 4 and year_part.isdigit()
                                and 1 <= int(month_part) <= 12):
                            ym = candidate
                    except Exception:
                        ym = None
                if ym is None:
                    # Fallback: try datetime.strptime with a few formats
                    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d",
                                "%Y-%m-%dT%H:%M:%S", "%Y%m%d"):
                        try:
                            dt = datetime.strptime(s, fmt)
                            ym = dt.strftime("%Y-%m")
                            break
                        except ValueError:
                            continue
        new_row[output] = ym
        new_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    # Don't duplicate if output name already exists
    if not any(c.name == output for c in cols):
        cols.append(ColumnDef(
            name=output, role="dimension", data_type="string",
            description=f"Year-month extracted from {date_column}",
            is_computed=True,
        ))

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(new_rows)
    meta.description = f"Extract year-month from {date_column} -> {output}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=new_rows)


def time_diff(
    data: StandardDataFrame,
    start_column: str,
    end_column: str,
    unit: str = "hours",
    output: str = "time_diff_result",
    output_unit: Optional[str] = None,
    **kwargs,
) -> StandardDataFrame:
    """Calculate time difference between two datetime columns."""
    unit_divisors = {
        "seconds": 1, "second": 1, "sec": 1, "s": 1,
        "minutes": 60, "minute": 60, "min": 60, "m": 60,
        "hours": 3600, "hour": 3600, "h": 3600, "hr": 3600,
        "days": 86400, "day": 86400, "d": 86400,
    }
    divisor = unit_divisors.get(unit.lower(), 3600)

    if output_unit is None:
        output_unit = unit

    result_rows = []
    for row in data.rows:
        new_row = dict(row)
        start_val = row.get(start_column)
        end_val = row.get(end_column)

        if start_val is not None and end_val is not None:
            try:
                # Try parsing datetime strings
                for fmt in ["%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"]:
                    try:
                        start_dt = datetime.strptime(str(start_val), fmt)
                        break
                    except ValueError:
                        continue
                else:
                    start_dt = None

                for fmt in ["%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"]:
                    try:
                        end_dt = datetime.strptime(str(end_val), fmt)
                        break
                    except ValueError:
                        continue
                else:
                    end_dt = None

                if start_dt and end_dt:
                    diff_seconds = (end_dt - start_dt).total_seconds()
                    new_row[output] = round(diff_seconds / divisor, 4)
                else:
                    new_row[output] = None
            except Exception:
                new_row[output] = None
        else:
            new_row[output] = None
        result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    cols.append(ColumnDef(
        name=output, role="measure", data_type="float",
        unit=output_unit, description=f"Time diff ({start_column} to {end_column}) in {unit}",
        is_computed=True,
    ))

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


# =========================================================================
# C1-FIX: Date derivation functions
# =========================================================================

def extract_year(
    data: StandardDataFrame,
    date_column: str,
    output: str = "year",
    **kwargs,
) -> StandardDataFrame:
    """C1-FIX: Extract year from a date column and add as a new dimension column."""
    result_rows = []
    for row in data.rows:
        new_row = dict(row)
        val = row.get(date_column)
        if val is not None:
            s = str(val)
            new_row[output] = s[:4] if len(s) >= 4 else None
        else:
            new_row[output] = None
        result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    cols.append(ColumnDef(
        name=output, role="dimension", data_type="string",
        description=f"Year extracted from {date_column}", is_computed=True,
    ))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Added year column from {date_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def extract_month(
    data: StandardDataFrame,
    date_column: str,
    output: str = "month",
    **kwargs,
) -> StandardDataFrame:
    """C1-FIX: Extract month from a date column and add as a new dimension column."""
    result_rows = []
    for row in data.rows:
        new_row = dict(row)
        val = row.get(date_column)
        if val is not None:
            s = str(val)
            if len(s) >= 7:
                new_row[output] = s[5:7]
            else:
                new_row[output] = None
        else:
            new_row[output] = None
        result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    cols.append(ColumnDef(
        name=output, role="dimension", data_type="string",
        description=f"Month extracted from {date_column}", is_computed=True,
    ))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Added month column from {date_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def _parse_date(val):
    """Parse common date representations into a datetime, or None."""
    if val is None or val == "":
        return None
    if hasattr(val, "isocalendar") and hasattr(val, "year"):
        # datetime.date / datetime.datetime
        return val
    s = str(val).strip().replace("/", "-")
    for fmt in ("%Y-%m-%d", "%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y%m%d"):
        try:
            return datetime.strptime(s[:19] if " " in s or "T" in s else s[:10], fmt)
        except ValueError:
            continue
    return None


def extract_week(
    data: StandardDataFrame,
    date_column: str,
    output: str = "week",
    **kwargs,
) -> StandardDataFrame:
    """问题5/问题8 fix: derive an ISO year-week dimension ('2026-W23') from a
    date column. Use this before weekly aggregation — the metadata pseudo
    dimension ptweek() is NOT a real queryable column.
    """
    result_rows = []
    for row in data.rows:
        new_row = dict(row)
        dt = _parse_date(row.get(date_column))
        if dt is not None:
            iso = dt.isocalendar()
            new_row[output] = f"{iso[0]}-W{iso[1]:02d}"
        else:
            new_row[output] = None
        result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    if not any(c.name == output for c in cols):
        cols.append(ColumnDef(
            name=output, role="dimension", data_type="string",
            description=f"ISO year-week extracted from {date_column}",
            is_computed=True,
        ))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Added week column from {date_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def extract_weekday(
    data: StandardDataFrame,
    date_column: str,
    output: str = "weekday_type",
    **kwargs,
) -> StandardDataFrame:
    """问题4 fix: derive weekday information from a date column.

    Appends TWO dimension columns:
    - ``weekday``: 星期一 .. 星期日
    - ``weekday_type`` (the ``output`` column): 工作日 (Mon-Fri) / 周末 (Sat-Sun)

    Enables 工作日 vs 周末 comparisons via group_by ["weekday_type"].
    """
    zh_names = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
    result_rows = []
    for row in data.rows:
        new_row = dict(row)
        dt = _parse_date(row.get(date_column))
        if dt is not None:
            wd = dt.weekday()  # 0=Mon .. 6=Sun
            new_row["weekday"] = zh_names[wd]
            new_row[output] = "周末" if wd >= 5 else "工作日"
        else:
            new_row["weekday"] = None
            new_row[output] = None
        result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    if not any(c.name == "weekday" for c in cols):
        cols.append(ColumnDef(
            name="weekday", role="dimension", data_type="string",
            description=f"星期几 extracted from {date_column}", is_computed=True,
        ))
    if not any(c.name == output for c in cols):
        cols.append(ColumnDef(
            name=output, role="dimension", data_type="string",
            description=f"工作日/周末 type from {date_column}", is_computed=True,
        ))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Added weekday columns from {date_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def cumulative_sum(
    data: StandardDataFrame,
    column: str,
    time_column: Optional[str] = None,
    group_by: Optional[list] = None,
    output: str = "cumulative_sum",
    **kwargs,
) -> StandardDataFrame:
    """问题9 fix: running cumulative sum of ``column`` ordered by
    ``time_column`` (optionally within groups). Supports '累计增长趋势'-type
    questions that previously had no matching function.
    """
    if isinstance(group_by, str):
        group_by = [group_by]

    # Auto-correct column name
    if not data.has_column(column):
        from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
        column = _fix_column_refs_in_sdf(data, column)

    indices = list(range(len(data.rows)))
    if time_column:
        indices.sort(key=lambda i: str(data.rows[i].get(time_column, "")))

    result_rows = [dict(r) for r in data.rows]
    if group_by:
        running = {}
        for i in indices:
            key = tuple(data.rows[i].get(g) for g in group_by)
            v = _to_float(data.rows[i].get(column))
            running[key] = running.get(key, 0.0) + (v or 0.0)
            result_rows[i][output] = round(running[key], 6)
    else:
        total = 0.0
        for i in indices:
            v = _to_float(data.rows[i].get(column))
            total += (v or 0.0)
            result_rows[i][output] = round(total, 6)

    # Keep output ordered by time for readability
    if time_column:
        result_rows = [result_rows[i] for i in indices]

    cols = [c.copy() for c in data.columns]
    orig_col = data.get_column_def(column)
    _cum_label = (orig_col.description if orig_col and orig_col.description else column)
    if not any(c.name == output for c in cols):
        cols.append(ColumnDef(
            name=output, role="measure", data_type="float",
            unit=orig_col.unit if orig_col else None,
            description=f"{_cum_label}的累计值", is_computed=True,
        ))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Cumulative sum of {column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


# NOTE: a more robust ``extract_year_month`` is defined earlier in this module
# (handling datetime objects + multiple format fallbacks). The simple
# string-prefix variant that previously lived here was removed because the two
# definitions collided when reloaded.


def latest_data(
    data: StandardDataFrame,
    time_column: str,
    group_by: Optional[list] = None,
    **kwargs,
) -> StandardDataFrame:
    """快照类指标取最新：按 ``time_column`` 每组取时间最新的一条整行。

    对应提示词规则十四之二【快照类指标处理-强制】中已承诺的行为：
    - 不传 ``group_by`` 时自动按全部非时间维度列分组、各组各取最新一条；
    - 输出中剔除非快照 measure 列（防止普通指标被误读成"最后一天的值"）。
      剔除仅在至少识别出一个快照列（描述含"快照"，来自元数据聚合类型声明）
      时才执行；一个快照列都识别不到时全部保留，避免元数据缺失导致空结果。

    时间列缺失兜底：COUNT 类计数结果天然无时间列，而提示词规则十四之二仍会强制
    套用本函数，此时按行数降级——0 行返回空结果；1 行"唯一一行即最新"原样返回；
    ≥2 行无法判定哪条最新，仍抛错（避免静默给出错误的业务结果）。
    """
    if isinstance(group_by, str):
        group_by = [group_by]

    if not data.has_column(time_column):
        from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
        time_column = _fix_column_refs_in_sdf(data, time_column)
    if not data.has_column(time_column):
        actual_cols = [c.name for c in data.columns]
        if not data.rows:
            logger.warning(
                f"[latest_data] empty input without time column '{time_column}'; returning empty result"
            )
            return data
        if len(data.rows) == 1:
            logger.warning(
                f"[latest_data] single-row input without time column '{time_column}' "
                f"(actual columns: {actual_cols}); single row is the latest, returning as-is"
            )
            return data
        raise ValueError(
            f"latest_data: time column '{time_column}' not found in data "
            f"(actual columns: {actual_cols})"
        )

    from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
    if group_by:
        keys = []
        for g in group_by:
            keys.append(g if data.has_column(g) else _fix_column_refs_in_sdf(data, g))
    else:
        keys = [c.name for c in data.columns
                if c.role == "dimension" and c.name != time_column]

    measure_cols = [c for c in data.columns if c.role == "measure"]
    snapshot_names = {c.name for c in measure_cols if "快照" in (c.description or "")}
    if snapshot_names:
        drop_cols = {c.name for c in measure_cols} - snapshot_names
        if drop_cols:
            logger.info(f"[latest_data] dropping non-snapshot measure columns: {sorted(drop_cols)}")
    else:
        drop_cols = set()
        logger.warning("[latest_data] no snapshot column identified (no '快照' in "
                       "measure descriptions); keeping all columns")

    def _normalize_date(val):
        """标准化日期值为 ISO 格式 YYYY-MM-DD，便于字典序比较。"""
        if val is None or val == "":
            return ""
        # 如果是 datetime 对象
        if isinstance(val, datetime):
            return val.strftime("%Y-%m-%d")
        # 如果是字符串，提取前 10 个字符，再标准化
        s = str(val).strip()
        if not s:
            return ""
        # 去掉时间部分（保留前 10 个字符）
        s = " ".join(s.split())[:10]
        # 识别并标准化日期格式
        if len(s) == 10:
            # 纯数字：YYYYMMDD 或 DDMMYYYY 等
            if s.isdigit() and len(s) == 8:
                # 判断是 YYYYMMDD 还是其他格式
                if int(s[:4]) >= 1900 and int(s[:4]) <= 2100:
                    return s  # YYYYMMDD，已经是 ISO 格式
                # 其他数字格式无法准确判断，保持原样
            # 有分隔符：YYYY-MM-DD, DD-MM-YYYY, MM-DD-YYYY
            parts = s.split("-")
            if len(parts) == 3:
                # 检查是否为 YYYY-MM-DD
                if len(parts[0]) == 4 and parts[0].isdigit():
                    return s
                # 尝试 DD-MM-YYYY
                if len(parts[0]) == 2 and len(parts[1]) == 2 and len(parts[2]) == 4:
                    return parts[2] + "-" + parts[0] + "-" + parts[1]
                # 尝试 MM-DD-YYYY
                if len(parts[0]) == 2 and len(parts[1]) == 2 and len(parts[2]) == 4:
                    return parts[2] + "-" + parts[1] + "-" + parts[0]
        # 无法标准化，回退到前 10 个字符
        return s[:10]

    # 辅助函数：将日期值标准化为 ISO 格式 YYYY-MM-DD，用于字典序比较
    def _norm_date(val):
        """标准化日期：返回 ISO 格式字符串或空字符串。"""
        if val is None or val == "":
            return ""
        # 如果是 datetime 对象
        if isinstance(val, datetime):
            return val.strftime("%Y-%m-%d")
        # 如果是字符串，处理日期格式
        s = str(val).strip()
        if not s:
            return ""
        # 去掉时间部分（保留前 10 个字符）
        s = " ".join(s.split())[:10]
        # 识别并标准化为 ISO 格式
        if len(s) == 10:
            # 纯数字：YYYYMMDD
            if s.isdigit() and len(s) == 8:
                # 判断是 YYYYMMDD 还是其他格式
                if int(s[:4]) >= 1900 and int(s[:4]) <= 2100:
                    return s  # YYYYMMDD，已经是 ISO 格式
                # 其他数字格式无法准确判断，保持原样
            # 有分隔符：YYYY-MM-DD, DD-MM-YYYY, MM-DD-YYYY
            parts = s.split("-")
            if len(parts) == 3:
                # 检查是否为 YYYY-MM-DD
                if len(parts[0]) == 4 and parts[0].isdigit():
                    return s
                # 尝试 DD-MM-YYYY（如 10-07-2026）
                if len(parts[0]) == 2 and len(parts[1]) == 2 and len(parts[2]) == 4:
                    return parts[2] + "-" + parts[0] + "-" + parts[1]
                # 尝试 MM-DD-YYYY
                if len(parts[0]) == 2 and len(parts[1]) == 2 and len(parts[2]) == 4:
                    return parts[2] + "-" + parts[1] + "-" + parts[0]
        # 无法标准化，回退到前 10 个字符
        return s[:10]

    best = {}
    for row in data.rows:
        tv = _norm_date(row.get(time_column))
        gk = tuple(str(row.get(k)) if row.get(k) is not None else None for k in keys)
        if gk not in best:
            best[gk] = row
            best[gk]["_tv"] = tv
        else:
            best_tv = best[gk].get("_tv", "")
            if tv > best_tv:
                best[gk] = row
                best[gk]["_tv"] = tv

    keep_cols = [c for c in data.columns if c.name not in drop_cols]
    result_rows = [{c.name: row.get(c.name) for c in keep_cols} for row in best.values()]

    cols = [c.copy() for c in keep_cols]
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Latest row per {keys or 'dataset'} by {time_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
