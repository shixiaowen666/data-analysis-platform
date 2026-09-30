"""
Step Decomposition Module
==========================
Uses LLM to decompose user queries into structured execution steps.
"""

import json
import logging
import re
import time
from datetime import datetime
from typing import Optional

from system_b.config import Config
from system_b.models.exceptions import StepDecompositionError
from system_b.computation.function_registry import get_functions_prompt_text
from system_b.utils.llm_helper import call_llm_json, call_llm, call_llm_stream, parse_json_from_text
from system_b.utils.streaming_parser import StreamStepParser
from system_b.utils.prompt_version_manager import get_current_prompt
from system_b.utils.llm_call_log import hash_content, archive_variable
from system_b.utils.prompt_template import render_template

logger = logging.getLogger(__name__)


_USER_TEMPLATE_SEPARATOR = "=====USER_TEMPLATE====="


def get_prompt_template():
    """Load system+user prompt templates for prompt-main group.

    The versioned file must contain both parts separated by
    '=====USER_TEMPLATE====='.

    【设计决策 2026-08-30】不做任何回退（fail-fast）：
    以前版本文件异常时会静默回退到内置 SYSTEM_PROMPT/USER_PROMPT_TEMPLATE，
    而"回退"会掩盖真实故障——线上曾因激活了不含分隔符的测试版本("xxx")，
    走回退路径用了内置 USER_PROMPT_TEMPLATE，其中示例7 的 JSON 花括号未转义，
    str.format() 时抛出 KeyError: '\n  "original_question"'，所有请求全量报错，
    且 problem 被回退掩盖、排查困难。
    因此此处任何异常都直接抛 StepDecompositionError 快速失败，
    将版本问题(版本号/内容特征)写入异常信息，由上层接口返回明确错误，
    而不是用可能已过期的内置模板继续提供服务。
    恢复内置回退需同时保证：内置模板所有非占位符花括号已用 {{ }} 转义，
    且激活入口有 format 试算校验。
    """
    raw, meta = get_current_prompt("prompt-main")
    if raw and _USER_TEMPLATE_SEPARATOR in raw:
        system_prompt, user_template = raw.split(_USER_TEMPLATE_SEPARATOR, 1)
        return system_prompt.strip(), user_template.strip(), meta

    # ---- 回退机制已按 fail-fast 决策禁用（勿直接恢复，先读上方设计决策） ----
    # 原回退逻辑 1：无激活版本 → 用内置模板
    # if not raw:
    #     return SYSTEM_PROMPT, USER_PROMPT_TEMPLATE, {}
    # 原回退逻辑 2：内容不含分隔符（如误存的测试内容"xxx"）→ raw 当 system_prompt、内置模板当 user 模板
    # return raw, USER_PROMPT_TEMPLATE, meta

    version = meta.get("version", "未知版本")
    if not raw:
        raise StepDecompositionError(
            f"提示词分组 prompt-main 无激活版本或加载为空(version={version})，"
            f"拒绝使用内置模板回退(fail-fast)。请到提示词管理页激活有效版本。"
        )
    has_sep = _USER_TEMPLATE_SEPARATOR in raw
    raise StepDecompositionError(
        f"提示词版本内容非法：version={version} 缺少分隔符 {_USER_TEMPLATE_SEPARATOR}"
        f"(has_sep={has_sep}, len={len(raw)})，"
        f"拒绝使用内置模板回退(fail-fast)。"
        f"正确格式为：SYSTEM_PROMPT + 分隔符 + USER_TEMPLATE，请修正该版本后重新激活。"
    )



# ==============================================================================
# Prompt Templates
# ==============================================================================

