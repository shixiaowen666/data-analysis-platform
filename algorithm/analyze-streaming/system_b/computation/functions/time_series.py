"""
Time Series Functions: consecutive_check, trend_analysis
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


def _parse_time_value(val):
    """Parse a time value to a comparable form."""
    if val is None:
        return None
    if isinstance(val, (int, float)):
        return int(val)
    s = str(val)
    # Try YYYY-MM-DD
    for fmt in ["%Y-%m-%d", "%Y-%m", "%Y/%m/%d", "%Y/%m"]:
        try:
            return datetime.strptime(s, fmt)
        except ValueError:
            pass
    # Try pure year or month number
    try:
        return int(s)
    except (ValueError, TypeError):
        return s


# =========================================================================
# 函数库梳理 (2026-07): year_over_year 与 period_over_period 已删除。
# 原因:
# 1. 与提示词规范的标准同比/环比模式重复 —— subtract + divide(multiply_by_100=true)
#    已完全覆盖其功能, 且提示词早已标注两函数"已废弃、禁止使用"。
# 2. 其自动期间配对逻辑在数据不规整(缺月/跨表)时会产生错误配对, 是历史故障来源。
# 同比/环比一律通过 subtract + divide 组合完成。
# =========================================================================


def _period_index(time_val):
    """Return a comparable integer index for a time value so we can tell
    whether two adjacent rows are *adjacent periods* or have a gap.
    Supports yyyy / yyyy-mm / yyyy-mm-dd. Returns None if not parseable."""
    if time_val is None:
        return None
    s = str(time_val)
    # yyyy-mm-dd
    m = re.match(r"^(\d{4})-(\d{1,2})-(\d{1,2})$", s)
    if m:
        try:
            return datetime(int(m.group(1)), int(m.group(2)), int(m.group(3))).toordinal()
        except ValueError:
            return None
    # yyyy-mm
    m = re.match(r"^(\d{4})-(\d{1,2})$", s)
    if m:
        return int(m.group(1)) * 12 + int(m.group(2))
    # yyyy
    m = re.match(r"^(\d{4})$", s)
    if m:
        return int(m.group(1))
    try:
        return int(s)
    except (ValueError, TypeError):
        return None


def consecutive_check(
    data: StandardDataFrame,
    column: str,
    condition: str,
    n: int,
    time_column: str,
    threshold: Optional[float] = None,
    group_by: Optional[list] = None,
    output: str = "consecutive_result",
    **kwargs,
) -> StandardDataFrame:
    """
    Check for N consecutive periods meeting a condition.
    condition: rise/fall/above/below (also accepts: increasing/decreasing, up/down)

    BUGFIX (issue #7):
    - When `condition` is 'above' or 'below' and no `threshold` is provided,
      default to threshold=0 (the typical use-case in the LLM-generated plans
      is "above 0" to express "growth").
    - Track time-period adjacency: when consecutive rows in the (sorted) data
      are not in adjacent periods (e.g. after an upstream filter removed the
      non-matching months), reset the run-length counter so we never report
      "3 consecutive months" out of non-adjacent rows.
    """
    n = int(n)

    # Normalize condition aliases
    condition_aliases = {
        "decreasing": "fall",
        "declining": "fall",
        "decrease": "fall",
        "decline": "fall",
        "down": "fall",
        "increasing": "rise",
        "increase": "rise",
        "up": "rise",
        "ascending": "rise",
        "descending": "fall",
        "positive": "above",
        "negative": "below",
    }
    condition = condition_aliases.get(condition.lower(), condition.lower())

    # Default threshold for above/below to 0 when not provided.
    if condition in ("above", "below") and threshold is None:
        threshold = 0
        logger.info(
            f"[consecutive_check] No threshold provided for condition '{condition}', defaulting to 0"
        )

    if isinstance(group_by, str):
        group_by = [group_by]

    if group_by:
        groups = {}
        for row in data.rows:
            key = tuple(row.get(g) for g in group_by)
            groups.setdefault(key, []).append(row)
    else:
        groups = {(): list(data.rows)}

    result_rows = []
    for key, group_rows in groups.items():
        sorted_rows = sorted(group_rows, key=lambda r: str(r.get(time_column, "")))
        values = [_to_float(r.get(column)) for r in sorted_rows]
        period_indices = [_period_index(r.get(time_column)) for r in sorted_rows]

        # Build boolean series based on condition
        bools = []
        for i, val in enumerate(values):
            if val is None:
                bools.append(False)
                continue
            if condition == "rise":
                bools.append(i > 0 and values[i - 1] is not None and val > values[i - 1])
            elif condition == "fall":
                bools.append(i > 0 and values[i - 1] is not None and val < values[i - 1])
            elif condition == "above":
                bools.append(threshold is not None and val > threshold)
            elif condition == "below":
                bools.append(threshold is not None and val < threshold)
            else:
                bools.append(False)

        # Find consecutive runs of True >= n, but ALSO require time adjacency
        # (period_indices differ by exactly 1 between i-1 and i) when both are known.
        matched_indices = set()
        count = 0
        for i, b in enumerate(bools):
            if not b:
                count = 0
                continue
            adjacent = True
            if i > 0:
                p_now, p_prev = period_indices[i], period_indices[i - 1]
                if p_now is not None and p_prev is not None:
                    # For yyyy-mm-dd we used toordinal (daily diff = 1); for yyyy-mm
                    # we used year*12+month (monthly diff = 1); for yearly = 1.
                    # Anything else means a gap.
                    adjacent = (p_now - p_prev == 1)
            if not adjacent:
                count = 1  # restart run at this row
            else:
                count += 1
            if count >= n:
                for j in range(i - n + 1, i + 1):
                    matched_indices.add(j)

        for idx in sorted(matched_indices):
            new_row = dict(sorted_rows[idx])
            new_row["consecutive_match"] = True
            if group_by:
                for g, k in zip(group_by, key):
                    new_row[g] = k
            result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    cols.append(ColumnDef(name="consecutive_match", role="metadata", data_type="boolean", description=f"Consecutive {condition} check", is_computed=True))

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Consecutive {n} {condition} check on {column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def trend_analysis(
    data: StandardDataFrame,
    value_column: str,
    time_column: str,
    group_by: Optional[list] = None,
    output: str = "trend_result",
    **kwargs,
) -> StandardDataFrame:
    """
    Analyze trend direction using simple linear regression.
    Returns: trend_direction, slope, r_squared
    """
    if isinstance(group_by, str):
        group_by = [group_by]

    if group_by:
        groups = {}
        for row in data.rows:
            key = tuple(row.get(g) for g in group_by)
            groups.setdefault(key, []).append(row)
    else:
        groups = {(): list(data.rows)}

    result_rows = []
    for key, group_rows in groups.items():
        sorted_rows = sorted(group_rows, key=lambda r: str(r.get(time_column, "")))
        values = [_to_float(r.get(value_column)) for r in sorted_rows]
        valid = [(i, v) for i, v in enumerate(values) if v is not None]

        if len(valid) < 2:
            direction = "insufficient_data"
            slope = None
            r_squared = None
        else:
            x = [p[0] for p in valid]
            y = [p[1] for p in valid]
            n_pts = len(x)
            x_mean = sum(x) / n_pts
            y_mean = sum(y) / n_pts
            ss_xy = sum((xi - x_mean) * (yi - y_mean) for xi, yi in zip(x, y))
            ss_xx = sum((xi - x_mean) ** 2 for xi in x)
            ss_yy = sum((yi - y_mean) ** 2 for yi in y)

            if ss_xx == 0:
                slope = 0
                r_squared = 0
            else:
                slope = round(ss_xy / ss_xx, 6)
                if ss_yy == 0:
                    r_squared = 1.0 if ss_xy == 0 else 0.0
                else:
                    r_squared = round((ss_xy ** 2) / (ss_xx * ss_yy), 4)

            if abs(slope) < 0.001 * abs(y_mean) if y_mean != 0 else abs(slope) < 0.001:
                direction = "stable"
            elif slope > 0:
                direction = "rising"
            else:
                direction = "falling"

        new_row = {}
        if group_by:
            for g, k in zip(group_by, key):
                new_row[g] = k
        new_row["trend_direction"] = direction
        new_row["slope"] = slope
        new_row["r_squared"] = r_squared
        result_rows.append(new_row)

    cols = []
    if group_by:
        for g in group_by:
            orig = data.get_column_def(g)
            if orig:
                cols.append(orig.copy())
            else:
                cols.append(ColumnDef(name=g, role="dimension", data_type="string"))
    cols.extend([
        ColumnDef(name="trend_direction", role="measure", data_type="string", description="Trend direction", is_computed=True),
        ColumnDef(name="slope", role="measure", data_type="float", description="Linear regression slope", is_computed=True),
        ColumnDef(name="r_squared", role="measure", data_type="float", description="R-squared value", is_computed=True),
    ])

    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Trend analysis on {value_column}"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)
