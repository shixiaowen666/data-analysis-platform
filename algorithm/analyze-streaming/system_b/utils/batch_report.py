# -*- coding: utf-8 -*-
"""
批量测试结果分析与报告
=====================
解析用户操作日志（logs/userlogs/{user_id}/{date}/问题N.log），复用
log_analysis_workflow.py 的三层规则判定逻辑，生成 JSON 报告 + Excel。

判定规则移植自 结果分析/log_analysis_workflow.py，保留其 4 条实战原理：
  原理1: "无法"关键词不能直接判错，需检查 answer 是否含有效数值+单位
  原理2: 增长率超100%不判错，仅 answer 自述异常时触发
  原理3: 绝对值字段不当百分比检测
  原理4: answer 异常关键词用组合短语正则精确匹配
"""

import json
import logging
import re
from pathlib import Path

logger = logging.getLogger(__name__)

TYPE_MAP = {'query': '查询', 'compute': '计算', 'analyze': '分析', 'summarize': '总结'}

# answer 中表示"无法回答"的强信号关键词（原理1：配合有效数据检测使用）
ABNORMAL_KEYWORDS = ['无法', '未能', '不支持', '没有数据', '暂无', '不可用', '无法提供', '无法确定']

# 原理4：组合短语精确匹配，不单独匹配"异常"
DATA_ANOMALY_PATTERNS = [
    r'增长率[^。]*?(?:严重异常|不可信|存在矛盾|计算方向反)',
    r'变化率[^。]*?(?:严重异常|不可信|存在矛盾|方向反)',
    r'数据存在严重异常',
    r'数据[^。]*?存在逻辑矛盾',
    r'同比增长[^。]*?(?:严重异常|不可信|方向反)',
    r'同比[^。]*?(?:下降超过100%|下降.*?100%以上|负.*?100%)',
    r'环比[^。]*?(?:下降超过100%|下降.*?100%以上|负.*?100%)',
]

DATE_KEYWORDS = [
    r'\d{4}年', r'\d{1,2}月\d{1,2}日', r'\d{1,2}月', r'\d{4}',
    '昨天', '今天', '明天', '前天', '当日', '近期', '最近', '本月', '上月', '下月',
    '今年', '去年', '明年', '上周', '本周', '下周', '近7天', '近30天', '近90天',
    '工作日', '周末', '节假日', '年初', '年末', '季度', '季度初',
    '周一', '周二', '周三', '周四', '周五', '周六', '周日',
    '星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期天', '星期日',
    '春节', '元旦', '国庆', '五一', '端午', '中秋', '清明',
]

# 通用兜底单位：日志执行结果缺 columns[].unit（旧格式/单位为null）时仍能识别常见量纲
FALLBACK_UNITS = [
    '元', '万元', '亿元', '%', '户', '人', '次', '台', '天', '吨', '度', '件',
    '笔', '辆', 'kWh', 'kW', 'MW', 'mw', 'MVA',
]


def _has_valid_data(answer: str, step_results: dict) -> bool:
    """有效数据判定：数字+单位。单位优先取执行结果 columns[].unit（随业务场景动态变化），
    兜底 FALLBACK_UNITS，避免硬编码单位列表导致跨场景误判。"""
    units = set(FALLBACK_UNITS)
    for sr in (step_results or {}).values():
        units.update(u for u in (sr.get('units') or []) if u)
    unit_alt = '|'.join(re.escape(u) for u in sorted(units, key=len, reverse=True))
    return bool(re.search(rf'[\d,.]+[万亿]?\s*(?:{unit_alt})', answer))


# ============================================================
# 日志读取
# ============================================================

def _read_log_text(logs_dir: str, rel_path: str) -> str:
    """按相对路径读取单个日志文件（相对 LOGS_DIR），失败返回空串。"""
    p = Path(logs_dir) / rel_path
    try:
        return p.read_text(encoding="utf-8")
    except Exception as exc:
        logger.warning(f"[BatchReport] 读取日志失败 {p}: {exc}")
    return ""


