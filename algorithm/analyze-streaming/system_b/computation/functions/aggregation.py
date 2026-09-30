"""
Aggregation Function: aggregate
Supports sum, mean, max, min, count, count_distinct, std, median
with optional group_by.
"""

import logging
import re
from typing import Optional

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata

logger = logging.getLogger(__name__)

# 0806 fix: 聚合派生列描述的中文方法名映射（供总结模型直接读懂含义）
_ZH_METHOD = {
    "sum": "求和", "mean": "平均值", "avg": "平均值", "max": "最大值",
    "min": "最小值", "count": "计数", "count_distinct": "去重计数",
    "std": "标准差", "median": "中位数",
}


def _to_float(val):
    if val is None:
        return None
    try:
        if isinstance(val, str):
            val = val.replace(",", "")
        return float(val)
    except (ValueError, TypeError):
        return None


def aggregate(
    data: StandardDataFrame,
    column,
    method: str,
    group_by: Optional[list] = None,
    output: str = "agg_result",
    output_unit: Optional[str] = None,
    **kwargs,
) -> StandardDataFrame:
    """
    Aggregate a column using the specified method.
    Without group_by: returns a scalar.
    With group_by: returns grouped results.

    BUGFIX (issue #9): ``column`` may be a list, or a comma-separated string
    of column names. In that case we aggregate each one independently and
    return a single multi-measure SDF (scalar without group_by, grouped
    otherwise).
    """
    if isinstance(group_by, str):
        group_by = [group_by]

    # Normalize column input: support lists and comma-separated strings (issue #9).
    if isinstance(column, str) and "," in column and not data.has_column(column):
        column_list = [c.strip() for c in column.split(",") if c.strip()]
    elif isinstance(column, list):
        column_list = [str(c).strip() for c in column if str(c).strip()]
    else:
        column_list = [column]

    # Filter to columns that actually exist (helps when the LLM lists extras).
    available_names = {c.name for c in data.columns}
    column_list_existing = [c for c in column_list if c in available_names]
    if not column_list_existing and column_list:
        # Fall back to original behaviour with the first item so existing
        # fuzzy-matching upstream still has a chance.
        column_list_existing = [column_list[0]]

    # Multi-column aggregation -> produce a multi-measure result (issue #9).
    if len(column_list_existing) > 1:
        return _aggregate_multi(data, column_list_existing, method, group_by, output, output_unit)

    # Single-column aggregation (original behavior).
    column = column_list_existing[0]

    # A1-FIX (from earlier branch): Auto-correct column name if it doesn't
    # exist in data. Kept after multi-column normalization so a single
    # mistyped column name still benefits from fuzzy matching.
    if not data.has_column(column):
        from system_b.computation.function_dispatcher import _find_best_column_match
        available = [c.name for c in data.columns]
        # source_ref exact match first (aggregate/join renames keep it)
        src_match = None
        for c in data.columns:
            if c.source_ref and c.source_ref == column:
                src_match = c.name
                break
        match = src_match or _find_best_column_match(column, available)
        if match:
            logger.info(f"[aggregate] Auto-corrected column: '{column}' -> '{match}'")
            column = match
        else:
            # 0723 问题46/101 fix: plans often reference the aggregation
            # method name ('sum'/'mean'/'total'...) as the column after an
            # upstream aggregate renamed the measure. If the data has exactly
            # one measure column AND the requested name is method-like (or
            # matches the measure's source_ref), that is unambiguously the
            # intent — previously this fell through and produced sum(None)=None.
            # NOTE: a genuinely DIFFERENT measure name (e.g. 'purchase_users'
            # against a generation-only table, 0723 问题61) must NOT silently
            # fall back — that would compute a wrong value; keep the null and
            # the loud warning instead.
            _method_like = {"sum", "mean", "avg", "average", "min", "max",
                            "total", "count", "value", "agg_result", "std", "median"}
            measures = data.get_measure_columns()
            if len(measures) == 1 and str(column).strip().lower() in _method_like:
                logger.warning(
                    f"[aggregate] Column '{column}' is method-like and not in "
                    f"data; falling back to the sole measure column '{measures[0]}'"
                )
                column = measures[0]
            else:
                logger.warning(f"[aggregate] Column '{column}' not found. Available: {available}")

    # Get unit from original column if not specified
    if output_unit is None:
        col_def = data.get_column_def(column)
        if col_def:
            output_unit = col_def.unit

    # Normalize group_by: resolve date expression patterns into real column derivations
    # e.g. "strftime('%Y-%m', ptdate)" -> derive a virtual column from ptdate
    resolved_group_by, derived_columns = _resolve_group_by(data, group_by)

    # For count/count_distinct, we don't need to convert to float
    is_count_method = method.lower() in ("count", "count_distinct")

    # 0806 fix: 派生列描述用源列的中文描述组合（"抄见数的求和"）而非英文
    # 列名（"sum(copy_num)"），否则总结模型仍需猜测英文列名的含义。
    _src_col_def = data.get_column_def(column)
    _src_label = (_src_col_def.description if _src_col_def and _src_col_def.description
                  else column)
    _agg_desc = f"{_src_label}的{_ZH_METHOD.get(method.lower(), method)}"

    if not resolved_group_by:
        # Scalar aggregation
        if is_count_method:
            # A1-FIX: For count, keep ALL non-None values as-is (strings, numbers, etc.)
            # Do NOT use _to_float which converts strings like "风能" to None
            values = [row.get(column) for row in data.rows]
            values = [v for v in values if v is not None]
        else:
            values = [_to_float(row.get(column)) for row in data.rows]
            values = [v for v in values if v is not None]
        result = _compute_agg(values, method)

        return StandardDataFrame.from_scalar(
            value=result,
            name=output if output != "agg_result" else column,
            unit=output_unit,
            source_step=data.metadata.source_step,
            description=f"{_agg_desc} = {result}",
            lineage=list(data.metadata.lineage),
        )
    else:
        # Grouped aggregation
        # Apply derived columns to rows if any
        working_rows = data.rows
        if derived_columns:
            working_rows = []
            for row in data.rows:
                new_row = dict(row)
                for dc_name, dc_func in derived_columns.items():
                    new_row[dc_name] = dc_func(row)
                working_rows.append(new_row)

        groups = {}
        for row in working_rows:
            key = tuple(row.get(g) for g in resolved_group_by)
            groups.setdefault(key, []).append(row)

        result_rows = []
        for key, group_rows in groups.items():
            if is_count_method:
                values = [r.get(column) for r in group_rows]
                values = [v for v in values if v is not None]
            else:
                values = [_to_float(r.get(column)) for r in group_rows]
                values = [v for v in values if v is not None]
            agg_val = _compute_agg(values, method)
            new_row = {g: k for g, k in zip(resolved_group_by, key)}
            new_row[output if output != "agg_result" else column] = agg_val
            result_rows.append(new_row)

        # Build columns
        cols = []
        for g in resolved_group_by:
            orig_col = data.get_column_def(g)
            if orig_col:
                cols.append(orig_col.copy())
            else:
                cols.append(ColumnDef(name=g, role="dimension", data_type="string"))

        out_name = output if output != "agg_result" else column
        data_type = "integer" if is_count_method else "float"
        cols.append(ColumnDef(
            name=out_name, role="measure", data_type=data_type,
            unit=output_unit, description=_agg_desc,
            is_computed=True,
            # Keep the ORIGINAL measure name so downstream references to it
            # (sort_by / column / step_N.x.<orig>) resolve exactly instead of
            # depending on fuzzy matching (问题10/问题12 fix).
            source_ref=column if out_name != column else None,
        ))

        meta = Metadata.from_dict(data.metadata.to_dict())
        meta.row_count = len(result_rows)
        meta.description = f"{method}({column}) grouped by {resolved_group_by}"
        return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def _aggregate_multi(
    data: StandardDataFrame,
    columns: list,
    method: str,
    group_by: Optional[list],
    output: str,
    output_unit: Optional[str],
) -> StandardDataFrame:
    """Aggregate multiple columns side-by-side. Returns a multi-measure SDF.

    Implemented for issue #9 (LLM passes a comma-separated column string).
    Honors count/count_distinct (don't coerce strings to float in that case).
    """
    is_count_method = method.lower() in ("count", "count_distinct")

    def _values_for(col, rows):
        if is_count_method:
            return [r.get(col) for r in rows if r.get(col) is not None]
        return [v for v in (_to_float(r.get(col)) for r in rows) if v is not None]

    measure_data_type = "integer" if is_count_method else "float"

    if not group_by:
        result_row = {}
        cols = []
        desc_parts = []
        for col in columns:
            values = _values_for(col, data.rows)
            agg_val = _compute_agg(values, method)
            measure_name = f"{method}_{col}" if output in ("agg_result",) else col
            result_row[measure_name] = agg_val

            col_def = data.get_column_def(col)
            unit = col_def.unit if col_def else output_unit
            _lbl = (col_def.description if col_def and col_def.description else col)
            _zh = _ZH_METHOD.get(method.lower(), method)
            cols.append(ColumnDef(
                name=measure_name, role="measure", data_type=measure_data_type,
                unit=unit, description=f"{_lbl}的{_zh} = {agg_val}",
                is_computed=True,
            ))
            desc_parts.append(f"{_lbl}的{_zh}={agg_val}")

        meta = Metadata(
            source_step=data.metadata.source_step,
            description=", ".join(desc_parts),
            row_count=1,
            is_scalar=True,
            lineage=list(data.metadata.lineage),
        )
        return StandardDataFrame(metadata=meta, columns=cols, rows=[result_row])

    # Grouped multi-aggregation
    groups = {}
    for row in data.rows:
        key = tuple(row.get(g) for g in group_by)
        groups.setdefault(key, []).append(row)

    result_rows = []
    for key, group_rows in groups.items():
        new_row = {g: k for g, k in zip(group_by, key)}
        for col in columns:
            values = _values_for(col, group_rows)
            new_row[f"{method}_{col}"] = _compute_agg(values, method)
        result_rows.append(new_row)

    cols = []
    for g in group_by:
        orig_col = data.get_column_def(g)
        cols.append(orig_col.copy() if orig_col else ColumnDef(name=g, role="dimension", data_type="string"))
    for col in columns:
        col_def = data.get_column_def(col)
        unit = col_def.unit if col_def else output_unit
        _lbl = (col_def.description if col_def and col_def.description else col)
        cols.append(ColumnDef(
            name=f"{method}_{col}", role="measure", data_type=measure_data_type,
            unit=unit, description=f"{_lbl}的{_ZH_METHOD.get(method.lower(), method)}",
            is_computed=True,
        ))

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"{method}({columns}) grouped by {group_by}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def _compute_agg(values: list, method: str):
    """Compute aggregation on a list of values (numeric for sum/mean/max/min/std/median, any for count)."""
    if not values:
        return None
    method = method.lower()
    if method == "sum":
        return round(sum(values), 6)
    elif method in ("mean", "avg", "average"):
        return round(sum(values) / len(values), 6)
    elif method == "max":
        return max(values)
    elif method == "min":
        return min(values)
    elif method == "count":
        return len(values)
    elif method == "count_distinct":
        return len(set(values))
    elif method in ("std", "stddev", "stdev", "standard_deviation"):
        # Population standard deviation (问题10 fix: 'std' was unsupported and
        # crashed 波动率/阈值-type plans).
        n = len(values)
        if n == 1:
            return 0.0
        m = sum(values) / n
        return round((sum((v - m) ** 2 for v in values) / n) ** 0.5, 6)
    elif method == "median":
        s = sorted(values)
        n = len(s)
        mid = n // 2
        return s[mid] if n % 2 == 1 else round((s[mid - 1] + s[mid]) / 2, 6)
    else:
        raise ValueError(f"Unknown aggregation method: {method}")


