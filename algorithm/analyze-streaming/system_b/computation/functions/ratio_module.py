"""
Ratio Function: Calculate proportions/percentages
"""

import logging
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


def ratio(
    data: StandardDataFrame,
    column: str,
    total,
    group_by: Optional[list] = None,
    output: str = "ratio_result",
    output_unit: str = "%",
    **kwargs,
) -> StandardDataFrame:
    """
    Calculate ratio (percentage) of a column relative to total.
    total can be:
    - A numeric constant
    - A scalar StandardDataFrame
    - A string referencing a column in the same data for group total
    """
    if isinstance(group_by, str):
        group_by = [group_by]

    # Resolve total
    if isinstance(total, StandardDataFrame):
        if total.metadata.is_scalar:
            total_value = _to_float(total.get_scalar_value())
        else:
            # Use sum of measure column
            measures = total.get_measure_columns()
            if measures:
                vals = [_to_float(r.get(measures[0])) for r in total.rows]
                vals = [v for v in vals if v is not None]
                total_value = sum(vals) if vals else None
            else:
                total_value = None
    elif isinstance(total, (int, float)):
        total_value = float(total)
    elif isinstance(total, str):
        # String total: could be a column name or reference
        if data.has_column(total):
            vals = [_to_float(r.get(total)) for r in data.rows]
            vals = [v for v in vals if v is not None]
            distinct = set(vals)
            if len(distinct) == 1:
                # The "total" column holds a single broadcast value (e.g. a
                # grand total cross-joined onto every row). Use that value
                # directly instead of summing it across rows (which would
                # multiply the denominator by the row count).
                total_value = vals[0] if vals else None
            else:
                total_value = sum(vals) if vals else None
        else:
            try:
                total_value = float(total.replace(",", "") if isinstance(total, str) else total)
            except (ValueError, TypeError):
                total_value = None
    else:
        total_value = None

    # 问题11 fix: prompt rule 13 documents the ratio output column as
    # "ratio_value". Default to that canonical name; when the plan supplies a
    # custom output name we keep it but record source_ref="ratio_value" so
    # downstream references to the canonical name resolve exactly.
    ratio_col_name = output if output not in ("ratio_result", "result", None) else "ratio_value"

    if group_by:
        groups = {}
        for row in data.rows:
            key = tuple(row.get(g) for g in group_by)
            groups.setdefault(key, []).append(row)

        result_rows = []
        for key, group_rows in groups.items():
            # Calculate group total
            vals = [_to_float(r.get(column)) for r in group_rows]
            vals_non_null = [v for v in vals if v is not None]
            group_total = sum(vals_non_null) if vals_non_null else None

            for row in group_rows:
                new_row = dict(row)
                val = _to_float(row.get(column))
                if val is not None and group_total and group_total != 0:
                    new_row[ratio_col_name] = round(val / group_total * 100, 2)
                else:
                    new_row[ratio_col_name] = None
                result_rows.append(new_row)
    else:
        result_rows = []
        for row in data.rows:
            new_row = dict(row)
            val = _to_float(row.get(column))
            if val is not None and total_value and total_value != 0:
                new_row[ratio_col_name] = round(val / total_value * 100, 2)
            else:
                new_row[ratio_col_name] = None
            result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    # 0806 fix: 派生列描述用源列中文描述（"抄见数占比"）而非英文列名
    _src_def = data.get_column_def(column)
    _src_label = (_src_def.description if _src_def and _src_def.description else column)
    cols.append(ColumnDef(
        name=ratio_col_name, role="measure", data_type="float",
        unit=output_unit, description=f"{_src_label}占比",
        is_computed=True,
        source_ref="ratio_value" if ratio_col_name != "ratio_value" else None,
    ))

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Ratio calculation: {column} / total"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