def _parse_user_date_from_path(rel_path: str) -> tuple:
    """从 log_path 提取 (user_id, log_date)。
    支持目录布局 userlogs/{user}/{date}/问题N.log 和旧单文件 userlogs/{user}/{date}.log。"""
    parts = rel_path.replace("\\", "/").split("/")
    if len(parts) >= 3:
        return parts[1], parts[2]
    if len(parts) == 2:
        return parts[0], Path(parts[1]).stem
    return "", ""


def list_day_log_paths(logs_dir: str, user_id: str, log_date: str) -> list:
    """列出某用户某天的全部日志相对路径。目录布局优先，旧单文件兜底。"""
    base = Path(logs_dir) / "userlogs" / str(user_id)
    day_dir = base / log_date
    if day_dir.is_dir():
        files = sorted(
            day_dir.glob("问题*.log"),
            key=lambda p: int(p.stem[2:]) if p.stem[2:].isdigit() else 0,
        )
        return [f"userlogs/{user_id}/{log_date}/{f.name}" for f in files]
    if (base / f"{log_date}.log").is_file():
        return [f"userlogs/{user_id}/{log_date}.log"]
    return []


# ============================================================
# 解析问题块
# ============================================================

def parse_question_blocks(content: str):
    """按「问题N：」分割全文，返回 [(qnum, seg), ...]；无标记时整体为一块。"""
    markers = [(m.start(), int(m.group(1))) for m in re.finditer(r'问题(\d+)：', content)]
    if not markers:
        return [(1, content)] if content.strip() else []

    blocks = []
    positions = [p for p, _ in markers] + [len(content)]
    for i, (pos, qnum) in enumerate(markers):
        seg = content[pos:positions[i + 1]]
        seg = re.sub(
            r'(2[、.]\s*执行提示词：)\s*.*?\n(===\s*用户问题\s*===)',
            r'\1\n\2', seg, flags=re.DOTALL,
        )
        blocks.append((qnum, seg))
    return blocks


# ============================================================
# 提取执行数据（v3: step_N-类型 分段；v2: steps 数组 + source_step）
# ============================================================

def _extract_v3_format(s3_content: str):
    type_map = {'查询': 'query', '计算': 'compute', '分析': 'analyze', '总结': 'summarize'}
    step_sections = list(re.finditer(r'\nstep_(\d+)-(\w+)\n', s3_content))
    if not step_sections:
        return [], {}, "", {}

    step_plans, step_results, answer, step_plan_json = [], {}, "", {}
    for i, m in enumerate(step_sections):
        sid, stype_cn = m.group(1), m.group(2)
        stype = type_map.get(stype_cn, stype_cn)
        start = m.end()
        end = step_sections[i + 1].start() if i + 1 < len(step_sections) else len(s3_content)
        step_seg = s3_content[start:end]

        plan_match = re.search(r'执行计划[：:]\s*(\{.*?)\n执行结果[：:]', step_seg, re.DOTALL)
        if plan_match:
            plan_raw = plan_match.group(1).strip()
            desc_m = re.search(r'"description"\s*:\s*"([^"]*)"', plan_raw)
            step_plans.append((sid, stype, desc_m.group(1) if desc_m else ""))
            step_plan_json[sid] = plan_raw[:2000]

        res_match = re.search(r'执行结果[：:]\s*(\{.*)', step_seg, re.DOTALL)
        if res_match:
            res_json = res_match.group(1)
            rc_m = re.search(r'"row_count"\s*:\s*(\d+)', res_json)
            row_count = int(rc_m.group(1)) if rc_m else None

            if stype == 'summarize':
                ans_m = re.search(r'"answer"\s*:\s*"(.*?)"\s*\}\s*\]', res_json, re.DOTALL)
                if ans_m:
                    answer = ans_m.group(1).replace('\\n', '\n')

            numeric_values = {}
            for col_m in re.finditer(r'"(\w+)"\s*:\s*([+-]?[\d.]+(?:[eE][+-]?\d+)?)', res_json):
                try:
                    numeric_values[col_m.group(1)] = float(col_m.group(2))
                except ValueError:
                    pass

            columns_units = re.findall(r'"unit"\s*:\s*"([^"]+)"', res_json)
            step_results[sid] = {
                'row_count': row_count, 'values': numeric_values,
                'units': columns_units, 'raw': res_json[:2000],
            }

    return step_plans, step_results, answer, step_plan_json


