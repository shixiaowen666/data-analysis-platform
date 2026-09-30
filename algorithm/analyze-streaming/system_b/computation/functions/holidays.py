"""
节假日函数库 (Holiday Functions)
=================================
按用户要求：把 2021-2026 年（六个年份）全部中国法定节假日 + 调休上班日
**写死在程序中**，需要时直接查询，不依赖任何外部服务。

判定口径：
- 法定节假日: 国务院办公厅历年放假通知中的放假日（含调休形成的连休日）
- 调休上班日: 放假通知中"上班"的周六/周日（视为工作日，不算节假日）
- 周末:       普通周六/周日（非调休上班日）
- 工作日:     普通周一至周五

"节假日" = 法定节假日 ∪ 周末（排除调休上班日） —— 与用户口径一致
（"包括周末及法定节假日"）。

数据来源（历年国办发明电通知）：
- 2021: 国办发明电〔2020〕27号
- 2022: 国办发明电〔2021〕11号
- 2023: 国办发明电〔2022〕16号
- 2024: 国办发明电〔2023〕7号
- 2025: 国办发明电〔2024〕17号
- 2026: 国办发明电（2025年11月4日发布）
"""

import logging
from datetime import datetime, timedelta

from system_b.models.standard_dataframe import StandardDataFrame, ColumnDef, Metadata

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# 硬编码数据: 2021-2026 法定节假日（放假日）
# ---------------------------------------------------------------------------

def _expand(start: str, end: str) -> list:
    """展开日期区间 [start, end] 为 YYYY-MM-DD 字符串列表（写死数据的展开辅助）。"""
    d0 = datetime.strptime(start, "%Y-%m-%d")
    d1 = datetime.strptime(end, "%Y-%m-%d")
    out = []
    while d0 <= d1:
        out.append(d0.strftime("%Y-%m-%d"))
        d0 += timedelta(days=1)
    return out


# (start, end, 节日名称) —— 放假通知中的放假日期段
_HOLIDAY_RANGES = [
    # ---------- 2021 ----------
    ("2021-01-01", "2021-01-03", "元旦"),
    ("2021-02-11", "2021-02-17", "春节"),
    ("2021-04-03", "2021-04-05", "清明节"),
    ("2021-05-01", "2021-05-05", "劳动节"),
    ("2021-06-12", "2021-06-14", "端午节"),
    ("2021-09-19", "2021-09-21", "中秋节"),
    ("2021-10-01", "2021-10-07", "国庆节"),
    # ---------- 2022 ----------
    ("2022-01-01", "2022-01-03", "元旦"),
    ("2022-01-31", "2022-02-06", "春节"),
    ("2022-04-03", "2022-04-05", "清明节"),
    ("2022-04-30", "2022-05-04", "劳动节"),
    ("2022-06-03", "2022-06-05", "端午节"),
    ("2022-09-10", "2022-09-12", "中秋节"),
    ("2022-10-01", "2022-10-07", "国庆节"),
    ("2022-12-31", "2022-12-31", "元旦"),   # 2023年元旦假期首日
    # ---------- 2023 ----------
    ("2023-01-01", "2023-01-02", "元旦"),
    ("2023-01-21", "2023-01-27", "春节"),
    ("2023-04-05", "2023-04-05", "清明节"),
    ("2023-04-29", "2023-05-03", "劳动节"),
    ("2023-06-22", "2023-06-24", "端午节"),
    ("2023-09-29", "2023-10-06", "中秋节、国庆节"),
    # ---------- 2024 ----------
    ("2024-01-01", "2024-01-01", "元旦"),
    ("2024-02-10", "2024-02-17", "春节"),
    ("2024-04-04", "2024-04-06", "清明节"),
    ("2024-05-01", "2024-05-05", "劳动节"),
    ("2024-06-10", "2024-06-10", "端午节"),
    ("2024-09-15", "2024-09-17", "中秋节"),
    ("2024-10-01", "2024-10-07", "国庆节"),
    # ---------- 2025 ----------
    ("2025-01-01", "2025-01-01", "元旦"),
    ("2025-01-28", "2025-02-04", "春节"),
    ("2025-04-04", "2025-04-06", "清明节"),
    ("2025-05-01", "2025-05-05", "劳动节"),
    ("2025-05-31", "2025-06-02", "端午节"),
    ("2025-10-01", "2025-10-08", "国庆节、中秋节"),
    # ---------- 2026 ----------
    ("2026-01-01", "2026-01-03", "元旦"),
    ("2026-02-15", "2026-02-23", "春节"),
    ("2026-04-04", "2026-04-06", "清明节"),
    ("2026-05-01", "2026-05-05", "劳动节"),
    ("2026-06-19", "2026-06-21", "端午节"),
    ("2026-09-25", "2026-09-27", "中秋节"),
    ("2026-10-01", "2026-10-07", "国庆节"),
]

# 法定节假日: date -> 节日名称
STATUTORY_HOLIDAYS = {}
for _s, _e, _name in _HOLIDAY_RANGES:
    for _d in _expand(_s, _e):
        STATUTORY_HOLIDAYS[_d] = _name