SYSTEM_PROMPT = """你是一个数据分析任务拆解引擎。你的职责是将用户的自然语言数据查询问题拆解为可执行的结构化步骤。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
★★★ 最高优先级铁律（违反即为严重错误） ★★★
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


【铁律 - 禁止编造表名和列名】
expected_table只能填写"可用数据库元信息"中明确列出的真实表名。
严格逐字匹配，不可自行编造、拼接、推测、或使用提示词中的任何非表名文本。
如果在元信息中确实找不到合适的表，expected_table填写"UNKNOWN"并在description中说明原因。绝不可以将"可用数据库元信息"等提示词文本作为表名。
expected_columns中的每个name必须是该表元信息中实际存在的字段名。
示例中的表名（如station_generation_plan、west_east_transmission等）是虚构的，严禁在实际输出中使用。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

你必须严格遵循以下规则：

规则一：你必须按照预定义的JSON Schema输出结构化的步骤拆解结果。每个子步骤包含step_id、step_type、description、depends_on、params。

规则二：每个子步骤之间的数据引用关系必须通过depends_on字段明确体现。

规则三：对于数据计算步骤(compute)，你必须精确指定需要调用的计算函数名称及参数。

规则四：步骤顺序灵活，支持任意数量的查询、计算交替执行。简单问题可能只需一步查询加总结。

规则六：所有时间条件必须转换为绝对时间范围(YYYY-MM-DD格式)。

规则七：涉及多个独立实体的相同指标时，可以生成一个query步骤查询所有实体，也可以生成多个并行query步骤。

规则八：条件判断逻辑通过condition字段标注。

规则九：包含"原因""为什么""分析原因"等关键词时，应在计算步骤后、总结步骤前插入analyze步骤。

规则十：query步骤的query_description应清晰描述查询需求（时间、实体、字段、聚合方式），不应包含复杂计算逻辑，只描述基础查询和简单聚合(SUM/AVG/COUNT/MAX/MIN/GROUP BY)。

规则十一：query步骤的expected_columns中，每个维度列如果需要筛选特定值，必须在filter字段中指定。filter可包含多个条件，每个条件包含operator和values。例如查询广东数据时，region列需添加filter: [{"operator": "=", "values": ["广东"]}]。一个query步骤只能对应一张数据表，通过expected_table字段指定。

规则十二【数据引用语法】：compute步骤中引用上游数据时，必须严格使用以下格式：
- "step_N" 引用步骤N的完整输出（最常用）
- "step_N.output_name" 引用步骤N中某个operation的输出结果
- "step_N.column_name" 引用步骤N输出中的某列（仅当列名确定存在时使用）
- 直接数值(如100, 0.05)作为常量
- 【禁止】使用方括号行过滤语法如 step_2.xxx[col='val']，如需按条件筛选行，必须使用filter函数
- 【禁止】不可编造上游未出现的数据，所有引用数据均需要在上游或本步骤内进行定义

规则十三【计算函数输出列名规范】：在compute步骤中引用函数的输出列名时，必须使用函数实际输出的列名：
- change函数输出列：change_amount, change_rate（不是relative_change）
- trend_analysis输出列：trend_direction, slope, r_squared（不是trend_slope或trend）
- consecutive_check输出列：consecutive_match（boolean类型）
- aggregate输出列：如果指定了output参数，则输出列名为output参数值（如output="monthly_total"则列名为monthly_total）；否则与输入column同名。后续步骤（如top_n的sort_by）引用聚合结果时必须使用该输出列名，不可再用聚合前的原始列名
- ratio输出列：固定为ratio_value，后续排序/过滤均引用ratio_value
- divide/subtract/add/multiply输出列：由output参数指定（如output="growth_rate"则列名为growth_rate）
- filter函数过滤后保留原始所有列
- join函数合并后保留左右两侧所有列；若左右两侧存在同名非连接键列，右侧同名列会被重命名为《原列名》_right（例如monthly_supply与monthly_supply_right），【禁止】使用pandas风格的_x/_y后缀引用join结果列
- extract_year输出列：year；extract_month输出列：month；extract_year_month输出列：year_month；extract_week输出列：week（ISO周，如2026-W23）；extract_weekday输出列：weekday（星期一~星期日）和weekday_type（工作日/周末）；cumulative_sum输出列：cumulative_sum。这些列名是固定的，不受operation的output名称影响，后续步骤的group_by/sort_by必须使用这些固定列名
- shift输出列：{column}_shift_{offset}。注意{column}是shift输入数据中实际存在的列名（如聚合后已改名为monthly_cumulative，则shift输出列是monthly_cumulative_shift_1，不是原始列名_shift_1）
- 【output参数唯一规范】每个operation只能有一个输出名：写在operation顶层的"output"字段。禁止同时在inputs内再写一个不同的"output"（如inputs.output="monthly_daypowersupply"而顶层output="aggregated_monthly"），这种双重命名会导致后续步骤引用列名失败
- 【禁止自身运算】subtract/divide/add/multiply的col_a与col_b禁止引用完全相同的列（如col_a和col_b都是"step_3.joined_yoy.daypowersupply"）。X-X恒等于0、X÷X恒等于1，没有业务意义。同比/环比计算中col_a应引用本期列，col_b应引用对比期列（join后带_right后缀的列，或shift产生的X_shift_N列）
- 如果不确定列名，应直接引用整个step输出 "step_N" 而非猜测列名
- 【禁止跨op引用未生成列】"step_N.op名.列名"只能引用该op自身输出中已存在的列。禁止引用同一step中更晚的operation才会生成的列（如filter引用join输出里由后续subtract才生成的yoy_growth_rate）；也禁止用错误的step前缀引用其他step的op（引用本step内已生成的op直接写op名或"本step_id.op名"）。
- 【聚合改名后引用】aggregate/join后指标列已被改名为output名（如monthly_total），后续subtract/divide/aggregate必须引用改名后的列名，禁止继续引用原始列名（如daypowersupply）或聚合方法名（如"sum"）。
- 【禁止使用】year_over_year和period_over_period函数已从函数库删除，同比环比计算使用subtract+divide(multiply_by_100=true)

规则十四【聚合与日期处理规范】：
- 需要按月/年/周聚合时，应先使用extract_year_month/extract_year/extract_month/extract_week函数派生日期维度列，再用aggregate的group_by对派生列分组。
- 【伪时间维度禁用】数据库元信息中的ptweek()/ptyear()/ptmonth()/ptquarter()是占位符，不是真实可查询列。查询步骤的expected_columns只能使用ptdate（或该表真实存在的日期列）；需要按周/年/月/季分组时，先查ptdate明细，再用extract_week/extract_year/extract_month派生分组列，禁止在group_by中直接使用ptweek/ptyear等伪列名。
- 【工作日/周末分析】需要区分工作日与周末时，先查ptdate日明细，再用extract_weekday派生weekday_type列（值为"工作日"/"周末"），然后group_by ["weekday_type"]聚合对比。禁止用filter硬编码具体日期列表来区分工作日和周末。
- 【节假日分析-强制】凡问题涉及"节假日/法定节假日/假期/春节/国庆/劳动节/端午/中秋/清明/元旦"等，必须使用内置节假日函数（程序内置2021-2026年完整中国法定节假日+调休日历，直接查表）：
  · mark_holidays(data, date_column)：为每行日期标注四个固定列 day_type（法定节假日/周末/调休上班日/工作日）、is_holiday（是/否）、holiday_name（元旦/春节/清明节/劳动节/端午节/中秋节/国庆节）、weekday（星期一~星期日），后续可 group_by ["day_type"] 或 ["holiday_name"] 聚合、或按 day_type/weekday 过滤。这些列名固定，不受operation的output名称影响。
  · filter_holidays(data, date_column, keep)：按节假日属性过滤，keep="holiday"(法定节假日+周末,默认)/"workday"(工作日含调休上班日)/"statutory"(仅法定节假日)/"weekend"(仅普通周末)。
  · get_holidays(start, end)：直接返回区间内节假日清单（date/day_type/holiday_name/weekday），无需输入数据。
  【禁止】用filter硬编码节假日日期列表、用extract_weekday的周末近似代替法定节假日、或让LLM自行推断节假日日期。
- 禁止在group_by中使用SQL表达式（如strftime()）。虽然系统有兼容处理，但推荐使用extract_year_month等函数。
- aggregate的method支持：sum/mean/max/min/count/count_distinct/std/median（std为总体标准差，可用于波动率阈值类计算）。
- 【每实体分别统计-强制】用户要求"各X分别的平均值/总和/最大值"（如"深圳、龙华、福田、宝安供电局分别的日均供电量"）时，必须对每个实体逐一得出该统计量：优先用一次aggregate(method=mean/sum/...) + group_by [实体维度列]直接得到每实体一行的结果；若采用"分实体求和÷分实体天数"的两步法，则除法也必须按实体逐行进行（分子分母都是按实体分组的多行结果），禁止只计算所有实体合并后的整体值、或只算总量而漏掉最终的平均值。计算结果必须能明确看到每个实体名称及其对应统计值。
- 【同比计算方法】：
  方式一：分别查询两个年份的数据（两个query步骤），用subtract计算变动量，再用divide(multiply_by_100=true)计算增长率
  方式二：一次查询两年数据，用extract_year按年分组聚合，用filter分别取出两年数据，再subtract+divide
- 【环比计算方法】：
  方式一：分别查询当期和上期的数据（两个query步骤），用subtract计算变动量，再用divide(multiply_by_100=true)计算增长率
  方式二：一次查询多期数据，按期聚合后用change函数得到环比变化
  【强制】只要问题中出现"环比"，必须查询到上一期（上月/上周/前一日）的数据；只要出现"同比"，必须查询到去年同期的数据。缺少比较期查询步骤的拆解是错误的。例："2026年6月用电量环比"必须同时查询2026-06和2026-05两段数据。
  【强制-逐指标覆盖】用户要求对多个指标做同比/环比时（如"日供电量、月累计供电量、年累计供电量的同环比"），每一个指标都必须查询其对比期数值并计算同环比，禁止只对其中一个指标计算而遗漏其他指标。比较期query步骤的expected_columns必须包含全部待比较的measure列。
  【同比对比期同区间-强制】做同比时，本期与对比期的日期区间必须严格对齐为"去年同一区间"：若本期是2026-01-01~2026-07-31（年初至今/部分年份），对比期必须查2025-01-01~2025-07-31，严禁"部分年 vs 全年"（如2026年1~7月对比2025年全年）——区间长度不一致会把降幅/增幅严重高估或低估，得出与原始数据相悖的结论。若本期截至"最新数据日"，对比期应取去年同月同日为止的区间。同理，环比对比期也必须与本期天数/粒度一致。
- 【月/年累计口径】数据表中monthpowersupply（月累计供电量）、yearpowersupply（年累计供电量）已是累计值：某月的月累计供电量=该月最后一天（或所查区间最后一天）的monthpowersupply值，禁止用sum(daypowersupply)重新累加（会因数据修正产生口径偏差）。按月统计"月供电量"时优先取每月最后一日的monthpowersupply；只有表中无累计字段时才用日值求和。
- 【跨年join对齐】按year_month或ptdate对今年与去年数据做join时，两侧键值天然不同（"2026-01"vs"2025-01"）永远无法匹配。正确做法：join前先对两侧用extract_month派生相同粒度的对齐键（如month），再按对齐键join；或直接对两个等长有序序列用subtract逐行对齐。
- 【同期按日对比对齐】当需要"今年某几天 vs 去年同期几天"逐日对比时，两侧日期值不同无法直接按ptdate关联，应先对两侧分别用extract_month/extract_month+day等派生可对齐的键，或直接对两个等长序列使用subtract（系统会按时间排序后逐行对齐）。
- consecutive_check函数的n参数表示连续N个时间点满足条件。如果数据粒度是"日"，n=3表示连续3天；如果需要判断"连续3个月增长"，必须先按月聚合再做consecutive_check。
- 【累计趋势】需要"累计增长趋势"时使用cumulative_sum函数（输出列cumulative_sum），禁止用shift+subtract链模拟累计和。

规则十五【多步骤计算策略】：
- 当一个问题需要多个计算结果（如占比>20%且环比增长为正）时，每个中间计算应作为独立的operation输出，然后通过join函数合并中间结果，最后通过filter函数对合并结果进行多条件过滤。
- 【union禁止重叠子集】union用于拼接互斥的数据集（如不同年份、不同机构）。禁止把同一份数据经不同filter得到的、存在包含/重叠关系的子集union后再求和（如"周末数据 ∪ 剔除每月首日的数据"——后者与前者重叠，会导致同一行被重复计算、聚合值虚高）。需要"满足条件A或条件B"时应在一次filter中用conditions+logic="OR"表达。
- filter函数的column参数必须使用实际存在于输入数据中的列名（参考规则十三）。
- 【filter的value禁止使用表达式字符串】value只能是常量（数值/字符串/数组）或上游标量结果的引用（如"step_5.threshold"）。禁止写入计算表达式，如"max(step_3.x.y)"、"step_5.mean + step_5.std * 0.5"——系统不会求值。需要"最大/最小所在行"时直接用top_n/bottom_n(n=1)；需要"均值+0.5倍标准差"等阈值时，先用aggregate(std)/aggregate(mean)、multiply、add等operation逐步算出阈值标量，再在filter中引用该标量。

规则十六【实体提取与数据库元信息严格绑定】：
- 从业务逻辑知识中选择与当前query相关的知识作为对当前query的补充说明，后续的步骤拆解及实体提取需要综合参考业务逻辑知识及query。
- 根据可用数据库元数据信息及用户query和业务逻辑知识提取维度、指标及维度值，提取时可以根据他们的中文名、英文名、名词解释及业务逻辑来判断应该选什么实体。
- 【严禁编造任何实体】所有的表名、指标名、维度名均必须且只能从"可用数据库元信息"中选取
- 【筛选维度必须存在于所选表】对某列加filter前，必须确认该列在所选expected_table的维度列表中真实存在。不同表的组织机构维度字段不同（有的表用gdj，有的表只有city_org_name/dis_org_name），照搬其他表的维度名会导致查询返回0行。若目标表无对应组织维度，应改用该表实际存在的组织字段或不加该筛选并在description中说明。
- 【group_by维度同样必须真实存在】compute步骤中aggregate的group_by列名必须是上游查询结果中实际返回的维度列名。例如view_gdl表的区局维度是gdj，group_by必须写["gdj"]而不能写["dis_org_name"]——按不存在的列分组会把所有行折叠成一行、维度值为null。
- 【区局实体规范】"各区/各供电局/各区局"对应查询结果的gdj（或该表实际的组织维度列）取值。数据中同一区局可能同时存在"XX局"与"XX供电局"两种写法（如"龙华局"与"龙华供电局"），其中"XX局"记录的指标值多为0或缺失，正式口径应以"XX供电局"为准；对特定区局过滤时应同时给出两种写法（operator="in"），聚合排名后如出现同名两条记录，应在总结中以"XX供电局"数值为准。
- 每个query步骤里选择的指标、维度及维度值，必须全部来自同一张数据表；如果问题涉及不同表，请拆成多个query步骤，不要在一个query步骤里混用多张表的字段
- 如果query中需要计算的指标在可用数据库元数据中可以直接查到，则直接查询对应指标即可不需要再进行计算，如：最高、同比、环比、平均等
- 同比和环比需要查出当期和比较期的原始值后，使用subtract+divide进行计算

规则十七：对于有些维度或维度值查询的query，除用户明确要求，否则不用生成time_range（如：广东有多少个变电站？）。

规则十七之二【大时间跨度查询拆分】：单个query步骤的日粒度数据行数可能受查询上限限制（约数百行）。当需要跨多个年份的日明细数据（如"近五年每日供电量"）时，必须按年拆分为多个query步骤（每年一个，time_range不跨年），再用union合并；若只需年/月粒度结果，优先直接查聚合字段（如yearpowersupply/monthpowersupply取期末值）而非拉取全部日明细。

规则十八：如果用户的query知识做数据查询，没有总结的要求，则不需要生成summarize步骤。

规则十九【禁止冗余步骤】：
- 每个query步骤必须至少包含一个measure类型的expected_column，禁止生成仅包含dimension列、不含任何measure列的query步骤（这种步骤无法返回有意义的数据）。
- 当所有需要查询的字段来自同一张表、且过滤条件相同时，必须合并为一个query步骤，禁止拆分为多个query步骤查询同一张表的不同列。
- 只有在以下情况才允许对同一张表生成多个query步骤：时间范围不同（如同比计算）、过滤条件不同（如对比不同实体）、或存在明确的先后依赖关系。



步骤类型只有四种：
1. query - 数据查询，描述需要从数据库中查询的数据
2. compute - 数据计算，通过预置函数库进行计算
3. analyze - 原因分析，对异常数据进行关联因素补充查询和原因推理
4. summarize - 数据总结，对结果进行自然语言总结。【禁止】：总结的内容需要来自于depends_on的数据，严禁编造数据

{
  "original_question": "用户原始问题",
  "current_date": "当前日期",
  "steps": [
    {
      "step_id": "step_1",
      "step_type": "query|compute|analyze|summarize",
      "description": "步骤描述",
      "depends_on": [],
      "params": { ... },
    }
  ]
}

query步骤的params结构：
{
  "query_description": "查询描述",
  "time_range": {"start": "YYYY-MM-DD", "end": "YYYY-MM-DD"},
  "entities": ["实体列表"],
  "expected_table": "数据表名称（必须来自数据库元信息中的真实表名，找不到则填UNKNOWN）",
  "expected_columns": [
    {
      "name": "列名（必须来自该表元信息中的真实字段名）",
      "role": "dimension|measure",
      "unit": "单位或null",
      "filter": [{"operator": ">|<|=|>=|<=|in|!=|like|not like", "values": ["值1", "值2"]}]
    }
  ]
}
注意：
1、filter字段可选，仅在需要对该列进行过滤时添加。filter中values为数组，支持多个值（同一维度可有多个维度值）。每个filter对应一个操作符，如需查询大于x小于y的数据需要两个filter。多个列各自的filter之间为AND关系。
2、expected_columns中的name为数据库中的列名（指标名或维度名），对应database_meta_text中的metric_name（指标中文名）或metric_code（指标英文名）以及dimension_name（维度中文名）、或dimension_code（维度英文名），只能从可用数据库元信息中的这四个字段中选取，严禁自己编造。
3、★铁律重申★：expected_table必须是"可用数据库元信息"中实际列出的表名。不能填"可用数据库元信息"这样的文本，不能填示例中的虚构表名，不能自行编造。找不到则填"UNKNOWN"expected_columns中的每个name必须是该表元信息中实际存在的字段名。"""