def _extract_v2_format(s3_content: str):
    step_plans = re.findall(
        r'"step_id"\s*:\s*"step_(\d+)"\s*,\s*"step_type"\s*:\s*"(\w+)"\s*,\s*"description"\s*:\s*"([^"]*)"',
        s3_content,
    )
    step_results = {}
    for m in re.finditer(
        r'"source_step"\s*:\s*"step_(\d+)"(.*?)(?="source_step"|"step_id"|"answer"|\Z)',
        s3_content, re.DOTALL,
    ):
        sid, block = m.group(1), m.group(2)
        rc_match = re.search(r'"row_count"\s*:\s*(\d+)', block)
        row_count = int(rc_match.group(1)) if rc_match else None
        numeric_values = {}
        for col_m in re.finditer(r'"(\w+)"\s*:\s*([+-]?[\d.]+(?:[eE][+-]?\d+)?)', block):
            try:
                numeric_values[col_m.group(1)] = float(col_m.group(2))
            except ValueError:
                pass
        step_results[sid] = {
            'row_count': row_count, 'values': numeric_values,
            'units': re.findall(r'"unit"\s*:\s*"([^"]+)"', block), 'raw': block[:2000],
        }

    answers = re.findall(r'"answer"\s*:\s*"([^"]*)"', s3_content)
    answer = answers[-1] if answers else ""
    return step_plans, step_results, answer


def extract_execution_data(seg: str) -> dict:
    """从单个问题块提取用户问题、step计划、row_count、answer 等。"""
    s1 = re.search(r'1[、.]\s*用户问题：(.+?)(?:\n|$)', seg)
    user_q = s1.group(1).strip() if s1 else ""

    s2 = re.search(r'2[、.]\s*执行提示词', seg)
    s3 = re.search(r'3[、.]\s*拆解过程', seg)
    s2_content = ""
    if s2 and s3:
        s2_content = seg[s2.end():s3.start()]
    elif s2:
        s2_content = seg[s2.end():]

    current_date = ""
    date_m = re.search(r'当前日期[：:]\s*(\d{4}-\d{1,2}-\d{1,2})', s2_content)
    if date_m:
        current_date = date_m.group(1)

    s3 = re.search(r'3[、.]\s*拆解过程', seg)
    s4 = re.search(r'4[、.]\s*错误描述', seg)
    if s3 and s4:
        s3_content = seg[s3.end():s4.start()]
    elif s3:
        s3_content = seg[s3.end():]
    else:
        s3_content = ""

    step_plans, step_results, answer, step_plan_json = _extract_v3_format(s3_content)
    if not step_plans:
        step_plans, step_results, answer = _extract_v2_format(s3_content)

    has_date_in_question = any(re.search(kw, user_q) for kw in DATE_KEYWORDS) if user_q else False

    return {
        'user_q': user_q,
        'step_plans': step_plans,
        'step_results': step_results,
        'answer': answer,
        'has_date_in_question': has_date_in_question,
        'current_date': current_date,
        'step_plan_json': step_plan_json,
    }


# ============================================================
# 三层判定：查询 → 计算 → 总结匹配
# ============================================================

