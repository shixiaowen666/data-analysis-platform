# -*- coding: utf-8 -*-
"""
gen_domain_knowledge.py —— 生成 domain_knowledge.json 底稿（确定性部分）

输入：  data/database_meta.json（唯一权威源：70 表 display_name/description、52 维度）
输出：  data/domain_knowledge_gen.json（骨架，business_topics/topic.tables 由规则聚类得出；
        typical_questions / topic.description 润色 / 维度别名增强留给 LLM 阶段，人工审核后合入）

用法：
  python scripts/gen_domain_knowledge.py            # 试运行：写 domain_knowledge_gen.json + 校验报告
  python scripts/gen_domain_knowledge.py --write    # 覆盖 data/domain_knowledge.json（自动备份旧文件）

生成字段：
  table_enhancement       70 表：display_name/description 直取 database_meta；business_topics 按 display_name 关键词规则聚类
  dim_display_name_map    52 维度：code → dimension_name 直取
  dim_synonyms            业务术语别名（手写规则，强化向量召回）
  topic_definitions       业务主题（9 个）：tables 由同一套关键词规则反推，保证与 business_topics 一致
  derived_dependency_rules  3~6 条手写关联规则（机构/光伏/线损/抄表/档案）
"""
import argparse
import json
import shutil
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent   # webapp/
DATA_DIR = BASE_DIR / "data"
DOMAIN_PATH = DATA_DIR / "domain_knowledge.json"
GEN_PATH = DATA_DIR / "domain_knowledge_gen.json"

EXPECTED_TABLES = 70
EXPECTED_DIMS = 52

# ------------------------------------------------------------------
# 业务主题聚类规则：(topic_name, display_name 命中关键词, metric_keywords)
# 顺序无关，多主题可同时命中（business_topics / topic.tables 均为多值）
# ------------------------------------------------------------------
TOPIC_RULES = [
    ("供电量", ["供电量", "网供电量", "转供电量", "供电负荷"],
     ["供电量", "网供电量", "转供电量", "最大负荷", "供电负荷"]),
    ("用电量", ["用电量", "用电户", "用户规模", "企业用电", "行业用电", "产业用电", "用电管理"],
     ["用电量", "负荷", "用户数", "用电", "业扩"]),
    ("线损管理", ["线损"],
     ["线损率", "线损", "负损", "高损", "合格率"]),
    ("光伏发电", ["光伏", "综合能源"],
     ["光伏", "发电", "上网", "用电户数", "可调", "可观"]),
    ("电力交易", ["电力交易", "交易"],
     ["电力交易", "交易"]),
    ("电能质量", ["电能质量"],
     ["电能质量", "电压告警", "谐波", "敏感", "安装情况"]),
    ("计量与档案", ["计量", "档案", "电表"],
     ["覆盖率", "档案", "电表", "资产", "用户档案", "计量"]),
    ("数据质量", ["完整率", "数据异常", "数据发布", "采集", "异常", "及时率"],
     ["完整率", "及时率", "异常", "发布", "采集"]),
    ("抄表与校时", ["抄表", "校时", "终端"],
     ["抄表率", "校时", "时钟合格率", "终端", "抄表"]),
]

# 主题描述模板（底稿占位，LLM 阶段润色）
TOPIC_DESC = {
    "供电量": "供电量相关查询：供电量、网供电量、转供电量、供电负荷、电量构成等。",
    "用电量": "用电量相关查询：各行业/用户/产业用电量、最大负荷、业扩、企业用电等。",
    "线损管理": "线损管理相关查询：分区分线分压线损率、负损、高损、合格率、线损电量等。",
    "光伏发电": "光伏发电（综合能源）相关查询：光伏电量、发电购电、可调可观数据、用户统计等。",
    "电力交易": "电力交易相关查询：交易总览、交易电量、交易模块汇总等。",
    "电能质量": "电能质量相关查询：电压告警、谐波异常、敏感客户分布、安装情况等。",
    "计量与档案": "计量运行与档案管理相关查询：终端/电表覆盖率、档案监测、资产规模等。",
    "数据质量": "数据质量相关查询：采集完整率、数据异常、发布指标、工单及时率等。",
    "抄表与校时": "抄表与校时相关查询：自动抄表率、电表/终端校时、时钟合格率等。",
}