def _resolve_group_by(data: StandardDataFrame, group_by: Optional[list]) -> tuple:
    """
    Resolve group_by expressions.
    
    Handles:
    - Normal column names: returned as-is
    - SQL-style date expressions like "strftime('%Y-%m', ptdate)" -> derive 'year_month' virtual column
    - Expression patterns like "YEAR(ptdate)", "MONTH(ptdate)" -> derive virtual columns
    - Pseudo time dimensions ptweek/ptyear/ptmonth/ptquarter (metadata-only
      dims that are NOT real columns): derived from the date column (问题5/问题8 fix)
    
    Returns:
        (resolved_group_by_list, derived_columns_dict)
        derived_columns_dict maps virtual_col_name -> function(row) -> value
    """
    if not group_by:
        return group_by, {}

    resolved = []
    derived = {}

    for g in group_by:
        # Check if it's an actual column in data
        if data.has_column(g):
            resolved.append(g)
            continue

        # Pattern: strftime('%Y-%m', col) or strftime('%Y', col)
        m = re.match(r"strftime\(['\"](%[^'\"]+)['\"],\s*(\w+)\)", g)
        if m:
            fmt_str = m.group(1)
            source_col = m.group(2)
            virtual_name = _derive_name_from_format(fmt_str, source_col)
            derived[virtual_name] = _make_strftime_func(fmt_str, source_col)
            resolved.append(virtual_name)
            logger.info(f"[aggregate] Resolved group_by expression '{g}' -> virtual column '{virtual_name}'")
            continue

        # Pattern: YEAR(col), MONTH(col), QUARTER(col)
        m = re.match(r"(YEAR|MONTH|QUARTER|DAY)\((\w+)\)", g, re.IGNORECASE)
        if m:
            func_name = m.group(1).lower()
            source_col = m.group(2)
            virtual_name = f"{func_name}_{source_col}"
            derived[virtual_name] = _make_date_part_func(func_name, source_col)
            resolved.append(virtual_name)
            logger.info(f"[aggregate] Resolved group_by expression '{g}' -> virtual column '{virtual_name}'")
            continue

        # Pattern: extract_week(col) / extract_year(col) / extract_month(col)
        # / extract_year_month(col) / extract_quarter(col) — LLM plans sometimes
        # emit function-call style group_by using the extract_* function names
        # (新问题3 step_4 used "extract_week(ptdate)" which previously fell
        # through to the as-is fallback and grouped every row under None).
        m = re.match(r"extract_(year_month|year|month|quarter|week|weekday|day)\((\w+)\)", g, re.IGNORECASE)
        if m:
            part = m.group(1).lower()
            source_col = m.group(2)
            if part == "year_month":
                virtual_name = "year_month"
                derived[virtual_name] = _make_strftime_func("%Y-%m", source_col)
            elif part == "week":
                virtual_name = "week"
                derived[virtual_name] = _make_week_func(source_col)
            elif part == "weekday":
                virtual_name = "weekday_type"
                derived[virtual_name] = _make_date_part_func("day", source_col)
            else:
                virtual_name = part
                derived[virtual_name] = _make_date_part_func(part, source_col)
            resolved.append(virtual_name)
            logger.info(f"[aggregate] Resolved group_by expression '{g}' -> virtual column '{virtual_name}'")
            continue

        # Pseudo time dimensions from DB metadata: ptweek()/ptyear()/... are
        # placeholders, not real columns. Derive them from the actual date
        # column so weekly / yearly grouping works (问题5/问题8 fix).
        g_norm = g.lower().rstrip("()")
        pseudo_map = {"ptyear": "year", "ptmonth": "month",
                      "ptquarter": "quarter", "ptweek": "week"}
        if g_norm in pseudo_map:
            date_col = _find_date_column(data)
            if date_col:
                part = pseudo_map[g_norm]
                virtual_name = g_norm  # keep the requested name as output dim
                if part == "week":
                    derived[virtual_name] = _make_week_func(date_col)
                else:
                    derived[virtual_name] = _make_date_part_func(part, date_col)
                resolved.append(virtual_name)
                logger.info(
                    f"[aggregate] Pseudo dimension '{g}' derived from date "
                    f"column '{date_col}' as '{virtual_name}'"
                )
                continue
            logger.warning(
                f"[aggregate] Pseudo dimension '{g}' requested but no date "
                f"column found in data"
            )

        # 0723 问题48/49 fix: the plan groups by a dimension that does NOT
        # exist in the data (e.g. group_by=['dis_org_name'] when the query
        # returned 'gdj'). Previously the key was used as-is, every row
        # collapsed into ONE group with key None and the per-district
        # breakdown silently vanished. Fall back to the best available
        # non-time dimension column instead:
        #   1) fuzzy match against the real dimension columns
        #   2) if exactly ONE non-date dimension exists, use it
        dim_cols = [c.name for c in data.columns if c.role == "dimension"]
        non_time_dims = []
        for d in dim_cols:
            dl = d.lower()
            if dl in ("ptdate", "date", "pdate", "ymd") or "date" in dl:
                continue
            if dl in ("year", "month", "week", "quarter", "year_month",
                      "weekday", "weekday_type", "day_type", "is_holiday",
                      "holiday_name"):
                continue
            non_time_dims.append(d)
        from system_b.computation.function_dispatcher import _find_best_column_match
        fuzzy = _find_best_column_match(g, non_time_dims) if non_time_dims else None
        if fuzzy:
            logger.warning(
                f"[aggregate] group_by '{g}' not in data; fuzzy-matched to "
                f"dimension column '{fuzzy}'"
            )
            resolved.append(fuzzy)
            continue
        if len(non_time_dims) == 1:
            logger.warning(
                f"[aggregate] group_by '{g}' not in data; falling back to the "
                f"sole non-time dimension column '{non_time_dims[0]}'"
            )
            resolved.append(non_time_dims[0])
            continue

        # Fallback: use as-is (may result in None grouping key, but don't crash)
        logger.warning(f"[aggregate] group_by '{g}' not recognized as column or expression, using as-is")
        resolved.append(g)

    return resolved, derived