def _check_query_results(step_plans, step_results):
    error_types, details, query_status = [], [], {}
    for sid, stype, sdesc in step_plans:
        if stype != 'query':
            continue
        sr = step_results.get(sid, {})
        rc = sr.get('row_count')
        type_cn = TYPE_MAP.get(stype, stype)
        if rc is None:
            query_status[sid] = {'status': 'none', 'rc': None}
            error_types.append("查询未执行")
            details.append(f"step_{sid}({type_cn}：{sdesc[:60]})无返回数据")
        elif rc == 0:
            query_status[sid] = {'status': 'zero', 'rc': 0}
            error_types.append("查询返回0行")
            if any(kw in sdesc for kw in ['去年', '2024', '2023', '2022']):
                reason = "数据库可能缺少该时段数据"
            elif any(kw in sdesc for kw in ['昨天', '今天', '当日']):
                reason = "目标日期数据可能尚未入库"
            elif any(kw in sdesc for kw in ['同比', '环比']):
                reason = "缺少历史对比数据"
            else:
                reason = "查询条件与数据库不匹配"
            details.append(f"step_{sid}({type_cn}：{sdesc[:60]})返回0行，{reason}")
        else:
            query_status[sid] = {'status': 'ok', 'rc': rc}
    return query_status, error_types, details


def _check_compute_results(step_plans, step_results):
    error_types, details, compute_status = [], [], {}
    for sid, stype, sdesc in step_plans:
        if stype != 'compute':
            continue
        sr = step_results.get(sid, {})
        rc = sr.get('row_count')
        type_cn = TYPE_MAP.get(stype, stype)
        if rc is None:
            compute_status[sid] = {'status': 'none', 'rc': None}
            error_types.append("计算未执行")
            details.append(f"step_{sid}({type_cn}：{sdesc[:60]})无返回数据")
        else:
            compute_status[sid] = {'status': 'ok', 'rc': rc}
    return compute_status, error_types, details


def _check_answer_match(data):
    step_plans = data['step_plans']
    step_results = data['step_results']
    answer = data['answer']
    has_date_in_question = data.get('has_date_in_question', False)
    current_date = data.get('current_date', '')

    error_types, details, match_info = [], [], []

    # 3.1 answer 为空
    if not answer:
        error_types.append("答案异常")
        details.append("Answer为空，summarize步骤未能生成有效总结")
        return {'has_answer': False}, error_types, details, match_info

    # 3.2 有效性（原理1：无有效数值+单位且含"无法"类表述才判错）
    has_valid_data = _has_valid_data(answer, step_results)
    if not has_valid_data:
        unable_snippets = []
        for kw in ABNORMAL_KEYWORDS:
            idx = answer.find(kw)
            if idx >= 0:
                sent_start = max(0, answer.rfind('。', 0, idx) + 1, answer.rfind('；', 0, idx) + 1, idx - 30)
                sent_end = min(len(answer), answer.find('。', idx), answer.find('；', idx), idx + 60)
                if sent_end <= idx:
                    sent_end = idx + 60
                unable_snippets.append(answer[sent_start:sent_end].strip())
        if unable_snippets:
            error_types.append("答案异常")
            details.append('Answer表示无法回答：' + '；'.join(unable_snippets[:3]))
        else:
            error_types.append("答案异常")
            details.append("Answer未包含有效数据")
        match_info.append("Answer无有效数据")
    else:
        match_info.append("Answer含有效数据")

    # 3.3 answer 自述异常（原理2/4）
    anomaly_matched = False
    for pattern in DATA_ANOMALY_PATTERNS:
        m = re.search(pattern, answer)
        if m:
            idx = m.start()
            sent_start = max(0, answer.rfind('。', 0, idx) + 1, idx - 30)
            sent_end = min(len(answer), answer.find('。', idx + 1), idx + 80)
            if sent_end <= idx:
                sent_end = idx + 80
            if not anomaly_matched:
                error_types.append("答案异常")
            details.append('Answer自述数据异常：' + answer[sent_start:sent_end].strip())
            anomaly_matched = True
            break

    # 3.4 None 检测
    if 'None' in answer or 'null' in answer.lower():
        if "结果含None" not in error_types:
            error_types.append("结果含None")
        none_fields = re.findall(r'(\w+)\s*[是为]\s*[Nn]one', answer)
        if none_fields:
            details.append(f"以下字段为None：{'、'.join(none_fields[:5])}")
        else:
            details.append("Answer中包含None值")

    # 3.5 数值字段全为0
    for sid, sr in step_results.items():
        values = sr.get('values', {})
        if not values:
            continue
        zero_count = sum(1 for v in values.values() if v == 0.0)
        if zero_count > 0 and sr.get('row_count'):
            zero_ratio = zero_count / len(values)
            if zero_ratio >= 0.8 and sr['row_count'] > 0:
                error_types.append("答案异常")
                details.append(
                    f"step_{sid}返回{sr['row_count']}行，"
                    f"但{zero_count}/{len(values)}个数值字段为0.0，可能数据查询条件不匹配"
                )
                break

    # 3.6 时间维度匹配
    answer_has_year = bool(re.search(r'\d{4}年', answer))
    answer_has_date = bool(re.search(r'\d{1,2}月\d{1,2}日', answer))
    if has_date_in_question:
        if answer_has_year or answer_has_date:
            match_info.append("问题含日期，Answer中也含日期 ✓")
        else:
            match_info.append("【注意】问题含日期，但Answer中未出现具体日期")
    elif current_date:
        current_year = current_date.split('-')[0]
        if answer_has_year:
            answer_years = re.findall(r'(\d{4})年', answer)
            if answer_years and all(y != current_year for y in answer_years):
                error_types.append("时间不匹配")
                details.append(
                    f"总结时间与问题不匹配：问题未指定日期，但总结使用了{answer_years[0]}年，"
                    f"与当前日期{current_date}不一致"
                )
                match_info.append(f"问题无日期，但Answer中使用了{answer_years[0]}年 ✗")
            else:
                match_info.append(f"问题无指定日期，Answer使用{current_year}年 ✓")
        else:
            for rel_kw in ['昨天', '今天', '前天', '最近', '本周', '本月', '今年']:
                if rel_kw in answer:
                    match_info.append(f"问题无日期，Answer含相对日期'{rel_kw}' ✓")
                    break
            else:
                match_info.append("问题无日期，Answer中无明确日期信息")
    else:
        match_info.append("未提取到当前日期，跳过时间匹配检查")

    if any(kw in answer for kw in ABNORMAL_KEYWORDS) and has_valid_data:
        match_info.append("Answer含'无法'说明但给出了有效数据")

    return {'has_answer': bool(answer), 'has_valid_data': has_valid_data}, error_types, details, match_info


