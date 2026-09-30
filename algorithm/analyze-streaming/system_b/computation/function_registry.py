"""
Function Registry
==================
Central registry mapping function names to implementations.
"""

from system_b.computation.functions import (
    arithmetic,
    sorting_filtering,
    aggregation,
    time_series,
    forecast_module,
    data_operations,
    ratio_module,
    change_module,
    holidays,
)

FUNCTION_REGISTRY = {
    # Basic arithmetic
    "add": {
        "func": arithmetic.add,
        "description": "Two datasets addition",
        "required_params": ["col_a", "col_b"],
        "optional_params": ["join_keys"],
    },
    "subtract": {
        "func": arithmetic.subtract,
        "description": "Two datasets subtraction",
        "required_params": ["col_a", "col_b"],
        "optional_params": ["join_keys"],
    },
    "multiply": {
        "func": arithmetic.multiply,
        "description": "Two datasets multiplication",
        "required_params": ["col_a", "col_b"],
        "optional_params": ["join_keys"],
    },
    "divide": {
        "func": arithmetic.divide,
        "description": "Two datasets division (zero-safe)",
        "required_params": ["col_a", "col_b"],
        "optional_params": ["join_keys"],
    },
    # Sorting & filtering
    "top_n": {
        "func": sorting_filtering.top_n,
        "description": "Top N rows by column descending",
        "required_params": ["data", "sort_by", "n"],
        "optional_params": [],
    },
    "bottom_n": {
        "func": sorting_filtering.bottom_n,
        "description": "Bottom N rows by column ascending",
        "required_params": ["data", "sort_by", "n"],
        "optional_params": [],
    },
    "filter": {
        "func": sorting_filtering.filter_data,
        "description": "Filter rows by condition",
        "required_params": ["data", "column", "operator", "value"],
        "optional_params": ["logic", "conditions"],
    },
    "rank": {
        "func": sorting_filtering.rank,
        "description": "Rank rows by column",
        "required_params": ["data", "sort_by", "order"],
        "optional_params": ["group_by"],
    },
    # Aggregation
    "aggregate": {
        "func": aggregation.aggregate,
        "description": "Aggregate (sum/mean/max/min/count/count_distinct/std/median) with optional group_by",
        "required_params": ["data", "column", "method"],
        "optional_params": ["group_by"],
    },
    # Time series
    # NOTE (函数库梳理): year_over_year / period_over_period were REMOVED.
    # They duplicated the standard subtract + divide(multiply_by_100=true)
    # pattern documented in the prompt, were already marked deprecated there,
    # and their自动 period-pairing logic produced wrong pairings on irregular
    # data. 同比/环比 must be computed via subtract + divide.
    "consecutive_check": {
        "func": time_series.consecutive_check,
        "description": "Consecutive condition check",
        "required_params": ["data", "column", "condition", "n", "time_column"],
        "optional_params": ["threshold", "group_by"],
    },
    "trend_analysis": {
        "func": time_series.trend_analysis,
        "description": "Trend analysis (linear regression)",
        "required_params": ["data", "value_column", "time_column"],
        "optional_params": ["group_by"],
    },
    # Forecast
    "forecast": {
        "func": forecast_module.forecast,
        "description": "Linear regression forecast",
        "required_params": ["data", "value_column", "time_column", "periods"],
        "optional_params": ["group_by"],
    },
    # Data operations
    "extract_dimension": {
        "func": data_operations.extract_dimension,
        "description": "Extract dimension column (with optional dedup)",
        "required_params": ["data", "column"],
        "optional_params": ["distinct", "keep_all_columns"],
    },
    "extract_year_month": {
        "func": data_operations.extract_year_month,
        "description": "Add a 'year_month' (YYYY-MM) dimension column derived from a date column (fixed output column name: year_month)",
        "required_params": ["data", "date_column"],
        "optional_params": [],
    },
    "extract_year": {
        "func": data_operations.extract_year,
        "description": "Add a 'year' (YYYY) dimension column derived from a date column (fixed output column name: year)",
        "required_params": ["data", "date_column"],
        "optional_params": [],
    },
    "extract_month": {
        "func": data_operations.extract_month,
        "description": "Add a 'month' (MM) dimension column derived from a date column (fixed output column name: month)",
        "required_params": ["data", "date_column"],
        "optional_params": [],
    },
    "extract_week": {
        "func": data_operations.extract_week,
        "description": "Add a 'week' (ISO year-week, e.g. 2026-W23) dimension column derived from a date column; use before weekly aggregation (fixed output column name: week)",
        "required_params": ["data", "date_column"],
        "optional_params": [],
    },
    "extract_weekday": {
        "func": data_operations.extract_weekday,
        "description": "Add 'weekday' (星期一..星期日) and 'weekday_type' (工作日/周末) dimension columns derived from a date column",
        "required_params": ["data", "date_column"],
        "optional_params": [],
    },
    "mark_holidays": {
        "func": holidays.mark_holidays,
        "description": "Add 'day_type'(法定节假日/周末/调休上班日/工作日), 'is_holiday'(是/否), 'holiday_name'(节日名), 'weekday'(星期一~星期日) dimension columns from a date column, using hardcoded 2021-2026 Chinese holiday calendar (statutory holidays + makeup workdays). Column names are FIXED regardless of the operation's output name",
        "required_params": ["data", "date_column"],
        "optional_params": ["output"],
    },
    "filter_holidays": {
        "func": holidays.filter_holidays,
        "description": "Filter rows by holiday attribute of a date column. keep='holiday'(法定节假日+周末,默认)/'workday'(工作日含调休上班日)/'statutory'(仅法定节假日)/'weekend'(仅普通周末). Uses hardcoded 2021-2026 calendar",
        "required_params": ["data", "date_column"],
        "optional_params": ["keep"],
    },
    "get_holidays": {
        "func": holidays.get_holidays,
        "description": "Directly query the holiday list (date/day_type/holiday_name/weekday) within [start,end] from hardcoded 2021-2026 calendar; no input data needed. include_weekends=false to return statutory holidays only",
        "required_params": ["start", "end"],
        "optional_params": ["include_weekends"],
    },
    "cumulative_sum": {
        "func": data_operations.cumulative_sum,
        "description": "Running cumulative sum of a column ordered by time_column (fixed output column name: cumulative_sum)",
        "required_params": ["data", "column"],
        "optional_params": ["time_column", "group_by"],
    },
    "latest_data": {
        "func": data_operations.latest_data,
        "description": "Snapshot metric: keep the latest row per group by time_column. Without group_by, auto-groups by ALL non-time dimension columns. Non-snapshot measure columns are dropped from the output. ONLY for snapshot metrics (agg_type='快照'); never aggregate snapshots with sum/mean",
        "required_params": ["data", "time_column"],
        "optional_params": ["group_by"],
    },
    "union": {
        "func": data_operations.union,
        "description": "Vertically union multiple datasets",
        "required_params": ["datasets"],
        "optional_params": [],
    },
    "join": {
        "func": data_operations.join,
        "description": "Join two datasets",
        "required_params": ["left", "right", "on"],
        "optional_params": ["how"],
    },
    "group_apply": {
        "func": data_operations.group_apply,
        "description": "Apply operation within groups",
        "required_params": ["data", "group_by", "operation", "operation_params"],
        "optional_params": [],
    },
    "shift": {
        "func": data_operations.shift,
        "description": "Row shift operation",
        "required_params": ["data", "column", "offset"],
        "optional_params": ["group_by", "time_column"],
    },
    "time_diff": {
        "func": data_operations.time_diff,
        "description": "Time difference between two columns",
        "required_params": ["data", "start_column", "end_column", "unit"],
        "optional_params": [],
    },
    # Ratio
    "ratio": {
        "func": ratio_module.ratio,
        "description": "Percentage/ratio calculation",
        "required_params": ["data", "column", "total"],
        "optional_params": ["group_by"],
    },
    # Change
    "change": {
        "func": change_module.change,
        "description": "Absolute and relative change between two periods",
        "required_params": ["current_data", "previous_data", "value_column"],
        "optional_params": ["join_keys"],
    },
}