def _find_date_column(data: StandardDataFrame) -> Optional[str]:
    """Locate the date column in an SDF (by common names, then by value shape)."""
    preferred = ["ptdate", "date", "pdate", "day", "ymd"]
    names = [c.name for c in data.columns]
    for p in preferred:
        for n in names:
            if n.lower() == p:
                return n
    # Heuristic: first dimension column whose values look like YYYY-MM-DD
    for c in data.columns:
        if c.role != "dimension":
            continue
        for row in data.rows[:5]:
            v = row.get(c.name)
            if v is not None:
                s = str(v)
                if len(s) >= 10 and s[4] in "-/" and s[7] in "-/":
                    return c.name
                break
    return None


def _make_week_func(source_col: str):
    """Create a function deriving ISO year-week (e.g. '2026-W23') from a date column."""
    from datetime import datetime as _dt

    def func(row):
        val = row.get(source_col)
        if val is None:
            return None
        if hasattr(val, "isocalendar"):
            iso = val.isocalendar()
            return f"{iso[0]}-W{iso[1]:02d}"
        s = str(val).strip().replace("/", "-")[:10]
        try:
            dt = _dt.strptime(s, "%Y-%m-%d")
            iso = dt.isocalendar()
            return f"{iso[0]}-W{iso[1]:02d}"
        except ValueError:
            return None
    return func