def judge_errors(data: dict) -> dict:
    query_status, query_errors, query_details = _check_query_results(data['step_plans'], data['step_results'])
    compute_status, compute_errors, compute_details = _check_compute_results(data['step_plans'], data['step_results'])
    answer_info, answer_errors, answer_details, match_info = _check_answer_match(data)

    all_error_types = query_errors + compute_errors + answer_errors
    all_details = query_details + compute_details + answer_details

    seen, unique_types = set(), []
    for t in all_error_types:
        if t not in seen:
            seen.add(t)
            unique_types.append(t)

    detail_parts = ["【排查步骤】"]
    for status_map, target_type in ((query_status, 'query'), (compute_status, 'compute')):
        for sid, stype, sdesc in data['step_plans']:
            if stype != target_type:
                continue
            type_cn = TYPE_MAP.get(stype, stype)
            info = status_map.get(sid, {})
            status, rc = info.get('status', ''), info.get('rc', '?')
            mark = {'ok': '✓', 'zero': '✗', 'none': '✗'}.get(status, '')
            rc_text = f"返回{rc}行" if status == 'ok' else ("返回0行" if status == 'zero' else "无返回数据")
            detail_parts.append(f"  step_{sid}({type_cn}：{sdesc[:60]}){rc_text} {mark}".rstrip())

    has_summarize = any(stype == 'summarize' for _, stype, _ in data['step_plans'])
    if has_summarize and data.get('answer'):
        detail_parts.append("  summarize输出完整 ✓")
    elif has_summarize:
        detail_parts.append("  summarize输出为空 ✗")

    for d in all_details:
        if d:
            detail_parts.append(f"  【异常】{d}")

    detail_parts.append("【匹配检查】")
    detail_parts.extend(f"  {m}" for m in match_info) if match_info else detail_parts.append("  无匹配信息")

    if data.get('current_date'):
        date_status = "含日期" if data.get('has_date_in_question') else "无日期"
        detail_parts.append(f"  问题{date_status}，当前日期{data['current_date']}")

    if unique_types:
        detail_parts.append(f"【结论】错误（{'；'.join(unique_types)}）")
        is_correct = "错误"
    else:
        detail_parts.append("【结论】正确")
        is_correct = "正确"

    return {
        'error_type': '；'.join(unique_types) if unique_types else "无",
        'error_detail': '\n'.join(detail_parts),
        'is_correct': is_correct,
    }