def get_function(name: str):
    """Get the callable function by name."""
    entry = FUNCTION_REGISTRY.get(name)
    if entry:
        return entry["func"]
    return None


def get_function_info(name: str) -> dict:
    """Get function info from registry."""
    return FUNCTION_REGISTRY.get(name)


def list_functions() -> list[str]:
    """List all registered function names."""
    return list(FUNCTION_REGISTRY.keys())


def get_functions_prompt_text() -> str:
    """Generate the functions catalog text for prompt injection."""
    lines = ["=== Preset Computation Function Library ===\n"]

    categories = {
        "Basic Arithmetic": ["add", "subtract", "multiply", "divide"],
        "Sorting & Filtering": ["top_n", "bottom_n", "filter", "rank"],
        "Aggregation": ["aggregate"],
        "Time Series Analysis": ["consecutive_check", "trend_analysis", "cumulative_sum", "latest_data"],
        "Forecast": ["forecast"],
        "Data Operations": ["extract_dimension", "union", "join", "group_apply", "shift", "time_diff",
                            "extract_year_month", "extract_year", "extract_month", "extract_week", "extract_weekday"],
        "Holidays (2021-2026 内置节假日日历)": ["mark_holidays", "filter_holidays", "get_holidays"],
        "Ratio": ["ratio"],
        "Change": ["change"],
    }

    for cat_name, func_names in categories.items():
        lines.append(f"[{cat_name}]")
        for fname in func_names:
            info = FUNCTION_REGISTRY[fname]
            req = ", ".join(info["required_params"])
            opt = ", ".join(f"[{p}]" for p in info["optional_params"])
            params = req + (", " + opt if opt else "")
            lines.append(f"- {fname}({params}): {info['description']}")
        lines.append("")

    return "\n".join(lines)