# ------------------------------------------------------------------
# 维度业务别名（dimension_code → 中文别名列表；dimension_name 之外的常用说法）
# ------------------------------------------------------------------
DIM_SYNONYMS = {
    "data_time": ["日期", "时间", "统计日期"],
    "data_time_str": ["时间戳", "日期"],
    "stat_day": ["日期", "统计日期"],
    "datatime": ["日期", "时间"],
    "stat_time": ["日期", "统计日期"],
    "stat_date": ["日期", "统计日期"],
    "creat_time_prev": ["日期", "上一周期"],
    "month": ["月份", "统计月份"],
    "quarter": ["统计季度", "季度"],
    "weekofyear": ["周", "年周"],
    "year": ["年份", "统计年份"],
    "hour": ["小时", "时刻", "时点"],
    "hours": ["小时", "时刻", "时点"],
    "org_name": ["供电局", "局", "地市供电局", "供电单位"],
    "supply_org_name": ["供电局", "局", "供电单位"],
    "org_short_name": ["供电局", "局", "供电单位"],
    "supply_org_no": ["供电单位编码", "机构编号", "org"],
    "region_code": ["供电单位编码", "机构编号", "区编码"],
    "regioncode": ["供电单位编码", "机构编号", "区编码"],
    "org_no": ["供电单位编码", "机构编号", "org"],
    "city_org_short_name": ["市局", "市公司", "深圳局"],
    "dis_org_short_name": ["区局", "区公司", "分局"],
    "ele_user_category": ["用户类型", "客户类型", "用电类别"],
    "usertypename": ["用户类型", "客户类型", "用户类别"],
    "trade_sort_code": ["行业", "行业类别", "行业编码"],
    "trade_sort_name": ["行业分类", "国民经济行业", "产业"],
    "code_name_list": ["行业", "所属行业", "行业类别"],
    "busi_type_name": ["业务类型", "业扩业务", "业扩类型"],
    "electricitytype": ["电压等级", "线路电压", "线路类型"],
    "sort_name": ["四可数据", "数据类型"],
    "type": ["光伏负荷类型", "发电类型", "负荷类型"],
    "sort_comp_name": ["用户行业分布", "行业分布"],
    "volt_level": ["电压等级", "分压", "线损电压等级"],
    "volt_name": ["电压等级", "分压", "线损电压等级"],
    "data_type": ["数据类型", "数据项"],
    "stat_type": ["统计口径", "统计类型"],
    "mainten_team_name": ["班组", "运维班组"],
    "org_unit_no": ["部门", "运维部门"],
    "order_type": ["客户类别", "用户类别"],
    "abnor_code": ["异常类型", "工单类型", "异常工单"],
    "file_type": ["档案类型", "档案类别"],
    "main_term_flag": ["终端主备", "主备终端"],
    "pt_stat_type": ["现货类型", "现货统计"],
    "ma_auxil_table_signs": ["主副表", "表计主副"],
    "equ_sort_name": ["终端类别", "终端类型"],
    "equ_type": ["电表类型", "表计类型", "电能表类型"],
    "公司名称": ["企业名称", "公司", "用电企业"],
    "elec_cust_name": ["公司", "客户名称", "用户名称", "用电户"],
    "ll_obj_subtype_name": ["线损率类型", "线损对象"],
    "pt_stat_type_code": ["发布指标统计类型", "数据发布类型"],
}

# ------------------------------------------------------------------
# 派生指标关联规则（keyword 命中 description/display_name 时关联实体）
# 注意 keyword 为子串匹配；ref_key 未命中实体会被消费端静默跳过，故可宽松。
# ------------------------------------------------------------------
DERIVED_DEPENDENCY_RULES = [
    {
        "keyword": "计量自动化",
        "deps": [
            {"entity_type": "table", "ref_key": ["view_mk_mc_metering_coverage"]},
            {"entity_type": "dimension_code", "ref_key": ["usertypename"]},
            {"entity_type": "dimension_code", "ref_key": ["main_term_flag"]},
            {"entity_type": "dimension_code", "ref_key": ["ma_auxil_table_signs"]},
        ],
    },
    {
        "keyword": "光伏",
        "deps": [
            {"entity_type": "table", "ref_key": ["view_pv_online_power"]},
            {"entity_type": "dimension_code", "ref_key": ["type"]},
            {"entity_type": "dimension_code", "ref_key": ["max_load_time"]},
        ],
    },
    {
        "keyword": "线损",
        "deps": [
            {"entity_type": "dimension_code", "ref_key": ["volt_level", "volt_name"]},
            {"entity_type": "dimension_code", "ref_key": ["ll_obj_subtype_name"]},
        ],
    },
    {
        "keyword": "抄表",
        "deps": [
            {"entity_type": "table", "ref_key": ["view_electricity_auto_copy_rate_home"]},
            {"entity_type": "dimension_code", "ref_key": ["usertypename"]},
        ],
    },
    {
        "keyword": "档案",
        "deps": [
            {"entity_type": "table", "ref_key": ["view_mk_mc_file_sum_stat"]},
            {"entity_type": "dimension_code", "ref_key": ["file_type"]},
        ],
    },
    {
        "keyword": "深圳供电局",
        "deps": [
            {"entity_type": "dimension_code", "ref_key": ["city_org_short_name"]},
            {"entity_type": "dimension_code", "ref_key": ["org_name", "supply_org_name", "org_short_name"]},
        ],
    },
]