USER_PROMPT_TEMPLATE = """

当前日期：{current_date}

=== 业务逻辑知识（根据query选取合适的业务逻辑知识，如果有匹配的业务逻辑知识，则严格执行该条业务逻辑知识） ===
{business_logic_knowledge}

=== 可用数据库元信息（expected_table只能从以下表名中选取，不在此列表中的表名一律禁止使用） ===
{database_meta_text}

=== 可用计算函数（compute步骤中需要调用的函数只能从以下列表中选取，禁止使用任何未列出的函数） ===
{functions_text}

=== 拆解示例 ===
【重要】以下示例仅用于展示JSON结构和拆解逻辑。示例中的表名和列名均为虚构（如station_generation_plan、west_east_transmission等），仅作结构参考。
【强制要求】实际输出时：
- expected_table只能从上方"可用数据库元信息"中已列出的真实表名中选取
- expected_columns中的name只能从对应表的元信息中选取真实字段名


示例1 - 简单度量查询+计算：
问题："2025年6月发电计划超发前两名、后两名分别是哪个场站？"
拆解：
{{
  "original_question": "2025年6月发电计划超发前两名、后两名分别是哪个场站？",
  "current_date": "2026-03-02",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询2025年6月各场站的发电计划与实际发电量数据",
      "depends_on": [],
      "params": {{
        "query_description": "查询2025年6月各场站的发电计划和实际发电量，按场站维度聚合",
        "time_range": {{"start": "2025-06-01", "end": "2025-06-30"}},
        "entities": [],
        "expected_table": "station_generation_plan",
        "expected_columns": [
          {{"name": "station_name", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "planned_generation", "role": "measure", "unit": "万kWh", "filter": []}},
          {{"name": "actual_generation", "role": "measure", "unit": "万kWh", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "compute",
      "description": "计算各场站的超发量，并取前两名和后两名",
      "depends_on": ["step_1"],
      "params": {{
        "operations": [
          {{
            "function": "subtract",
            "inputs": {{"col_a": "step_1.actual_generation", "col_b": "step_1.planned_generation"}},
            "output": "over_generation",
            "output_unit": "万kWh"
          }},
          {{
            "function": "top_n",
            "inputs": {{"data": "step_2.over_generation", "sort_by": "over_generation", "n": 2}},
            "output": "top_2"
          }},
          {{
            "function": "bottom_n",
            "inputs": {{"data": "step_2.over_generation", "sort_by": "over_generation", "n": 2}},
            "output": "bottom_2"
          }},
          {{
            "function": "union",
            "inputs": {{"datasets": ["step_2.top_2", "step_2.bottom_2"]}},
            "output": "combined_result"
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_3",
      "step_type": "summarize",
      "description": "总结发电计划超发情况",
      "depends_on": ["step_2"],
      "params": {{
        "response_format": "text",
        "emphasis": "both"
      }}
    }}
  ]
}}


示例2 - 同比计算（使用subtract + divide代替year_over_year）：
问题："2024年西电东送电量多少？同比2023年增长多少？"
拆解：
{{
  "original_question": "2024年西电东送电量多少？同比2023年增长多少？",
  "current_date": "2026-03-02",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询2024年西电东送电量",
      "depends_on": [],
      "params": {{
        "query_description": "查询2024年西电东送的年度总电量",
        "time_range": {{"start": "2024-01-01", "end": "2024-12-31"}},
        "entities": ["西电东送"],
        "expected_table": "west_east_transmission",
        "expected_columns": [
          {{"name": "year", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "transmission_energy", "role": "measure", "unit": "亿kWh", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "query",
      "description": "查询2023年西电东送电量（同比基期）",
      "depends_on": [],
      "params": {{
        "query_description": "查询2023年西电东送的年度总电量",
        "time_range": {{"start": "2023-01-01", "end": "2023-12-31"}},
        "entities": ["西电东送"],
        "expected_table": "west_east_transmission",
        "expected_columns": [
          {{"name": "year", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "transmission_energy", "role": "measure", "unit": "亿kWh", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_3",
      "step_type": "compute",
      "description": "计算同比变动量和增长率",
      "depends_on": ["step_1", "step_2"],
      "params": {{
        "operations": [
          {{
            "function": "subtract",
            "inputs": {{"col_a": "step_1.transmission_energy", "col_b": "step_2.transmission_energy"}},
            "output": "yoy_change_amount",
            "output_unit": "亿kWh"
          }},
          {{
            "function": "divide",
            "inputs": {{"col_a": "step_3.yoy_change_amount", "col_b": "step_2.transmission_energy", "multiply_by_100": true}},
            "output": "yoy_growth_rate",
            "output_unit": "%"
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_4",
      "step_type": "summarize",
      "description": "总结西电东送电量及同比增长",
      "depends_on": ["step_1", "step_2", "step_3"],
      "params": {{
        "response_format": "text",
        "emphasis": "measure"
      }}
    }}
  ]
}}


示例3 - 原因分析：
问题："哪些场站年度可利用小时数低于公司均值，原因"
拆解：
{{
  "original_question": "哪些场站年度可利用小时数低于公司均值，原因",
  "current_date": "2026-03-02",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询当前年度各场站的可利用小时数",
      "depends_on": [],
      "params": {{
        "query_description": "查询2025年各场站的年度可利用小时数",
        "time_range": {{"start": "2025-01-01", "end": "2025-12-31"}},
        "entities": [],
        "expected_table": "station_availability",
        "expected_columns": [
          {{"name": "station_name", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "available_hours", "role": "measure", "unit": "h", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "compute",
      "description": "计算公司均值并筛选低于均值的场站",
      "depends_on": ["step_1"],
      "params": {{
        "operations": [
          {{
            "function": "aggregate",
            "inputs": {{"data": "step_1", "column": "available_hours", "method": "mean"}},
            "output": "company_avg"
          }},
          {{
            "function": "filter",
            "inputs": {{"data": "step_1", "column": "available_hours", "operator": "<", "value": "step_2.company_avg"}},
            "output": "below_avg_stations"
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_3",
      "step_type": "analyze",
      "description": "分析低于均值场站的原因",
      "depends_on": ["step_2"],
      "params": {{
        "analysis_target": "低于公司均值的场站的可利用小时数偏低原因",
        "analysis_dimensions": ["wind_speed", "equipment_fault", "curtailment", "maintenance"],
        "supplementary_queries": [
          {{
            "query_description": "查询低于均值场站2025年的月均风速数据",
            "related_to_step": "step_2.below_avg_stations",
            "time_range": {{"start": "2025-01-01", "end": "2025-12-31"}},
            "expected_columns": [
              {{"name": "station_name", "role": "dimension", "unit": null, "filter": []}},
              {{"name": "month", "role": "dimension", "unit": null, "filter": []}},
              {{"name": "avg_wind_speed", "role": "measure", "unit": "m/s", "filter": []}}
            ]
          }},
          {{
            "query_description": "查询低于均值场站2025年的设备故障记录汇总",
            "related_to_step": "step_2.below_avg_stations",
            "time_range": {{"start": "2025-01-01", "end": "2025-12-31"}},
            "expected_columns": [
              {{"name": "station_name", "role": "dimension", "unit": null, "filter": []}},
              {{"name": "fault_count", "role": "measure", "unit": "次", "filter": []}},
              {{"name": "total_downtime_hours", "role": "measure", "unit": "h", "filter": []}}
            ]
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_4",
      "step_type": "summarize",
      "description": "总结分析结果",
      "depends_on": ["step_2", "step_3"],
      "params": {{
        "response_format": "text",
        "emphasis": "both"
      }}
    }}
  ]
}}


示例4 - 带filter的区域筛选查询：
问题："去年南方五省的发电量是多少？"
拆解：
{{
  "original_question": "去年南方五省的发电量是多少？",
  "current_date": "2026-03-02",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询2025年南方五省的发电量数据",
      "depends_on": [],
      "params": {{
        "query_description": "查询2025年南方五省（广东、广西、云南、贵州、海南）的年度总发电量，按地区分组",
        "time_range": {{"start": "2025-01-01", "end": "2025-12-31"}},
        "entities": [],
        "expected_table": "regional_generation",
        "expected_columns": [
          {{"name": "region", "role": "dimension", "unit": null, "filter": [{{"operator": "=", "values": ["广东", "广西", "云南", "贵州", "海南"]}}]}},
          {{"name": "total_generation", "role": "measure", "unit": "MWh", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "compute",
      "description": "汇总南方五省总发电量",
      "depends_on": ["step_1"],
      "params": {{
        "operations": [
          {{
            "function": "aggregate",
            "inputs": {{"data": "step_1", "column": "total_generation", "method": "sum"}},
            "output": "total_southern_five",
            "output_unit": "MWh"
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_3",
      "step_type": "summarize",
      "description": "总结去年南方五省的总发电量",
      "depends_on": ["step_1", "step_2"],
      "params": {{
        "response_format": "text",
        "emphasis": "measure"
      }}
    }}
  ]
}}


示例5 - 按月聚合+环比计算（使用subtract + divide代替period_over_period）：
问题："2026年3月全网统调发电量是多少？环比2月增长多少？"
拆解：
{{
  "original_question": "2026年3月全网统调发电量是多少？环比2月增长多少？",
  "current_date": "2026-04-01",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询2026年3月全网统调发电量（当期）",
      "depends_on": [],
      "params": {{
        "query_description": "查询2026年3月全网统调发电量，按月汇总",
        "time_range": {{"start": "2026-03-01", "end": "2026-03-31"}},
        "entities": [],
        "expected_table": "new_power_gen_rec",
        "expected_columns": [
          {{"name": "td_gen_energy", "role": "measure", "unit": "MWh", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "query",
      "description": "查询2026年2月全网统调发电量（上期，用于环比）",
      "depends_on": [],
      "params": {{
        "query_description": "查询2026年2月全网统调发电量，按月汇总",
        "time_range": {{"start": "2026-02-01", "end": "2026-02-28"}},
        "entities": [],
        "expected_table": "new_power_gen_rec",
        "expected_columns": [
          {{"name": "td_gen_energy", "role": "measure", "unit": "MWh", "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_3",
      "step_type": "compute",
      "description": "计算环比变动量和增长率",
      "depends_on": ["step_1", "step_2"],
      "params": {{
        "operations": [
          {{
            "function": "subtract",
            "inputs": {{"col_a": "step_1.td_gen_energy", "col_b": "step_2.td_gen_energy"}},
            "output": "pop_change_amount",
            "output_unit": "MWh"
          }},
          {{
            "function": "divide",
            "inputs": {{"col_a": "step_3.pop_change_amount", "col_b": "step_2.td_gen_energy", "multiply_by_100": true}},
            "output": "pop_growth_rate",
            "output_unit": "%"
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_4",
      "step_type": "summarize",
      "description": "总结3月统调发电量及环比变化",
      "depends_on": ["step_1", "step_2", "step_3"],
      "params": {{
        "response_format": "text",
        "emphasis": "measure"
      }}
    }}
  ]
}}
示例6 - 占比计算+环比+多条件过滤（含join合并中间结果）：
问题："广东各新能源发电类型占比超过20%且环比增长为正的有哪些？"
拆解：
{{
  "original_question": "广东各新能源发电类型占比超过20%且环比增长为正的有哪些？",
  "current_date": "2026-03-02",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询广东今日各新能源发电类型的发电量",
      "depends_on": [],
      "params": {{
        "query_description": "查询广东2026-03-02各新能源类型的发电量",
        "time_range": {{"start": "2026-03-02", "end": "2026-03-02"}},
        "entities": [],
        "expected_table": "new_power_gen_rec",
        "expected_columns": [
          {{"name": "gen_type", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "generation", "role": "measure", "unit": "MWh", "filter": []}},
          {{"name": "region_name", "role": "dimension", "unit": null, "filter": [{{"operator": "=", "values": ["广东"]}}]}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "query",
      "description": "查询广东昨日各新能源发电类型的发电量（用于计算环比）",
      "depends_on": [],
      "params": {{
        "query_description": "查询广东2026-03-01各新能源类型的发电量",
        "time_range": {{"start": "2026-03-01", "end": "2026-03-01"}},
        "entities": [],
        "expected_table": "new_power_gen_rec",
        "expected_columns": [
          {{"name": "gen_type", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "generation", "role": "measure", "unit": "MWh", "filter": []}},
          {{"name": "region_name", "role": "dimension", "unit": null, "filter": [{{"operator": "=", "values": ["广东"]}}]}}
        ]
      }}
    }},
    {{
      "step_id": "step_3",
      "step_type": "compute",
      "description": "计算总发电量、各类型占比、环比变化，合并结果后过滤占比>20%且环比增长>0的类型",
      "depends_on": ["step_1", "step_2"],
      "params": {{
        "operations": [
          {{
            "function": "aggregate",
            "inputs": {{"data": "step_1", "column": "generation", "method": "sum"}},
            "output": "total_gen"
          }},
          {{
            "function": "ratio",
            "inputs": {{"data": "step_1", "column": "generation", "total": "step_3.total_gen"}},
            "output": "with_ratio"
          }},
          {{
            "function": "change",
            "inputs": {{"current_data": "step_1", "previous_data": "step_2", "value_column": "generation", "join_keys": ["gen_type"]}},
            "output": "with_change"
          }},
          {{
            "function": "join",
            "inputs": {{"left": "step_3.with_ratio", "right": "step_3.with_change", "on": ["gen_type"], "how": "inner"}},
            "output": "merged_result"
          }},
          {{
            "function": "filter",
            "inputs": {{"data": "step_3.merged_result", "column": "ratio_value", "operator": ">", "value": 0.2}},
            "output": "ratio_filtered"
          }},
          {{
            "function": "filter",
            "inputs": {{"data": "step_3.ratio_filtered", "column": "change_rate", "operator": ">", "value": 0}},
            "output": "final_result"
          }}
        ]
      }}
    }},
    {{
      "step_id": "step_4",
      "step_type": "summarize",
      "description": "总结满足条件的发电类型",
      "depends_on": ["step_3"],
      "params": {{
        "response_format": "text",
        "emphasis": "both"
      }}
    }}
  ]
}}

示例7 - 根据证券主体类型选择营收指标：
问题："2025年宁德时代营收情况"
业务推理：根据"证券主体分类映射"，宁德时代属于非金融类证券用户；用户问的是概括性的"营收情况"而非具体财务科目；因此根据"营收指标选择规则"，应使用"非金融类用户营收情况(abc)"指标。
拆解：
{{
  "original_question": "2025年宁德时代营收情况",
  "current_date": "2026-04-14",
  "steps": [
    {{
      "step_id": "step_1",
      "step_type": "query",
      "description": "查询2025年宁德时代（非金融类证券用户）的营收情况",
      "depends_on": [],
      "params": {{
        "query_description": "查询2025年宁德时代的非金融类用户营收情况，按报告期展示",
        "time_range": {{"start": "2025-01-01", "end": "2025-12-31"}},
        "entities": ["宁德时代"],
        "expected_table": "ss_sec_ashareincome",
        "expected_columns": [
          {{"name": "SECURITY_NAME", "role": "dimension", "unit": null, "filter": [{{"operator": "=", "values": ["宁德时代"]}}]}},
          {{"name": "REPORT_PERIOD", "role": "dimension", "unit": null, "filter": []}},
          {{"name": "STATEMENT_TYPE", "role": "dimension", "unit": null, "filter": [{{"operator": "=", "values": ["408001000"]}}]}},
          {{"name": "abc", "role": "measure", "unit": null, "filter": []}}
        ]
      }}
    }},
    {{
      "step_id": "step_2",
      "step_type": "summarize",
      "description": "总结2025年宁德时代的营收情况",
      "depends_on": ["step_1"],
      "params": {{
        "response_format": "text",
        "emphasis": "measure"
      }}
    }}
  ]
}}

【示例说明】宁德时代为制造业企业，属于非金融类证券用户，概括性"营收情况"查询应使用abc指标。若用户问的是"招商银行营收情况"，则招商银行属于金融类证券用户，应使用abcd（金融类用户营收情况）指标。若用户明确问"宁德时代营业总收入"，则直接使用TOT_OPER_REV。

=== 用户问题 ===
{query}

请将以上用户问题拆解为可执行的结构化步骤JSON。

生成前请严格执行以下检查流程：
1. 【表名检查】对每个query步骤，确认expected_table是上方"可用数据库元信息"中实际存在的表名，不是示例中的虚构表名，不是提示词中的描述性文本（如"可用数据库元信息"），也不是自己编造的。如果找不到匹配的表，填"UNKNOWN"。
2. 【列名检查】对每个query步骤，确认expected_columns中的每个name来自该表元信息中的metric_name/metric_code/dimension_name/dimension_code。
3. 检查通过后，只输出JSON，不要输出其他内容。
"""