# ============================================================
# 报告组装
# ============================================================

def analyze_logs(logs_dir: str, user_id: str, log_date: str) -> dict:
    """解析指定用户指定日期的全部问题日志，返回报告 JSON。"""
    paths = list_day_log_paths(logs_dir, user_id, log_date)
    if not paths:
        return {'error': f'未找到日志：logs/userlogs/{user_id}/{log_date}/问题N.log'}
    return analyze_log_paths(logs_dir, paths, user_id=user_id, log_date=log_date)


def analyze_log_paths(logs_dir: str, log_paths: list, user_id: str = "", log_date: str = "") -> dict:
    """按日志路径列表逐文件解析生成报告，每个文件对应一条记录（题号按列表顺序）。"""
    rows = []
    qnum = 0
    for rel in log_paths:
        rel = str(rel or "").strip().replace("\\", "/")
        if not rel:
            continue
        content = _read_log_text(logs_dir, rel)
        if not content.strip():
            continue
        blocks = parse_question_blocks(content) or [(1, content)]
        for _, seg in blocks:
            qnum += 1
            data = extract_execution_data(seg)
            judgment = judge_errors(data)
            row = _build_row(qnum, data, judgment)
            row['log_path'] = rel
            rows.append(row)

    if not user_id or not log_date:
        for rel in log_paths:
            u, d = _parse_user_date_from_path(str(rel))
            user_id = user_id or u
            log_date = log_date or d
            if user_id and log_date:
                break
    return _assemble_report(rows, user_id, log_date)


def _build_missing_row(qnum: int, question: str, reason: str) -> dict:
    """日志缺失/空文件时的占位行：行结构与正常行同构（前端/Excel 均可渲染），
    序号用前端显式 qnum，不跳过、不错位，缺失原因明确标注。"""
    return {
        'num': qnum,
        'user_q': question,
        'steps': '',
        'error_type': '日志缺失',
        'error_detail': f"【排查步骤】\n  无法读取该题日志（{reason}）。\n【结论】错误（日志缺失）",
        'verify': f"日志缺失：{reason}",
        'answer': '',
        'correct': '错误',
        'step_outputs': [],
        'log_path': '',
    }


def analyze_log_items(logs_dir: str, items: list, user_id: str = "", log_date: str = "") -> dict:
    """按前端显式题号(items)逐条解析生成报告。

    items: [{"qnum": int, "log_path": str|"", "question": str}, ...]
    报告第 N 行永远 = items 第 N 条，序号取 item.qnum（不依赖文件序号、不按列表顺序自增），
    log_path 缺失或文件为空时生成占位行，避免丢题/序号漂移。日志被拼接时优先取
    「用户问题」与 item.question 精确匹配的块，其余块自动忽略。
    """
    rows = []
    for item in items:
        qnum = int(item.get('qnum') or 0)
        question = str(item.get('question') or "").strip()
        rel = str(item.get('log_path') or "").strip().replace("\\", "/")

        if not rel:
            rows.append(_build_missing_row(qnum, question, "响应未返回日志路径"))
            continue
        content = _read_log_text(logs_dir, rel)
        if not content.strip():
            rows.append(_build_missing_row(qnum, question, f"日志文件缺失或为空：{rel}"))
            continue

        blocks = parse_question_blocks(content) or [(1, content)]
        target_seg = None
        if question:
            for _, seg in blocks:
                s1 = re.search(r'1[、.]\s*用户问题：(.+?)(?:\n|$)', seg)
                if s1 and s1.group(1).strip() == question:
                    target_seg = seg
                    break
        if target_seg is None:
            target_seg = blocks[0][1]

        data = extract_execution_data(target_seg)
        judgment = judge_errors(data)
        row = _build_row(qnum, data, judgment)
        row['log_path'] = rel
        rows.append(row)

    if not user_id or not log_date:
        for item in items:
            u, d = _parse_user_date_from_path(str(item.get('log_path') or ""))
            user_id = user_id or u
            log_date = log_date or d
            if user_id and log_date:
                break
    return _assemble_report(rows, user_id, log_date)