def _clamp(s: str, n: int) -> str:
    return s if len(s) <= n else s[: n - 1] + "…"


def build_table_enhancement(db_meta) -> dict:
    """table_enhancement：display_name/description 直取 + business_topics 规则聚类"""
    enh = {}
    unmatched = []
    for t in db_meta["table_summaries"]:
        tn = t["table_name"]
        display_name = (t.get("display_name") or "").strip()
        description = (t.get("description") or "").strip()
        if not display_name:
            raise ValueError(f"表 {tn} 缺少 display_name，无法生成底稿")
        if len(display_name) > 100:
            raise ValueError(f"表 {tn} display_name 超长（{len(display_name)}/100）")
        if not description:
            raise ValueError(f"表 {tn} 缺少 description，无法生成底稿")

        topics = sorted({tn_name for tn_name, kws, _ in TOPIC_RULES if any(k in display_name for k in kws)})
        if not topics:
            unmatched.append(tn)
        enh[tn] = {
            "display_name": display_name,
            "description": description,
            "business_topics": topics,
            "typical_questions": [],  # LLM 阶段填充
        }
    return enh, unmatched


def build_dim_maps(db_meta) -> dict:
    """dim_display_name_map 直取 + dim_synonyms 手写规则"""
    name_map = {}
    missing_name = []
    for d in db_meta["available_dimensions"]:
        code = d["dimension_code"]
        dname = (d.get("dimension_name") or "").strip()
        if not dname:
            missing_name.append(code)
        name_map[code] = dname or code
    synonyms = {code: DIM_SYNONYMS.get(code, []) for code in name_map}
    return {"dim_display_name_map": name_map, "dim_synonyms": synonyms}, missing_name


def build_topic_definitions(enh: dict) -> list:
    """topic_definitions：tables 由 table_enhancement.business_topics 反推，保证两者一致"""
    topics = []
    for tn_name, kws, metric_keywords in TOPIC_RULES:
        tables = sorted(tn for tn, e in enh.items() if tn_name in e["business_topics"])
        topics.append({
            "topic_name": tn_name,
            "description": TOPIC_DESC[tn_name],
            "typical_queries": [],  # LLM 阶段填充
            "tables": tables,
            "metric_keywords": metric_keywords,
        })
    return topics


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="覆盖 data/domain_knowledge.json（自动备份）")
    args = ap.parse_args()

    src = json.loads(DATA_DIR.joinpath("database_meta.json").read_text(encoding="utf-8"))
    db_meta = src["database_meta"]

    # ---------- 构建骨架 ----------
    enh, unmatched_tables = build_table_enhancement(db_meta)
    dim_result, missing_dims = build_dim_maps(db_meta)
    topic_defs = build_topic_definitions(enh)

    domain = {
        "table_enhancement": enh,
        **dim_result,
        "topic_definitions": topic_defs,
        "derived_dependency_rules": DERIVED_DEPENDENCY_RULES,
    }

    # ---------- 校验报告 ----------
    n_tables = len(enh)
    n_dims = len(dim_result["dim_display_name_map"])
    topic_hit = {td["topic_name"]: len(td["tables"]) for td in topic_defs}
    print("=" * 56)
    print(f"表数量: {n_tables}/{EXPECTED_TABLES}   维度数量: {n_dims}/{EXPECTED_DIMS}")
    print(f"未命中任何主题的表: {len(unmatched_tables)}")
    for tn in unmatched_tables:
        print("  !", tn)
    print(f"缺 dimension_name 的维度: {len(missing_dims)}")
    for c in missing_dims:
        print("  !", c)
    print("-" * 56)
    for name, cnt in topic_hit.items():
        flag = "" if cnt else "  <-- 空主题！"
        print(f"  {name}: {cnt} 张表{flag}")
    print("=" * 56)
    if unmatched_tables or missing_dims or any(c == 0 for c in topic_hit.values()):
        print("存在异常，请先修正后重跑（不写盘）。")
        if args.write:
            print("已忽略 --write。")
        return
    if n_tables != EXPECTED_TABLES:
        print(f"表数量 {n_tables} != 期望 {EXPECTED_TABLES}，请核对 database_meta.json 后重跑。")
        return

    out = json.dumps(domain, ensure_ascii=False, indent=2)
    if args.write:
        if DOMAIN_PATH.exists():
            bak = DOMAIN_PATH.with_suffix(".bak.json")
            shutil.copy2(DOMAIN_PATH, bak)
            print(f"已备份旧 domain_knowledge.json → {bak.name}")
        DOMAIN_PATH.write_text(out, encoding="utf-8")
        print(f"已写入 {DOMAIN_PATH.name}（{len(out)} 字节）")
    else:
        GEN_PATH.write_text(out, encoding="utf-8")
        print(f"试运行：已写入 {GEN_PATH.name}，请人工审核后 --write 覆盖。")


if __name__ == "__main__":
    main()
