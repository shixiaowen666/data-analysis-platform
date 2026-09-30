"""
Forecast Function: Simple linear regression prediction
"""

import logging
import re
from datetime import datetime, timedelta
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


def _try_parse_date(val):
    """Try to parse a value to a datetime. Returns (datetime, format_str) or (None, None)."""
    if val is None:
        return None, None
    s = str(val)
    for fmt in ("%Y-%m-%d", "%Y-%m", "%Y/%m/%d", "%Y/%m", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(s, fmt), fmt
        except ValueError:
            continue
    return None, None


def _next_date(last_dt: datetime, fmt: str, p: int) -> str:
    """Compute the date string `p` periods after last_dt for the given fmt."""
    if fmt in ("%Y-%m-%d", "%Y/%m/%d"):
        return (last_dt + timedelta(days=p)).strftime(fmt)
    if fmt in ("%Y-%m", "%Y/%m"):
        # Add p months
        year = last_dt.year + (last_dt.month - 1 + p) // 12
        month = (last_dt.month - 1 + p) % 12 + 1
        return datetime(year, month, 1).strftime(fmt)
    if fmt == "%Y-%m-%d %H:%M:%S":
        return (last_dt + timedelta(days=p)).strftime(fmt)
    return (last_dt + timedelta(days=p)).strftime("%Y-%m-%d")


def forecast(
    data: StandardDataFrame,
    value_column: str,
    time_column: str,
    periods: int,
    group_by: Optional[list] = None,
    output: str = "forecast_result",
    output_unit: Optional[str] = None,
    **kwargs,
) -> StandardDataFrame:
    """
    Simple linear regression forecast.
    Returns predicted values and confidence intervals for future periods.

    BUGFIX (issue #1): The forecast result now also carries:
      - the original `time_column` (with extrapolated future dates) so downstream
        date-range filters work,
      - the original `value_column` aliased to the predicted value, so downstream
        aggregations on the original measure name work.
    """
    periods = int(periods)
    if isinstance(group_by, str):
        group_by = [group_by]

    if group_by:
        groups = {}
        for row in data.rows:
            key = tuple(row.get(g) for g in group_by)
            groups.setdefault(key, []).append(row)
    else:
        groups = {(): list(data.rows)}

    if output_unit is None:
        col_def = data.get_column_def(value_column)
        if col_def:
            output_unit = col_def.unit

    # Detect whether time_column contains parsable dates so we can extrapolate.
    sample_time_vals = [r.get(time_column) for r in data.rows if r.get(time_column) is not None][:5]
    detected_fmt = None
    for sv in sample_time_vals:
        _, fmt = _try_parse_date(sv)
        if fmt:
            detected_fmt = fmt
            break

    result_rows = []
    for key, group_rows in groups.items():
        sorted_rows = sorted(group_rows, key=lambda r: str(r.get(time_column, "")))
        values = [_to_float(r.get(value_column)) for r in sorted_rows]
        valid = [(i, v) for i, v in enumerate(values) if v is not None]

        if len(valid) < 2:
            continue

        x = [p[0] for p in valid]
        y = [p[1] for p in valid]
        n = len(x)
        x_mean = sum(x) / n
        y_mean = sum(y) / n
        ss_xy = sum((xi - x_mean) * (yi - y_mean) for xi, yi in zip(x, y))
        ss_xx = sum((xi - x_mean) ** 2 for xi in x)

        if ss_xx == 0:
            slope = 0
            intercept = y_mean
        else:
            slope = ss_xy / ss_xx
            intercept = y_mean - slope * x_mean

        # Calculate residual standard error
        residuals = [yi - (slope * xi + intercept) for xi, yi in zip(x, y)]
        if n > 2:
            residual_se = (sum(r ** 2 for r in residuals) / (n - 2)) ** 0.5
        else:
            residual_se = 0

        # Determine last observed time value for extrapolation
        last_time_str = None
        last_time_dt = None
        if sorted_rows:
            last_time_str = sorted_rows[-1].get(time_column)
            if detected_fmt and last_time_str:
                last_time_dt, _ = _try_parse_date(last_time_str)

        # Predict future periods
        last_x = max(x)
        for p in range(1, periods + 1):
            future_x = last_x + p
            predicted = round(slope * future_x + intercept, 4)
            ci_lower = round(predicted - 1.96 * residual_se, 4)
            ci_upper = round(predicted + 1.96 * residual_se, 4)

            new_row = {}
            if group_by:
                for g, k in zip(group_by, key):
                    new_row[g] = k
            # Extrapolated time value (so downstream date filters work)
            if last_time_dt is not None and detected_fmt is not None:
                new_row[time_column] = _next_date(last_time_dt, detected_fmt, p)
            else:
                # Fall back to integer offset string
                new_row[time_column] = f"{last_time_str}+{p}" if last_time_str else str(p)

            new_row["forecast_period"] = p
            new_row["predicted_value"] = predicted
            # Alias predicted value under the original value_column so downstream
            # aggregate/filter operations using the original column name still work.
            new_row[value_column] = predicted
            new_row["ci_lower"] = ci_lower
            new_row["ci_upper"] = ci_upper
            result_rows.append(new_row)

    cols = []
    # Preserve group_by dimensions
    if group_by:
        for g in group_by:
            orig = data.get_column_def(g)
            if orig:
                cols.append(orig.copy())
            else:
                cols.append(ColumnDef(name=g, role="dimension", data_type="string"))

    # Carry the time_column as a dimension (extrapolated future dates).
    orig_time_col = data.get_column_def(time_column)
    if orig_time_col:
        time_col_def = orig_time_col.copy()
        time_col_def.role = "dimension"
        # If we synthesized date strings, mark as date; otherwise inherit type.
        if detected_fmt:
            time_col_def.data_type = "date"
        cols.append(time_col_def)
    else:
        cols.append(ColumnDef(
            name=time_column, role="dimension",
            data_type="date" if detected_fmt else "string",
            description=f"Forecast time column ({time_column})",
        ))

    # 0806 fix: 预测派生列描述用源列中文描述
    _orig_val_col = data.get_column_def(value_column)
    _val_label = (_orig_val_col.description if _orig_val_col and _orig_val_col.description
                  else value_column)
    cols.extend([
        ColumnDef(name="forecast_period", role="dimension", data_type="int", description="预测期数偏移"),
        ColumnDef(name="predicted_value", role="measure", data_type="float", unit=output_unit, description=f"{_val_label}预测值", is_computed=True),
    ])

    # Carry the original value_column (aliased to predicted value) as a measure
    # so downstream aggregations using the original column name still work.
    orig_val_col = _orig_val_col
    if orig_val_col:
        val_col_def = orig_val_col.copy()
        val_col_def.is_computed = True
        val_col_def.description = f"{_val_label}预测值"
        cols.append(val_col_def)
    else:
        cols.append(ColumnDef(
            name=value_column, role="measure", data_type="float",
            unit=output_unit, description=f"{_val_label}预测值", is_computed=True,
        ))

    cols.extend([
        ColumnDef(name="ci_lower", role="measure", data_type="float", unit=output_unit, description="95%置信区间下界", is_computed=True),
        ColumnDef(name="ci_upper", role="measure", data_type="float", unit=output_unit, description="95%置信区间上界", is_computed=True),
    ])

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Forecast {periods} periods on {value_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