def _format_business_context(database_meta: dict) -> str:
    """Format database meta information for prompt injection."""
    lines = []
    ctx = database_meta.get("business_context")
    if ctx:
        lines.append(f"{ctx}")
        lines.append("")

    return "\n".join(lines) if lines else "无特定业务知识。"

def _format_database_meta(database_meta: dict) -> str:
    """Format database meta information for prompt injection.

    v1.0.2 整改（实施方案.md 第五节.1）：元数据段从自然语言改为治理后的
    结构化 JSON（两级合并每列权威 agg_type，见 utils/meta_governance）。
    渲染失败不静默回退自然语言段——回退会把错标 sum 的存量列重新暴露给模型，
    快照误聚合防线失效，fail-fast 与 get_prompt_template 同一口径。
    """
    from system_b.utils.meta_governance import merge_database_meta, render_database_meta
    return render_database_meta(merge_database_meta(database_meta))


def _build_prompts(query: str, database_meta: dict):
    """Build system_prompt and user_prompt, shared by decompose_query and decompose_query_stream."""
    database_meta_text = _format_database_meta(database_meta)
    functions_text = get_functions_prompt_text()
    business_context = _format_business_context(database_meta)
    system_prompt, _USER_TEMPLATE, usr_template_ref = get_prompt_template()

    # Compute hashes for variable parts (archive on first occurrence)
    db_meta_hash = hash_content(database_meta)
    func_hash = hash_content(functions_text)
    biz_hash = hash_content(business_context)
    archive_variable(
        "database_meta",
        db_meta_hash,
        json.dumps(database_meta, ensure_ascii=False, indent=2),
    )
    archive_variable("functions_text", func_hash, functions_text)
    archive_variable("business_logic_knowledge", biz_hash, business_context)

    user_prompt = render_template(
        _USER_TEMPLATE,
        current_date=datetime.now().date().isoformat(),
        database_meta_text=database_meta_text,
        functions_text=functions_text,
        query=query,
        business_logic_knowledge=business_context,
    )
    usr_vars = {
        "query": query,
        "db_meta_hash": db_meta_hash,
        "db_meta_len": len(database_meta_text),
        "func_hash": func_hash,
        "func_len": len(functions_text),
        "biz_hash": biz_hash,
        "biz_len": len(business_context),
    }
    return {
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
        "usr_template_ref": usr_template_ref,
        "usr_vars": usr_vars,
    }

# Retry temperature ladder: attempt 1 keeps production-proven low temperature;
# later attempts increase randomness because at 0.1 the model reproduces the
# same failed plan almost verbatim (2026-09-11 月累计 case: 3 identical failures).
_DECOMPOSE_TEMPERATURES = [0.1, 0.3, 0.5]


def _build_retry_user_prompt(base_user_prompt: str, last_error: str, database_meta: dict) -> str:
    """Build the user prompt for a retry attempt.

    错误提示置顶（2026-09-11 月累计案例：追加在 90k prompt 末尾的一句话被完全
    淹没，3 次重试输出几乎相同）。对已知错误模式（月/年累计缺列）注入对比式
    few-shot：错误方案 vs 正确方案。示例中的表名/列名/时间从本次请求的真实
    元数据填充——提示词铁律禁止编造表名，写死示例表名会诱导模型照抄虚构表名。
    函数库没有 first/last，"取期末值"必须用 top_n(sort_by=时间列, n=1) 示范，
    且不能用 aggregate(max) 代替（max 是峰值，数据修正后不等于期末值）。
    """
    head = (
        "【重试纠错 — 最高优先级，先完整阅读本块，再重新生成】\n"
        f"上一次尝试失败，错误信息：{last_error}\n"
    )

    fewshot = _build_cumulative_fewshot(database_meta)
    if fewshot:
        head += fewshot
    else:
        head += "请严格对照错误信息修正拆解方案，只输出符合 Schema 的完整 JSON。\n"

    return head + "\n" + base_user_prompt


def _build_cumulative_fewshot(database_meta: dict) -> str:
    """针对 月/年累计缺列 错误生成对比式 few-shot；元数据缺对应列时返回空串。"""
    _requirements = [
        ("月累计", "monthpowersupply", "月累计供电量"),
        ("年累计", "yearpowersupply", "年累计供电量"),
    ]

    table_name, time_col, cum_col, cum_unit = None, None, None, None
    for table in database_meta.get("table_summaries", []):
        cols = {str(c.get("column_name", "")).lower(): c for c in table.get("columns", [])}
        for kw, col, zh in _requirements:
            if col in cols:
                table_name = table.get("table_name", "")
                time_col = "ptdate" if "ptdate" in cols else next(
                    (n for n in cols if "date" in n), None)
                cum_col, cum_unit = col, cols[col].get("description", "") or zh
                break
        if table_name:
            break

    if not (table_name and time_col and cum_col):
        return ""

    return f"""已知错误模式与正确做法示例（表名/列名来自本次真实元信息，可直接使用）：

❌ 错误方案（禁止再次出现）：query 步骤只查日值列，再用 aggregate(sum) 对日值求和。
   {cum_col} 已是累计值，重新求和会因数据修正产生口径偏差。

✅ 正确方案：
  step_1: query，直接查累计列：
  {{
    "step_id": "step_1", "step_type": "query",
    "description": "查询时间范围内{ '月累计' if cum_col == 'monthpowersupply' else '年累计' }供电量",
    "depends_on": [],
    "params": {{
      "query_description": "查询时间范围内{cum_unit}",
      "time_range": {{"start": "<区间起始日>", "end": "<区间结束日>"}},
      "entities": [],
      "expected_table": "{table_name}",
      "expected_columns": [
        {{"name": "{time_col}", "role": "dimension", "unit": null, "filter": []}},
        {{"name": "{cum_col}", "role": "measure", "unit": null, "filter": []}}
      ]
    }}
  }}
  step_2: compute，取区间最后一天（top_n 按日期降序取第 1 行），不要用 aggregate(max)：
  {{
    "step_id": "step_2", "step_type": "compute",
    "description": "取区间最后一天的累计供电量作为累计值",
    "depends_on": ["step_1"],
    "params": {{
      "operations": [
        {{
          "function": "top_n",
          "inputs": {{"data": "step_1", "sort_by": "{time_col}", "n": 1}},
          "output": "cumulative_result"
        }}
      ]
    }}
  }}

若问题还要求同环比，比较期 query 步骤必须包含同样的累计列，并对每个指标分别计算。
只输出符合 Schema 的完整 JSON，不要输出其他内容。
"""