# 调休上班日（放假通知中"上班"的周六/周日）: date -> 对应节日
MAKEUP_WORKDAYS = {
    # 2021
    "2021-02-07": "春节", "2021-02-20": "春节",
    "2021-04-25": "劳动节", "2021-05-08": "劳动节",
    "2021-09-18": "中秋节",
    "2021-09-26": "国庆节", "2021-10-09": "国庆节",
    # 2022
    "2022-01-29": "春节", "2022-01-30": "春节",
    "2022-04-02": "清明节",
    "2022-04-24": "劳动节", "2022-05-07": "劳动节",
    "2022-10-08": "国庆节", "2022-10-09": "国庆节",
    # 2023
    "2023-01-28": "春节", "2023-01-29": "春节",
    "2023-04-23": "劳动节", "2023-05-06": "劳动节",
    "2023-06-25": "端午节",
    "2023-10-07": "中秋节、国庆节", "2023-10-08": "中秋节、国庆节",
    # 2024
    "2024-02-04": "春节", "2024-02-18": "春节",
    "2024-04-07": "清明节",
    "2024-04-28": "劳动节", "2024-05-11": "劳动节",
    "2024-09-14": "中秋节",
    "2024-09-29": "国庆节", "2024-10-12": "国庆节",
    # 2025
    "2025-01-26": "春节", "2025-02-08": "春节",
    "2025-04-27": "劳动节",
    "2025-09-28": "国庆节、中秋节", "2025-10-11": "国庆节、中秋节",
    # 2026
    "2026-01-04": "元旦",
    "2026-02-14": "春节", "2026-02-28": "春节",
    "2026-05-09": "劳动节",
    "2026-09-20": "国庆节", "2026-10-10": "国庆节",
}

COVERED_YEARS = (2021, 2026)  # 闭区间


# ---------------------------------------------------------------------------
# 核心查询 API（可被程序任意位置直接调用）
# ---------------------------------------------------------------------------

def _norm_date(val):
    """归一化各种日期表示为 (YYYY-MM-DD 字符串, datetime)，失败返回 (None, None)。"""
    if val is None or val == "":
        return None, None
    if hasattr(val, "year") and hasattr(val, "month") and hasattr(val, "day"):
        dt = datetime(val.year, val.month, val.day)
        return dt.strftime("%Y-%m-%d"), dt
    s = str(val).strip().replace("/", "-")
    s = s[:10].strip()
    for fmt in ("%Y-%m-%d", "%Y%m%d"):
        try:
            dt = datetime.strptime(s if fmt == "%Y-%m-%d" else s.replace("-", ""), fmt)
            return dt.strftime("%Y-%m-%d"), dt
        except ValueError:
            continue
    return None, None


def get_day_type(date_val):
    """返回 (day_type, holiday_name)。

    day_type ∈ {"法定节假日", "周末", "调休上班日", "工作日", None}
    - 法定节假日: holiday_name 为节日名称
    - 调休上班日: holiday_name 为对应节日名称（该日实际上班，不算节假日）
    - 周末/工作日: holiday_name 为 None
    超出 2021-2026 覆盖范围时按星期几退化判断（周末/工作日）。
    """
    date_str, dt = _norm_date(date_val)
    if dt is None:
        return None, None
    if date_str in STATUTORY_HOLIDAYS:
        return "法定节假日", STATUTORY_HOLIDAYS[date_str]
    if date_str in MAKEUP_WORKDAYS:
        return "调休上班日", MAKEUP_WORKDAYS[date_str]
    if not (COVERED_YEARS[0] <= dt.year <= COVERED_YEARS[1]):
        logger.warning("get_day_type: %s 超出节假日硬编码覆盖范围(2021-2026), 按星期退化判断", date_str)
    if dt.weekday() >= 5:
        return "周末", None
    return "工作日", None


def is_holiday(date_val) -> bool:
    """节假日 = 法定节假日 或 周末（调休上班日除外）。"""
    day_type, _ = get_day_type(date_val)
    return day_type in ("法定节假日", "周末")


# ---------------------------------------------------------------------------
# SDF 计算函数（注册到 FUNCTION_REGISTRY 供执行计划调用）
# ---------------------------------------------------------------------------