def _derive_name_from_format(fmt_str: str, source_col: str) -> str:
    """Generate a virtual column name from a strftime format string."""
    if fmt_str == "%Y":
        return f"year_{source_col}" if source_col != "year" else "year"
    elif fmt_str in ("%Y-%m", "%Y%m"):
        return f"year_month"
    elif fmt_str == "%m":
        return f"month_{source_col}" if source_col != "month" else "month"
    elif fmt_str == "%Y-%m-%d":
        return source_col  # already date
    else:
        return f"derived_{source_col}"


def _make_strftime_func(fmt_str: str, source_col: str):
    """Create a function that extracts a date part from a row's column."""
    def func(row):
        val = row.get(source_col)
        if val is None:
            return None
        s = str(val)
        try:
            if fmt_str == "%Y":
                return s[:4]
            elif fmt_str in ("%Y-%m", "%Y%m"):
                # Handle "2026-02-01" -> "2026-02"
                if len(s) >= 7:
                    return s[:7]
                return s[:4]
            elif fmt_str == "%m":
                if len(s) >= 7:
                    return s[5:7]
                return None
            else:
                # Try generic approach
                from datetime import datetime
                for date_fmt in ["%Y-%m-%d", "%Y-%m-%d %H:%M:%S", "%Y/%m/%d"]:
                    try:
                        dt = datetime.strptime(s, date_fmt)
                        return dt.strftime(fmt_str)
                    except ValueError:
                        continue
                return s[:len(fmt_str)] if len(s) >= len(fmt_str) else s
        except Exception:
            return None
    return func


def _make_date_part_func(func_name: str, source_col: str):
    """Create a function that extracts year/month/quarter/day from a date column."""
    def func(row):
        val = row.get(source_col)
        if val is None:
            return None
        s = str(val)
        try:
            if func_name == "year":
                return s[:4]
            elif func_name == "month":
                if len(s) >= 7:
                    return s[5:7]
                return None
            elif func_name == "quarter":
                if len(s) >= 7:
                    month = int(s[5:7])
                    return str((month - 1) // 3 + 1)
                return None
            elif func_name == "day":
                if len(s) >= 10:
                    return s[8:10]
                return None
            else:
                return None
        except Exception:
            return None
    return func