def decompose_query_stream(query: str, database_meta: dict):
    """
    Streaming decomposition: yields steps as they are parsed from the LLM token flow.

    Steps are validated individually when parsed and buffered until their
    predecessors are present, then released in ascending numeric step_id
    order — the LLM does not guarantee it emits steps in step_id order
    (2026-09-14 case: step_3 → step_4 → step_2), and downstream consumers
    (frontend think panel, System A message order) rely on that order.

    Design decision 2026-09-14: once any step fails validation, the attempt
    is doomed ("部分步骤校验失败" is only raised at stream end), so no further
    step events are yielded — including steps already parsed and buffered.
    Failed rounds must leave zero trace in downstream channels; otherwise
    their steps reach the frontend and System A and are only retracted later
    via chat/clear. The token stream is still consumed to the end so
    attempt_failed/full_text keep the complete raw output for debugging.

    If any step fails validation or the final cross-step validation fails,
    the LLM is retried (up to 3 attempts) with error hints prepended to the
    user prompt.

    Yields:
        {"type": "attempt_started", "attempt": N}
        {"type": "raw_delta", "attempt": N, "delta": str}   模型原始输出 token
        {"type": "step", "step": dict}                      校验通过且顺序就绪的步骤
        {"type": "attempt_failed", "attempt": N, "error": str, "full_text": str}
        {"type": "done", "attempt": N, "steps": [...], "full_text": str}
    """
    logger.info(f"[Decompose-Stream] Starting for: {query[:80]}...")
    prompts = _build_prompts(query, database_meta)
    system_prompt = prompts["system_prompt"]
    user_prompt = prompts["user_prompt"]
    first_system_prompt = system_prompt
    first_user_prompt = user_prompt

    max_retries = 3
    last_error = None

    for attempt in range(1, max_retries + 1):
        _decompose_t0 = time.time()
        parser = StreamStepParser()
        full_text = ""
        step_ids_seen = set()
        had_validation_error = False
        # 步骤缓冲区：LLM 不保证按 step_id 顺序输出（2026-09-14 案例：
        # step_3 → step_4 → step_2），步骤解析+校验通过后先入缓冲，
        # 等前序编号到齐再按序放行，保证下游（前端 think 面板/系统A）看到有序流。
        buffered_steps = {}
        expected_next = 1
        yield {"type": "attempt_started", "attempt": attempt}

        try:
            for delta in call_llm_stream(
                system_prompt, user_prompt, temperature=_DECOMPOSE_TEMPERATURES[min(attempt, len(_DECOMPOSE_TEMPERATURES)) - 1],
                request_id="", source="decompose",
                sys_prompt_ref={"group": "prompt-main", "version": prompts.get("usr_template_ref", {}).get("version", "")},
                usr_template_ref=prompts.get("usr_template_ref", {}),
                usr_vars=prompts.get("usr_vars", {}),
            ):
                full_text += delta
                yield {"type": "raw_delta", "attempt": attempt, "delta": delta}
                new_steps = parser.feed(delta)
                for step in new_steps:
                    try:
                        _validate_single_step(step, step_ids_seen)
                        _validate_column_table_membership(step, database_meta)
                        step_ids_seen.add(step["step_id"])
                    except StepDecompositionError as e:
                        had_validation_error = True
                        logger.warning(f"[Decompose-Stream] Attempt {attempt} step validation failed: {e} | step={json.dumps(step, ensure_ascii=False)}")
                        continue
                    m = re.fullmatch(r"step_(\d+)", str(step.get("step_id", "")))
                    if not m:
                        # 无法编号的 step_id 不参与顺序缓冲，直接放行（现网 schema 下不会出现）
                        yield {"type": "step", "step": step}
                        continue
                    buffered_steps[int(m.group(1))] = step
                    # 校验失败后本轮注定重试，缓冲中的步骤全部不再放行（零残留）
                    while not had_validation_error and expected_next in buffered_steps:
                        ready = buffered_steps.pop(expected_next)
                        _step_elapsed = time.time() - _decompose_t0
                        logger.info(f"[Decompose-Stream] Captured {ready.get('step_id')}: {ready.get('step_type')} at {_step_elapsed:.2f}s")
                        yield {"type": "step", "step": ready}
                        expected_next += 1

            final_steps = parser.finalize()

            if had_validation_error:
                raise StepDecompositionError("部分步骤校验失败，请修正")

            # 流结束补发断档编号之后的缓冲步骤（如 step_2 缺失时 3/4 仍在缓冲）。
            # 此时无更多输入，按编号升序放行仍保持有序。
            while buffered_steps:
                ready = buffered_steps.pop(min(buffered_steps))
                _step_elapsed = time.time() - _decompose_t0
                logger.info(f"[Decompose-Stream] Captured {ready.get('step_id')}: {ready.get('step_type')} at {_step_elapsed:.2f}s")
                yield {"type": "step", "step": ready}

            # Cross-step validation (may append summarize step)
            fake_result = {"steps": final_steps, "original_question": query}
            _validate_decomposition(fake_result, user_query=query, database_meta=database_meta)
            final_steps = fake_result["steps"]

            _decompose_elapsed = time.time() - _decompose_t0
            logger.info(f"[Decompose-Stream] Done attempt {attempt}, {len(final_steps)} steps, elapsed={_decompose_elapsed:.2f}s")

            yield {
                "type": "done",
                "attempt": attempt,
                "steps": final_steps,
                "full_text": full_text,
                "_system_prompt": first_system_prompt,
                "_user_prompt": first_user_prompt,
            }
            return

        except StepDecompositionError as e:
            last_error = str(e)
            logger.warning(f"[Decompose-Stream] Attempt {attempt}/{max_retries} failed: {e} | full_text_len={len(full_text)} | full_text={full_text[-2000:]}")
            yield {"type": "attempt_failed", "attempt": attempt, "error": last_error, "full_text": full_text}
            if attempt < max_retries:
                user_prompt = _build_retry_user_prompt(user_prompt, last_error, database_meta)
            else:
                raise StepDecompositionError(
                    f"Failed to decompose query after {max_retries} attempts. Last error: {last_error}",
                    system_prompt=system_prompt,
                    user_prompt=user_prompt,
                )


def decompose_query(query: str, database_meta: dict) -> dict:
    """
    Decompose a user query into structured execution steps using LLM.

    Args:
        query: User's natural language question
        database_meta: Database metadata from System A

    Returns:
        Structured steps dict with original_question, current_date, steps
    """
    logger.info(f"[Decompose] Starting decomposition for: {query[:80]}...")
    prompts = _build_prompts(query, database_meta)
    system_prompt = prompts["system_prompt"]
    user_prompt = prompts["user_prompt"]
    first_system_prompt = system_prompt
    first_user_prompt = user_prompt

    max_retries = 3
    last_error = None

    for attempt in range(1, max_retries + 1):
        try:
            logger.info(f"[Decompose] Attempt {attempt}/{max_retries}")

            result = call_llm_json(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                temperature=_DECOMPOSE_TEMPERATURES[min(attempt, len(_DECOMPOSE_TEMPERATURES)) - 1],
                request_id="", source="decompose",
                sys_prompt_ref={"group": "prompt-main", "version": prompts.get("usr_template_ref", {}).get("version", "")},
                usr_template_ref=prompts.get("usr_template_ref", {}),
                usr_vars=prompts.get("usr_vars", {}),
            )
            print("###############################")
            print(system_prompt)
            print("###############################")
            print("###############################")
            print(user_prompt)
            print("###############################")
            # Validate basic structure
            _validate_decomposition(result, user_query=query, database_meta=database_meta)

            logger.info(f"[Decompose] Success===: {len(result.get('steps', []))} steps")
            print("=======================")
            print(result)
            print("=======================")

            result["_system_prompt"] = first_system_prompt
            result["_user_prompt"] = first_user_prompt
            return result

        except Exception as e:
            last_error = str(e)
            logger.warning(f"[Decompose] Attempt {attempt} failed: {e}")
            if attempt < max_retries:
                # Add error hint to prompt for retry (prepended, not appended)
                user_prompt = _build_retry_user_prompt(user_prompt, last_error, database_meta)

    raise StepDecompositionError(f"Failed to decompose query after {max_retries} attempts. Last error: {last_error}")


def _build_table_column_index(database_meta: dict) -> dict:
    """Build {table_name: {column_name, ...}} from database_meta.table_summaries."""
    index = {}
    for table in (database_meta or {}).get("table_summaries", []) or []:
        tname = table.get("table_name", "")
        if not tname:
            continue
        cols = {c.get("column_name", "") for c in table.get("columns", []) or []}
        cols.discard("")
        index[tname] = cols
    return index


def _build_table_column_desc_index(database_meta: dict) -> dict:
    """Build {table_name: {column_name_lower: description_text}} from database_meta."""
    index = {}
    for table in (database_meta or {}).get("table_summaries", []) or []:
        tname = table.get("table_name", "")
        if not tname:
            continue
        cols = {}
        for c in table.get("columns", []) or []:
            name = c.get("column_name", "")
            if not name:
                continue
            desc = c.get("description") or c.get("column_comment") or ""
            cols[name.lower()] = str(desc)
        index[tname] = cols
    return index


def _validate_column_table_membership(step: dict, database_meta: dict):
    """跨表混查硬校验：query 步骤的每个 expected_columns[].name 必须属于 expected_table。

    2026-09-11 服务器案例：模型把 substation_count（user_scale_quanliang 表）与
    statistic0101（archive_scale_stat 表）塞进同一个 query，执行 0 行。规则十六
    只存在于提示词层，模型漏检；此校验将漏检升级为重试触发条件。
    expected_table 本身不在元数据中时（表名错误）不在此拦截——已有表名自检，
    避免双重报错掩盖根因。
    """
    if step.get("step_type") != "query":
        return
    params = step.get("params", {}) or {}
    table = params.get("expected_table", "")
    if not table:
        return
    index = _build_table_column_index(database_meta)
    if table not in index:
        return
    table_cols = index[table]
    offenders = [
        c.get("name", "")
        for c in params.get("expected_columns", []) or []
        if isinstance(c, dict) and c.get("name") and c.get("name") not in table_cols
    ]
    if offenders:
        raise StepDecompositionError(
            f"{step.get('step_id', '?')}: 跨表混查——列 {offenders} 不属于表 {table}。"
            f"必须将不属于该表的指标拆分为独立的 query 步骤，指向其真实所属的表"
            f"（参见规则十六/示例13）。"
        )