def mark_holidays(
    data: StandardDataFrame,
    date_column: str,
    output: str = "day_type",
    **kwargs,
) -> StandardDataFrame:
    """为每行日期标注节假日信息（2021-2026 法定节假日+周末写死查表）。

    新增四个维度列：
    - ``day_type``(=output): 法定节假日 / 周末 / 调休上班日 / 工作日
    - ``is_holiday``: 是 / 否（法定节假日或周末=是；调休上班日/工作日=否）
    - ``holiday_name``: 节日名称（元旦/春节/清明节/劳动节/端午节/中秋节/国庆节），非节日为 None
    - ``weekday``: 星期一 .. 星期日（0723 问题16 fix: 计划里常出现
      filter(column='weekday')，之前 mark_holidays 不产生该列 → 全部行被清空）
    """
    if not data.has_column(date_column):
        from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
        date_column = _fix_column_refs_in_sdf(data, date_column)

    zh_weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
    result_rows = []
    for row in data.rows:
        new_row = dict(row)
        day_type, name = get_day_type(row.get(date_column))
        new_row[output] = day_type
        new_row["is_holiday"] = "是" if day_type in ("法定节假日", "周末") else ("否" if day_type else None)
        new_row["holiday_name"] = name
        _, dt = _norm_date(row.get(date_column))
        new_row["weekday"] = zh_weekdays[dt.weekday()] if dt is not None else None
        result_rows.append(new_row)

    cols = [c.copy() for c in data.columns]
    for col_name, desc in (
        (output, f"日期类型(法定节假日/周末/调休上班日/工作日), 由{date_column}查2021-2026节假日表得到"),
        ("is_holiday", "是否节假日(法定节假日或周末=是)"),
        ("holiday_name", "法定节假日名称(非节假日为空)"),
        ("weekday", "星期几(星期一..星期日)"),
    ):
        if not any(c.name == col_name for c in cols):
            cols.append(ColumnDef(
                name=col_name, role="dimension", data_type="string",
                description=desc, is_computed=True,
            ))
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Added holiday columns from {date_column} (2021-2026 hardcoded calendar)"
    return StandardDataFrame(metadata=meta, columns=cols, rows=result_rows)


def filter_holidays(
    data: StandardDataFrame,
    date_column: str,
    keep: str = "holiday",
    **kwargs,
) -> StandardDataFrame:
    """按节假日属性过滤行。

    keep 取值：
    - "holiday":   保留节假日（法定节假日+周末，调休上班日除外）
    - "workday":   保留工作日（含调休上班日）
    - "statutory": 只保留法定节假日
    - "weekend":   只保留普通周末
    """
    if not data.has_column(date_column):
        from system_b.computation.function_dispatcher import _fix_column_refs_in_sdf
        date_column = _fix_column_refs_in_sdf(data, date_column)

    keep = (keep or "holiday").strip().lower()
    alias = {
        "节假日": "holiday", "holidays": "holiday",
        "工作日": "workday", "workdays": "workday", "非节假日": "workday",
        "法定节假日": "statutory", "statutory_holiday": "statutory",
        "周末": "weekend", "weekends": "weekend",
    }
    keep = alias.get(keep, keep)

    def _keep(day_type):
        if keep == "holiday":
            return day_type in ("法定节假日", "周末")
        if keep == "workday":
            return day_type in ("工作日", "调休上班日")
        if keep == "statutory":
            return day_type == "法定节假日"
        if keep == "weekend":
            return day_type == "周末"
        # 未知取值 → 默认节假日
        return day_type in ("法定节假日", "周末")

    result_rows = [
        dict(row) for row in data.rows
        if _keep(get_day_type(row.get(date_column))[0])
    ]
    meta = Metadata.from_dict(data.metadata.to_dict())
    meta.row_count = len(result_rows)
    meta.description = f"Filtered by holiday attribute keep={keep} on {date_column}"
    return StandardDataFrame(metadata=meta, columns=[c.copy() for c in data.columns], rows=result_rows)


def get_holidays(
    start: str,
    end: str,
    include_weekends: bool = True,
    **kwargs,
) -> StandardDataFrame:
    """直接查询区间 [start, end] 内的全部节假日清单（无需任何输入数据）。

    返回列: date / day_type(法定节假日|周末) / holiday_name / weekday(星期几)
    include_weekends=False 时只返回法定节假日。
    """
    _, d0 = _norm_date(start)
    _, d1 = _norm_date(end)
    if d0 is None or d1 is None:
        raise ValueError(f"get_holidays: 无法解析日期范围 start={start}, end={end}")
    if d0 > d1:
        d0, d1 = d1, d0

    zh_names = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
    rows = []
    cur = d0
    while cur <= d1:
        date_str = cur.strftime("%Y-%m-%d")
        day_type, name = get_day_type(date_str)
        if day_type == "法定节假日" or (include_weekends and day_type == "周末"):
            rows.append({
                "date": date_str,
                "day_type": day_type,
                "holiday_name": name,
                "weekday": zh_names[cur.weekday()],
            })
        cur += timedelta(days=1)

    cols = [
        ColumnDef(name="date", role="dimension", data_type="string",
                  description="节假日日期", is_computed=True),
        ColumnDef(name="day_type", role="dimension", data_type="string",
                  description="法定节假日/周末", is_computed=True),
        ColumnDef(name="holiday_name", role="dimension", data_type="string",
                  description="法定节假日名称(周末为空)", is_computed=True),
        ColumnDef(name="weekday", role="dimension", data_type="string",
                  description="星期几", is_computed=True),
    ]
    meta = Metadata(
        source_step="", source_operation="get_holidays",
        description=f"{start}~{end} 节假日清单(2021-2026硬编码日历)",
        row_count=len(rows),
    )
    return StandardDataFrame(metadata=meta, columns=cols, rows=rows)