def _assemble_report(rows: list, user_id: str, log_date: str) -> dict:
    total = len(rows)
    correct = sum(1 for r in rows if r['correct'] == '正确')
    error_rows = [r for r in rows if r['correct'] == '错误']
    error_type_counter = {}
    for r in error_rows:
        for t in r['error_type'].split('；'):
            if t and t != '无':
                error_type_counter[t] = error_type_counter.get(t, 0) + 1

    return {
        'user_id': user_id,
        'log_date': log_date,
        'total': total,
        'correct': correct,
        'errors': total - correct,
        'pass_rate': round(correct * 100.0 / total, 1) if total else 0.0,
        'error_type_stats': [
            {'type': t, 'count': c} for t, c in
            sorted(error_type_counter.items(), key=lambda x: -x[1])
        ],
        'rows': rows,
    }


def _build_row(qnum: int, data: dict, judgment: dict) -> dict:
    parts = []
    for sid, stype, sdesc in data['step_plans']:
        cn = TYPE_MAP.get(stype, stype)
        parts.append(f"step_{sid}{cn}（{sdesc[:35]}）")
    steps_summary = '、'.join(parts)

    verify_parts = []
    for label, target in (('查询', 'query'), ('计算', 'compute')):
        items = [(sid, stype, sdesc) for sid, stype, sdesc in data['step_plans'] if stype == target]
        if items:
            infos = []
            for sid, stype, sdesc in items:
                sr = data['step_results'].get(sid, {})
                rc = sr.get('row_count')
                if rc is not None and rc > 0:
                    infos.append(f"step_{sid}{label}返回{rc}行 ✓")
                elif rc is not None and rc == 0:
                    infos.append(f"step_{sid}{label}返回0行 ✗")
                else:
                    infos.append(f"step_{sid}{label}无返回 ✗")
            verify_parts.append(f"【步骤{'查询' if target == 'query' else '计算'}-{label}】" + " / ".join(infos))

    summary_parts = []
    if data['answer']:
        summary_parts.append(f"Answer: {data['answer'][:500]}")
    summary_parts.append(f"问题含日期:{['否', '是'][data['has_date_in_question']]}")
    summary_parts.append(f"当前日期:{data['current_date']}")
    if data['answer']:
        has_valid_data = _has_valid_data(data['answer'], data['step_results'])
        summary_parts.append(f"含有效数据:{['否', '是'][has_valid_data]}")
    if summary_parts:
        verify_parts.append("【步骤3-总结】" + " | ".join(summary_parts))

    return {
        'num': qnum,
        'user_q': data['user_q'],
        'steps': steps_summary,
        'error_type': judgment['error_type'],
        'error_detail': judgment['error_detail'],
        'verify': "\n".join(verify_parts),
        'answer': data['answer'][:1000],
        'correct': judgment['is_correct'],
        'step_outputs': [
            {
                'step_id': f'step_{sid}',
                'type': TYPE_MAP.get(stype, stype),
                'description': sdesc,
                'plan_json': (data.get('step_plan_json') or {}).get(sid, ''),
                'result_json': (data['step_results'].get(sid) or {}).get('raw', ''),
            }
            for sid, stype, sdesc in data['step_plans']
        ],
    }