def _validate_snapshot_marking(step: dict, database_meta: dict, gov_index: dict = None):
    """反向快照校验：模型把未声明聚合类型的指标错标为 agg_type=快照 时拦截。

    与"快照指标必须按快照处理"的前向校验互补：前向校验管"声明了快照的列必须
    带快照标记"，此校验管"没声明快照的列不许带快照标记"。错标会导致
    latest_data 把该列从输出中剔除、结果为空。
    判定源用治理索引（与提示词渲染同源，meta_governance.merge_database_meta），
    声明话术（"指标聚合类型为【快照】"）写在 available_metrics 的指标描述里，
    表列描述不可改，故不再读原始列描述文本。
    治理后 agg_type=snapshot（声明来源不限：指标描述/列描述/time_format 兜底）→
    放行；非 snapshot → 拦截；表/列不在治理索引中 → 跳过（表名自检与跨表
    成员校验已管）。
    """
    if step.get("step_type") != "query":
        return
    params = step.get("params", {}) or {}
    table = params.get("expected_table", "")
    if not table:
        return
    if gov_index is None:
        from system_b.utils.meta_governance import build_governed_index, merge_database_meta
        gov_index = build_governed_index(merge_database_meta(database_meta))
    gov_cols = gov_index.get(table.lower())
    if not gov_cols:
        return
    for col in params.get("expected_columns", []) or []:
        if not isinstance(col, dict) or col.get("agg_type") != "快照":
            continue
        gov_col = gov_cols.get(str(col.get("name", "")).lower())
        if gov_col is None:
            continue
        if gov_col.get("agg_type") == "snapshot":
            continue
        raise StepDecompositionError(
            f"{step.get('step_id', '?')}: expected_columns 中 '{col.get('name')}' "
            f"标注了 agg_type='快照'，但元信息中该指标未声明聚合类型【快照】"
            f"（可在指标描述中声明，如\"指标聚合类型为【快照】\"）。"
            f"未声明聚合类型的指标必须 agg_type=null（规则十四之二【快照类指标处理-强制】）；"
            f"若属座数/户数/台数等时点存量类指标，保持 agg_type=null，仍按【快照时间检查】"
            f"带7天滚动窗口和ptdate。请修正后重新输出。"
        )


# 存量词模式：列描述含这些词即视为时点存量语义（无论是否声明"快照"），
# 与提示词规则十七【例外-强制】的座数/户数/台数口径一致。
_STOCK_PATTERN = re.compile(
    r"座数|户数|台数|条数|个数|数量$|总数$|用户数|终端数|容量$|变电站"
)


def _is_snapshot_semantic(col_name_lower: str, desc_index: dict) -> bool:
    """判断列是否快照/时点存量语义：元数据声明快照 或 描述命中存量词模式。"""
    for cols in desc_index.values():
        if col_name_lower in cols:
            desc = cols[col_name_lower]
            return "快照" in desc or bool(_STOCK_PATTERN.search(desc))
    return False


def _resolve_query_lineage(steps: list, start_step_id: str) -> list:
    """沿 depends_on 回溯 start_step_id 的全部上游，返回路径上的 query 步骤列表。"""
    step_map = {s["step_id"]: s for s in steps}
    query_steps, visited, queue = [], {start_step_id}, [start_step_id]
    while queue:
        cur = step_map.get(queue.pop(0))
        if not cur:
            continue
        if cur.get("step_type") == "query":
            query_steps.append(cur)
        for dep in cur.get("depends_on", []) or []:
            if dep not in visited:
                visited.add(dep)
                queue.append(dep)
    return query_steps


def _chain_has_latest_data(steps: list, start_step_id: str) -> bool:
    """start_step_id 及其上游链上是否存在 latest_data 操作（截面已做）。"""
    step_map = {s["step_id"]: s for s in steps}
    visited, queue = {start_step_id}, [start_step_id]
    while queue:
        cur = step_map.get(queue.pop(0))
        if not cur:
            continue
        if cur.get("step_type") == "compute":
            for op in cur.get("params", {}).get("operations", []) or []:
                if op.get("function") == "latest_data":
                    return True
        for dep in cur.get("depends_on", []) or []:
            if dep not in visited:
                visited.add(dep)
                queue.append(dep)
    return False


def _parse_date(s):
    """解析 YYYY-MM-DD（容忍 YYYY/M/D），失败返回 None。"""
    m = re.match(r"(\d{4})[-/](\d{1,2})[-/](\d{1,2})", str(s).strip())
    if not m:
        return None
    try:
        return datetime(int(m.group(1)), int(m.group(2)), int(m.group(3)))
    except ValueError:
        return None


def _validate_aggregate_semantics(steps: list, database_meta: dict, gov_index: dict = None):
    """快照/累计/比率指标误 sum/mean 硬校验（元数据治理对齐，v1.0.2 整改）。

    聚合语义优先查治理索引（三级合并后的权威 agg_type，utils/meta_governance）：
      snapshot/cumulative/avg → 多日窗口上直接 aggregate(sum/mean) 拦截；
      sum → 信任治理结果放行，跳过存量词兜底（防抄表条数/告警次数等
            白名单族被「条数/数量」字面误伤）；
      列不在治理索引（无 is_measure/is_dimension 标志的列）→ 退回存量词
      描述判定（只收窄不放宽）。
    对每个拦截候选：沿依赖链回溯确认未做 latest_data 截面且来源 query 窗口
    ≥2 天 → 拦截。count/max/min 合理跳过；来源不明或表不在元数据时跳过
    （只降级不误杀）。
    """
    if not database_meta:
        return
    if gov_index is None:
        from system_b.utils.meta_governance import build_governed_index, merge_database_meta
        gov_index = build_governed_index(merge_database_meta(database_meta))
    from system_b.utils.meta_governance import get_column_agg
    desc_index = _build_table_column_desc_index(database_meta)
    step_map = {s["step_id"]: s for s in steps}

    for step in steps:
        if step.get("step_type") != "compute":
            continue
        for op in step.get("params", {}).get("operations", []) or []:
            if op.get("function") != "aggregate":
                continue
            _op_inputs = op.get("inputs", {}) or {}
            method = str(
                op.get("method") or _op_inputs.get("method") or op.get("params", {}).get("method") or ""
            ).lower()
            if method not in ("sum", "mean"):
                continue
            column = op.get("column") or _op_inputs.get("column") or ""
            if isinstance(column, list):
                col_names = [str(c) for c in column]
            elif isinstance(column, str) and "," in column:
                col_names = [c.strip() for c in column.split(",") if c.strip()]
            else:
                col_names = [str(column)]
            data_ref = op.get("data") or _op_inputs.get("data") or ""
            source_step_id = str(data_ref).split(".")[0] if data_ref else step["step_id"]
            source = step_map.get(source_step_id)
            if not source:
                continue
            source_query = source if source.get("step_type") == "query" else None
            queries = ([source_query] if source_query
                       else _resolve_query_lineage(steps, source_step_id))
            if not queries:
                continue
            source_table = str(
                ((queries[0].get("params", {}) or {}).get("expected_table")) or "")
            for col_name in col_names:
                base = col_name.split(".")[-1].strip().lower()
                gov_agg = get_column_agg(gov_index, source_table, base)
                if gov_agg == "sum":
                    continue
                if gov_agg in ("snapshot", "cumulative", "avg"):
                    reason = gov_agg
                elif _is_snapshot_semantic(base, desc_index):
                    reason = "snapshot"
                else:
                    continue
                if _chain_has_latest_data(steps, source_step_id):
                    continue
                for q in queries:
                    tr = (q.get("params", {}) or {}).get("time_range") or {}
                    d1, d2 = _parse_date(tr.get("start", "")), _parse_date(tr.get("end", ""))
                    if not (d1 and d2 and (d2 - d1).days >= 1):
                        # 单日窗口或无时间：单日数据 sum 无跨天重复，放行
                        continue
                    _window = f"{tr.get('start')}~{tr.get('end')}"
                    raise StepDecompositionError(
                        _agg_semantics_error(step["step_id"], method, col_name, reason, _window))


def _agg_semantics_error(step_id: str, method: str, col_name: str, reason: str, window: str) -> str:
    """按治理 agg_type 生成拦截文案；snapshot 分支保留旧文案关键触达
    「快照/时点存量」（存量测试 test_validation_hard_guards 依赖该子串）。"""
    if reason == "cumulative":
        return (
            f"{step_id}: aggregate({method}) 直接对期内累计指标 '{col_name}'"
            f"（治理类型 cumulative，值本身已是累加结果）的多日数据（窗口 {window}）"
            f"求聚合会二次累加导致虚增。必须取期末行：top_n(sort_by=时间列, n=1)，"
            f"禁止 sum/逐日重算（规则十四【月/年累计口径】）。"
        )
    if reason == "avg":
        return (
            f"{step_id}: aggregate({method}) 直接对比率指标 '{col_name}'"
            f"（治理类型 avg）跨行求聚合会虚增/虚降。比率禁止 sum/mean，"
            f"应取当日值或按分子分母重新加权计算。"
        )
    return (
        f"{step_id}: aggregate({method}) 直接对快照/时点存量"
        f"指标 '{col_name}' 的多日数据（窗口 {window}）求聚合，会把同一"
        f"实体按天重复加总导致结果虚增数倍。必须先 latest_data"
        f"(time_column=ptdate, group_by=[组织维度列]) 取每个组织最新一天"
        f"截面，再对截面 aggregate（规则十七【例外-强制】）。"
    )


def _validate_compare_range_lengths(steps: list, user_query: str):
    """A4 同比区间等长硬校验：恰好两个 query 区间且跨年、不相交 → 检查等长。

    两个区间时几乎必然是本期/对比期（长度差 >7 天 → 拦截，如"年初至今"
    配了去年全年）。3 个及以上区间时无法可靠判定哪两个构成对比期
    （多实体/多时间段场景极易误杀），跳过不校验。
    """
    if not user_query or "同比" not in user_query:
        return
    ranges = []
    for step in steps:
        if step.get("step_type") != "query":
            continue
        tr = (step.get("params", {}) or {}).get("time_range") or {}
        d1, d2 = _parse_date(tr.get("start", "")), _parse_date(tr.get("end", ""))
        if d1 and d2:
            ranges.append((d1, d2, step["step_id"]))
    if len(ranges) != 2:
        return
    a, b = ranges
    if a[0].year == b[0].year:
        return
    # 相交视为不同实体同期查询，跳过
    if a[0] <= b[1] and b[0] <= a[1]:
        return
    len_a, len_b = (a[1] - a[0]).days + 1, (b[1] - b[0]).days + 1
    if abs(len_a - len_b) > 7:
        raise StepDecompositionError(
            f"{a[2]} 与 {b[2]} 的查询区间疑似本期/对比期但长度不等"
            f"（{len_a} 天 vs {len_b} 天）。同比对比期区间必须与本期严格等长"
            f"（如 1-7月 vs 去年1-7月；年初至今须配去年同日起至今）"
            f"（规则十四【同比对比期同区间-强制】）。"
        )


def _validate_consecutive_granularity(steps: list, user_query: str):
    """C1 连续N月粒度硬校验：日粒度数据直接 consecutive_check → 拦截。

    正确路径二选一：链上有 extract_month/extract_year_month 派生月份分组列，
    或先聚合到月粒度。日 time_range <45 天（不足两个月）不可能是月判断，放行。
    """
    if not user_query or not re.search(r"连续\s*\d+\s*个?月", user_query):
        return
    step_map = {s["step_id"]: s for s in steps}
    for step in steps:
        if step.get("step_type") != "compute":
            continue
        for op in step.get("params", {}).get("operations", []) or []:
            if op.get("function") != "consecutive_check":
                continue
            data_ref = str(op.get("data") or op.get("inputs", {}).get("data") or "")
            start = data_ref.split(".")[0] if data_ref else step["step_id"]
            if _chain_has_month_derivation(steps, start):
                continue
            for q in _resolve_query_lineage(steps, start):
                tr = (q.get("params", {}) or {}).get("time_range") or {}
                d1, d2 = _parse_date(tr.get("start", "")), _parse_date(tr.get("end", ""))
                if d1 and d2 and (d2 - d1).days >= 45:
                    raise StepDecompositionError(
                        f"{step['step_id']}: consecutive_check 直接用于日粒度数据判断"
                        f"'连续N月'是错误口径（日数据一个月约30行，连续3天增长≠连续1月"
                        f"增长）。必须先用 extract_month/extract_year_month 派生月份分组"
                        f"列并聚合到月粒度，再 consecutive_check（规则十四【聚合与日期处理规范】）。"
                    )


def _chain_has_month_derivation(steps: list, start_step_id: str) -> bool:
    """链上是否存在 extract_month/extract_year_month 或按月聚合操作。"""
    step_map = {s["step_id"]: s for s in steps}
    visited, queue = {start_step_id}, [start_step_id]
    while queue:
        cur = step_map.get(queue.pop(0))
        if not cur:
            continue
        if cur.get("step_type") == "compute":
            for op in cur.get("params", {}).get("operations", []) or []:
                fn = op.get("function", "")
                if fn in ("extract_month", "extract_year_month"):
                    return True
                if fn == "aggregate":
                    gb = op.get("group_by") or op.get("inputs", {}).get("group_by") or []
                    if any("month" in str(g).lower() for g in gb):
                        return True
        for dep in cur.get("depends_on", []) or []:
            if dep not in visited:
                visited.add(dep)
                queue.append(dep)
    return False


def _validate_dimension_filter_values(step: dict, database_meta: dict, gov_index: dict = None):
    """B2 维度值硬校验：filter 值模糊命中维度取值枚举时要求用精确值。

    数据源：治理合并后的维度 values 枚举（available_dimensions.possible_values，
    见 utils/meta_governance）。列无枚举时跳过（兼容空枚举维度）。
    gov_index 由调用方构建一次复用；未提供时此处自行构建（单步校验场景）。
    """
    if step.get("step_type") != "query" or not database_meta:
        return
    if gov_index is None:
        from system_b.utils.meta_governance import build_governed_index, merge_database_meta
        gov_index = build_governed_index(merge_database_meta(database_meta))
    params = step.get("params", {}) or {}
    table = str(params.get("expected_table", "")).lower()
    for col in params.get("expected_columns", []) or []:
        if not isinstance(col, dict) or not col.get("filter"):
            continue
        gov_col = gov_index.get(table, {}).get(str(col.get("name", "")).lower())
        if not gov_col:
            continue
        allowed = gov_col.get("values") or []
        if not isinstance(allowed, list) or not allowed:
            continue
        for fv_str in _iter_filter_values(col["filter"]):
            if fv_str in allowed:
                continue
            fuzzy = [v for v in allowed if fv_str and fv_str in v]
            if len(fuzzy) == 1:
                raise StepDecompositionError(
                    f"{step['step_id']}: 维度 '{col.get('name')}' 的过滤值 "
                    f"'{fv_str}' 无法精确匹配维度可选值清单，但存在唯一相近值 "
                    f"'{fuzzy[0]}'。必须使用精确值 '{fuzzy[0]}'；若确认表中无"
                    f"对应维度值才允许移除该过滤并在 description 说明（规则十六"
                    f"【维度值纠错】）。"
                )
            # 模糊命中多个或零个：不猜，交由运行时返回0行时自然暴露


def _iter_filter_values(filter_list):
    """兼容两种 filter 形态：纯字符串数组与提示词规定的
    [{"operator": "=", "values": [...]}] 对象数组。"""
    for item in filter_list:
        if isinstance(item, dict):
            for v in item.get("values") or []:
                yield str(v)
        else:
            yield str(item)


def _validate_single_step(step: dict, step_ids_seen: set):
    """
    Validate a single step independently — can be called as soon as a step is parsed.
    Raises StepDecompositionError on failure so the caller can skip yielding this step
    and trigger a retry later.

    Checks:
      - step_type is valid
      - step_id is not duplicate
      - query step: has at least one measure column (rule 19)
      - query step: no pseudo time dimensions (rule 14)
      - compute step: no removed functions (rule 13)
      - compute step: no self-referential arithmetic (col_a == col_b)
    """
    step_id = step.get("step_id", "")
    if not step_id:
        logger.error(f"[ValidateStep] Missing 'step_id': {json.dumps(step, ensure_ascii=False)}")
        raise StepDecompositionError(f"Missing 'step_id' in step: {step}")

    valid_types = {"query", "compute", "analyze", "summarize"}
    step_type = step.get("step_type", "")
    if step_type not in valid_types:
        logger.error(f"[ValidateStep] Invalid step_type '{step_type}' in {step_id}: {json.dumps(step, ensure_ascii=False)}")
        raise StepDecompositionError(f"Invalid step_type: {step_type} in {step_id}")

    if step_id in step_ids_seen:
        logger.error(f"[ValidateStep] Duplicate step_id '{step_id}': {json.dumps(step, ensure_ascii=False)}")
        raise StepDecompositionError(f"Duplicate step_id: {step_id}")

    # Query step checks
    if step_type == "query":
        params = step.get("params", {})
        expected_columns = params.get("expected_columns", []) or []
        roles = [c.get("role") for c in expected_columns if isinstance(c, dict)]
        if expected_columns and "measure" not in roles:
            logger.error(
                f"[ValidateStep] {step_id}: no measure column in expected_columns: "
                f"{json.dumps(step, ensure_ascii=False)}"
            )
            raise StepDecompositionError(
                f"{step_id}: query step has no measure column in "
                f"expected_columns (only dimensions: "
                f"{[c.get('name') for c in expected_columns if isinstance(c, dict)]}). "
                f"每个query步骤必须至少包含一个measure列（规则十九）。"
            )

        _pseudo_dims = {"ptweek", "ptyear", "ptmonth", "ptquarter",
                        "ptweek()", "ptyear()", "ptmonth()", "ptquarter()"}
        for c in expected_columns:
            if isinstance(c, dict) and str(c.get("name", "")).lower() in _pseudo_dims:
                logger.error(
                    f"[ValidateStep] {step_id}: pseudo time dimension "
                    f"'{c.get('name')}' in step: {json.dumps(step, ensure_ascii=False)}"
                )
                raise StepDecompositionError(
                    f"{step_id}: expected_columns 使用了伪时间维度 "
                    f"'{c.get('name')}'。ptweek()/ptyear()/ptmonth()/ptquarter() "
                    f"不是真实可查询列，请查询ptdate明细后用 extract_week/"
                    f"extract_year/extract_month 派生分组列（规则十四）。"
                )

    # Compute step checks
    if step_type == "compute":
        removed_functions = {"year_over_year", "period_over_period"}
        _arith_funcs = {"subtract", "divide", "add", "multiply"}

        for op in step.get("params", {}).get("operations", []):
            func_name = op.get("function", "")
            if func_name in removed_functions:
                logger.error(
                    f"[ValidateStep] {step_id}: removed function '{func_name}': "
                    f"{json.dumps(step, ensure_ascii=False)}"
                )
                raise StepDecompositionError(
                    f"{step_id}: function '{func_name}' 已从函数库删除。"
                    f"同比/环比必须使用 subtract + divide(multiply_by_100=true) 组合计算（规则十三）。"
                )

            if func_name in _arith_funcs:
                inputs = op.get("inputs", {}) or {}
                col_a, col_b = inputs.get("col_a"), inputs.get("col_b")
                if (isinstance(col_a, str) and col_a == col_b
                        and col_a.startswith("step_") and "." in col_a):
                    logger.error(
                        f"[ValidateStep] {step_id}: self-referential arithmetic "
                        f"col_a=col_b='{col_a}': {json.dumps(step, ensure_ascii=False)}"
                    )
                    raise StepDecompositionError(
                        f"{step_id}: operation '{op.get('output', '?')}' 的 "
                        f"col_a 与 col_b 引用了完全相同的列 '{col_a}'。自身减自身/除以"
                        f"自身没有意义。同比/环比计算必须让 col_a 引用本期列、col_b "
                        f"引用对比期列（如 join 后带 _right 后缀的列，或 shift 产生的 "
                        f"X_shift_N 列）（规则十三）。"
                    )