# ============================================================
# Excel 导出（复用原工作流样式：错误行红色标注）
# ============================================================

EXCEL_HEADERS = ['序号', '用户问题', '执行步骤简述', '错误类型', '错误详情', '数据结果验证', '最终回答', '步骤JSON', '是否通过']
EXCEL_COL_WIDTHS = [6, 45, 60, 25, 50, 55, 55, 60, 10]


def export_excel(report: dict, output_path: str) -> str:
    try:
        import openpyxl
        from openpyxl.utils import get_column_letter
        from openpyxl.styles import Font, Alignment, PatternFill, Border, Side
    except ImportError:
        raise RuntimeError("需要安装 openpyxl: pip install openpyxl")

    thin_border = Border(
        left=Side(style='thin'), right=Side(style='thin'),
        top=Side(style='thin'), bottom=Side(style='thin'),
    )
    header_fill = PatternFill(start_color='4472C4', end_color='4472C4', fill_type='solid')
    header_font = Font(name='微软雅黑', size=10, bold=True, color='FFFFFF')
    cell_font = Font(name='微软雅黑', size=9)
    error_fill = PatternFill(start_color='FFC7CE', end_color='FFC7CE', fill_type='solid')
    wrap_align = Alignment(wrap_text=True, vertical='top')

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = '批量测试报告'

    for ci, h in enumerate(EXCEL_HEADERS):
        cell = ws.cell(row=1, column=ci + 1, value=h)
        cell.font = header_font
        cell.fill = header_fill
        cell.alignment = Alignment(horizontal='center', vertical='center', wrap_text=True)
        cell.border = thin_border

    def _format_step_json(item):
        parts = []
        for so in item.get('step_outputs', []):
            chunk = f"── {so['step_id']} {so['type']} ──"
            if so.get('plan_json'):
                chunk += f"\n【执行计划】{so['plan_json']}"
            if so.get('result_json'):
                chunk += f"\n【执行结果】{so['result_json']}"
            parts.append(chunk)
        return '\n\n'.join(parts)

    for ri, item in enumerate(report['rows'], 2):
        values = [item['num'], item['user_q'], item['steps'], item['error_type'],
                  item['error_detail'], item['verify'], item['answer'],
                  _format_step_json(item), item['correct']]
        failed = item['correct'] == '错误'
        for ci, val in enumerate(values):
            cell = ws.cell(row=ri, column=ci + 1, value=val)
            cell.font = cell_font
            cell.alignment = wrap_align
            cell.border = thin_border
            if failed:
                cell.fill = error_fill

    for ci, w in enumerate(EXCEL_COL_WIDTHS):
        ws.column_dimensions[get_column_letter(ci + 1)].width = w
    ws.freeze_panes = 'A2'

    # 统计sheet
    ws2 = wb.create_sheet('统计')
    stats = [
        ('用户', report['user_id']),
        ('测试日期', report['log_date']),
        ('总题数', report['total']),
        ('通过', report['correct']),
        ('未通过', report['errors']),
        ('通过率', f"{report['pass_rate']}%"),
    ]
    for ri, (k, v) in enumerate(stats, 1):
        ws2.cell(row=ri, column=1, value=k).font = Font(name='微软雅黑', size=10, bold=True)
        ws2.cell(row=ri, column=2, value=v).font = Font(name='微软雅黑', size=10)
    if report.get('error_type_stats'):
        base = len(stats) + 2
        ws2.cell(row=base, column=1, value='错误类型').font = Font(name='微软雅黑', size=10, bold=True)
        ws2.cell(row=base, column=2, value='次数').font = Font(name='微软雅黑', size=10, bold=True)
        for i, item in enumerate(report['error_type_stats'], 1):
            ws2.cell(row=base + i, column=1, value=item['type']).font = Font(name='微软雅黑', size=9)
            ws2.cell(row=base + i, column=2, value=item['count']).font = Font(name='微软雅黑', size=9)
    ws2.column_dimensions['A'].width = 16
    ws2.column_dimensions['B'].width = 24

    wb.save(output_path)
    return output_path