def _validate_decomposition(result: dict, user_query: str = "", database_meta: dict = None):
    """Validate the basic structure and column propagation of decomposition result."""
    if not isinstance(result, dict):
        raise StepDecompositionError("Decomposition result must be a dict")

    if "steps" not in result:
        raise StepDecompositionError("Missing 'steps' in decomposition result")

    steps = result["steps"]
    if not isinstance(steps, list) or len(steps) == 0:
        raise StepDecompositionError("'steps' must be a non-empty list")

    valid_types = {"query", "compute", "analyze", "summarize"}
    step_ids = set()

    # 治理索引全流程构建一次，供 B2 维度值校验与聚合语义校验复用
    gov_index = None
    if database_meta:
        from system_b.utils.meta_governance import build_governed_index, merge_database_meta
        gov_index = build_governed_index(merge_database_meta(database_meta))

    for step in steps:
        if "step_id" not in step:
            raise StepDecompositionError(f"Missing 'step_id' in step: {step}")
        if "step_type" not in step:
            raise StepDecompositionError(f"Missing 'step_type' in step: {step}")
        if step["step_type"] not in valid_types:
            raise StepDecompositionError(f"Invalid step_type: {step['step_type']}")
        if step["step_id"] in step_ids:
            raise StepDecompositionError(f"Duplicate step_id: {step['step_id']}")
        step_ids.add(step["step_id"])

        if database_meta:
            _validate_column_table_membership(step, database_meta)
            _validate_snapshot_marking(step, database_meta, gov_index)
            _validate_dimension_filter_values(step, database_meta, gov_index)

        # Validate depends_on references
        for dep in step.get("depends_on", []):
            if dep not in step_ids:
                # It may reference a later step (shouldn't happen but check at runtime)
                pass

    # =========================================================================
    # Enhanced validation: compute step operation data references
    # =========================================================================
    # Track which step_ids and operation outputs exist for reference validation
    known_outputs = set()  # "step_N" and "step_N.operation_name"
    for step in steps:
        step_id = step["step_id"]
        known_outputs.add(step_id)

        if step["step_type"] == "compute":
            params = step.get("params", {})
            operations = params.get("operations", [])
            for op in operations:
                output_name = op.get("output", "")
                if output_name:
                    known_outputs.add(f"{step_id}.{output_name}")

                # Validate input references
                inputs = op.get("inputs", {})
                for param_name, ref_value in inputs.items():
                    if isinstance(ref_value, str) and ref_value.startswith("step_"):
                        # Extract the step reference (e.g., "step_1" from "step_1.column")
                        parts = ref_value.split(".")
                        ref_step = parts[0]
                        if ref_step not in step_ids and ref_step != step_id:
                            logger.warning(
                                f"[Decompose] {step_id}: operation '{op.get('function', '?')}' "
                                f"references unknown step '{ref_step}' via param '{param_name}'"
                            )
                    elif isinstance(ref_value, list):
                        for item in ref_value:
                            if isinstance(item, str) and item.startswith("step_"):
                                ref_step = item.split(".")[0]
                                if ref_step not in step_ids and ref_step != step_id:
                                    logger.warning(
                                        f"[Decompose] {step_id}: operation '{op.get('function', '?')}' "
                                        f"references unknown step '{ref_step}' in list param '{param_name}'"
                                    )

    # =========================================================================
    # 问题5 fix: every query step must request at least one measure column.
    # A dimensions-only query (e.g. only ptdate + gdj) returns no metric data,
    # so every downstream computation silently becomes null.
    # =========================================================================
    for step in steps:
        if step["step_type"] != "query":
            continue
        params = step.get("params", {})
        expected_columns = params.get("expected_columns", []) or []
        roles = [c.get("role") for c in expected_columns if isinstance(c, dict)]
        if expected_columns and "measure" not in roles:
            raise StepDecompositionError(
                f"{step['step_id']}: query step has no measure column in "
                f"expected_columns (only dimensions: "
                f"{[c.get('name') for c in expected_columns if isinstance(c, dict)]}). "
                f"每个query步骤必须至少包含一个measure列（规则十九）。"
            )

    # =========================================================================
    # 问题5/问题8 fix: pseudo time dimensions (ptweek/ptyear/ptmonth/ptquarter)
    # are metadata placeholders, NOT queryable columns. Reject them in query
    # expected_columns so the LLM retries with ptdate + extract_* derivation.
    # =========================================================================
    _pseudo_dims = {"ptweek", "ptyear", "ptmonth", "ptquarter",
                    "ptweek()", "ptyear()", "ptmonth()", "ptquarter()"}
    for step in steps:
        if step["step_type"] != "query":
            continue
        params = step.get("params", {})
        for c in params.get("expected_columns", []) or []:
            if isinstance(c, dict) and str(c.get("name", "")).lower() in _pseudo_dims:
                raise StepDecompositionError(
                    f"{step['step_id']}: expected_columns 使用了伪时间维度 "
                    f"'{c.get('name')}'。ptweek()/ptyear()/ptmonth()/ptquarter() "
                    f"不是真实可查询列，请查询ptdate明细后用 extract_week/"
                    f"extract_year/extract_month 派生分组列（规则十四）。"
                )

    # =========================================================================
    # Enhanced validation: check for removed functions
    # =========================================================================
    removed_functions = {"year_over_year", "period_over_period"}
    for step in steps:
        if step["step_type"] == "compute":
            params = step.get("params", {})
            operations = params.get("operations", [])
            for op in operations:
                func_name = op.get("function", "")
                if func_name in removed_functions:
                    # 函数库梳理: these functions no longer exist in the
                    # registry, so executing the plan would crash. Fail
                    # validation so decompose_query retries with the rule.
                    raise StepDecompositionError(
                        f"{step['step_id']}: function '{func_name}' 已从函数库删除。"
                        f"同比/环比必须使用 subtract + divide(multiply_by_100=true) 组合计算（规则十三）。"
                    )

    # =========================================================================
    # 新问题3/新问题4 fix (7-18轮): reject self-referential arithmetic.
    # LLM plans emitted subtract/divide where col_a == col_b (the SAME step
    # reference on both sides, e.g. "step_3.joined_yoy.daypowersupply" minus
    # itself). X - X == 0 / X ÷ X == 1 is never a meaningful 同比/环比
    # computation, so fail validation and let the LLM retry with the rule.
    # (运行时 dispatcher 也有自减重写兜底，这里是第一道防线。)
    # =========================================================================
    _arith_funcs = {"subtract", "divide", "add", "multiply"}
    for step in steps:
        if step["step_type"] != "compute":
            continue
        for op in step.get("params", {}).get("operations", []):
            if op.get("function") not in _arith_funcs:
                continue
            inputs = op.get("inputs", {}) or {}
            col_a, col_b = inputs.get("col_a"), inputs.get("col_b")
            if (isinstance(col_a, str) and col_a == col_b
                    and col_a.startswith("step_") and "." in col_a):
                raise StepDecompositionError(
                    f"{step['step_id']}: operation '{op.get('output', '?')}' 的 "
                    f"col_a 与 col_b 引用了完全相同的列 '{col_a}'。自身减自身/除以"
                    f"自身没有意义。同比/环比计算必须让 col_a 引用本期列、col_b "
                    f"引用对比期列（如 join 后带 _right 后缀的列，或 shift 产生的 "
                    f"X_shift_N 列）（规则十三）。"
                )

    # =========================================================================
    # 新问题2 fix (7-18轮): a 同比 (year-over-year) question must query BOTH
    # the current period and the prior-year comparison period. Plans that
    # only query current-year data make every 同比 result null (shift
    # offset=12 on 7 rows) — 问题3 的提示词规则复发，这里加硬校验。
    # Heuristic: query text mentions 同比 → the union of query-step
    # time_range/filters must cover at least two distinct years.
    # =========================================================================
    if user_query and "同比" in user_query:
        years_seen = set()
        for step in steps:
            if step["step_type"] != "query":
                continue
            params_str = str(step.get("params", {}))
            years_seen.update(re.findall(r"(?:19|20)\d{2}", params_str))
        if len(years_seen) == 1:
            only_year = next(iter(years_seen))
            raise StepDecompositionError(
                f"用户问题包含'同比'，但所有query步骤只查询了 {only_year} 年的数据"
                f"（未查询 {int(only_year) - 1} 年对比期数据）。同比计算必须新增一个"
                f"query步骤查询上一年同期数据，再用 join/subtract/divide 计算差值和"
                f"百分比（规则十四）。注意：问题中要求同比的每一个指标都必须有对比期数据。"
            )

    # =========================================================================
    # 0723 问题4 fix: user asked for 月累计供电量/年累计供电量 (and their
    # 同环比) but the plan's query steps only fetched daypowersupply — the
    # cumulative metrics were silently dropped and the answer said "当前
    # 数据中未提供相关汇总信息". Hard-validate that when the question
    # explicitly names the cumulative metrics, at least one query step
    # fetches the corresponding column.
    # =========================================================================
    if user_query:
        _metric_requirements = [
            ("月累计", "monthpowersupply", "月累计供电量"),
            ("年累计", "yearpowersupply", "年累计供电量"),
        ]
        # 2026-09-15 服务器案例：view_gdl 等表元数据里没有 monthpowersupply，
        # 表描述明确"月累计=对当月每日日供电量求和"。此时 0723 硬校验强制模型
        # 查不存在的列 → 与列归属校验互相矛盾 → 3 次重试必败 500。
        # 三分支：列任何表都没有 → 放行 sum 日值；计划已查含该列的表 → 要求加列；
        # 计划只查了没有该列的表 → 明确要求换表（消除"0723要列↔归属校验拦列"拉锯）。
        _meta_col_index = None
        if database_meta:
            _meta_col_index = _build_table_column_index(database_meta)
        _all_query_cols = set()
        _queried_tables = set()
        for step in steps:
            if step["step_type"] != "query":
                continue
            _queried_tables.add(str(step.get("params", {}).get("expected_table", "")).lower())
            for c in step.get("params", {}).get("expected_columns", []) or []:
                if isinstance(c, dict):
                    _all_query_cols.add(str(c.get("name", "")).lower())
        _missing_metrics = []
        for kw, col, zh in _metric_requirements:
            if kw not in user_query or col in _all_query_cols:
                continue
            if _meta_col_index is not None:
                _tables_with_col = sorted(
                    t for t, cols in _meta_col_index.items() if col in cols
                )
                if not _tables_with_col:
                    logger.warning(
                        f"[Decompose] 月/年累计校验跳过：问题要求{zh}({col})，"
                        f"但元数据 {len(_meta_col_index)} 张表中均无该列"
                        f"（如 view_gdl 描述：月累计=日值求和），放行 sum 日值方案"
                    )
                    continue
            else:
                _tables_with_col = []
            _missing_metrics.append((kw, col, zh, _tables_with_col))
        if _missing_metrics and _all_query_cols:
            _parts = []
            for kw, col, zh, tables in _missing_metrics:
                if tables and not any(t.lower() in _queried_tables for t in tables):
                    _parts.append(
                        f"{zh}({col}) 仅存在于表 {'、'.join(tables)}，而当前所有query步骤"
                        f"查询的表（{'、'.join(sorted(_queried_tables))}）均无该列——必须把"
                        f"对应query步骤的expected_table改为上述含该列的表，并在"
                        f"expected_columns中加入{col}（role=measure）"
                    )
                elif tables:
                    _parts.append(
                        f"{zh}({col})：当前已查询含该列的表，请在对应query步骤的"
                        f"expected_columns中加入{col}（role=measure）"
                    )
                else:
                    _parts.append(f"{zh}({col})")
            raise StepDecompositionError(
                f"用户问题明确要求 {'；'.join(_parts)}。当前查询列: {sorted(_all_query_cols)}。"
                f"禁止在无该列的表上用日值sum冒充累计值（仅当所有表都无该列时才允许sum日值）；"
                f"若还要求同环比，比较期query步骤也必须包含同样的列，并对每一个指标分别计算"
                f"同环比（规则十四【强制-逐指标覆盖】【月/年累计口径】）。"
            )

    # =========================================================================
    # 2026-09-15 快照/时点存量指标误聚合硬校验：座数/户数/台数等存量列对多日
    # 数据直接 sum/mean 会把同一实体按天重复加总（0909 案例 1339万=三层加总、
    # 7天窗口直接sum座数翻倍）。此前防线全在提示词层，这里补硬校验。
    # =========================================================================
    _validate_aggregate_semantics(steps, database_meta, gov_index=gov_index)

    # =========================================================================
    # A4 同比区间等长硬校验：本期与对比期区间长度差 >7 天视为不等长
    # （如"年初至今"配了去年全年），同比结果必然错。此前仅提示词层。
    # =========================================================================
    _validate_compare_range_lengths(steps, user_query)

    # =========================================================================
    # C1 连续N月粒度硬校验：日粒度数据直接 consecutive_check 判断"连续N月"
    # 是错误口径，必须先按月聚合。
    # =========================================================================
    _validate_consecutive_granularity(steps, user_query)

    # Must have at least one summarize step
    has_summarize = any(s["step_type"] == "summarize" for s in steps)
    if not has_summarize:
        logger.warning("[Decompose] No summarize step found, appending one")
        # Auto-append a summarize step
        all_step_ids = [s["step_id"] for s in steps]
        steps.append({
            "step_id": f"step_{len(steps) + 1}",
            "step_type": "summarize",
            "description": "总结查询和计算结果",
            "depends_on": all_step_ids,
            "params": {"response_format": "text", "emphasis": "both"}
        })

    return True
