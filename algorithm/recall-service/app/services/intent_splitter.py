"""
意图拆分器：复合查询 → 多个子查询（供召回侧多向量 max 融合）

动机（批量未命中 #205）：「深圳互感器总数、表箱数、线路条数」这类多实体并列问题，
整体单向量被代表意图支配，非代表意图在同表指标 topK 竞争中被挤出。

策略：复用 query_rewriter 的同义词词典（指标/维度标准名+别名）做最长匹配锚定，
锚点（≥2 个）之间的并列关系即拆分边界：
  - 公共前缀（时间/地域等限定语）保留到每个子查询
  - 尾段（如"的总量/有多少"）保留到每个子查询
  - 锚点间纯连接符（、和与等）丢弃；含实义的中间段归属后一锚点
拆分只影响召回向量，judge 输入仍用原始问题。
"""
import re
from typing import Dict, List, Tuple

# 锚点间纯连接符：顿号/逗号/连词/助词，出现即视为无实义分隔
_SEP_RE = re.compile(r"^[、，,。；;：:\s和与及跟同以及还有的]+$")
# 单个分隔符（用于在含实义中间段中定位切分点）
_SEP_PART = re.compile(r"[、，,。；;：:]")
# 问句边界：问号/句号/分号/换行，句即独立意图，先切句再句内拆分
_SENT_RE = re.compile(r"[？！。?!;；\n]+")


def _find_anchors(query: str, dictionary: Dict[str, str]) -> List[Tuple[int, int]]:
    """词典最长匹配锚定，返回互不重叠的 (start, end) 区间（按位置升序）。
    长词优先占位：如「网供电量」整体锚定后，内部的「供电量」不再成为锚点。"""
    spans: List[Tuple[int, int]] = []
    lower = query.lower()
    for term in sorted(dictionary, key=len, reverse=True):
        if len(term) < 2:
            continue
        start = 0
        while True:
            idx = lower.find(term, start)
            if idx < 0:
                break
            end = idx + len(term)
            if all(end <= s or idx >= e for s, e in spans):
                spans.append((idx, end))
            start = end
    return sorted(spans)


def split_intents(query: str, dictionary: Dict[str, str]) -> List[str]:
    """返回子查询列表；锚点 <2 时返回 [query]（不拆分）。
    先按问句边界（？！。;）切句——每句是独立意图，句内再做多锚点拆分，
    避免「A是多少？B是多少？」三连问被搅成跨句垃圾子查询。"""
    if not query or not dictionary:
        return [query]
    sentences = [s for s in _SENT_RE.split(query) if s.strip()]
    if len(sentences) > 1:
        out: List[str] = []
        seen: set = set()
        for raw in sentences:
            # 行首"的"来自时间词（昨天/上月）替换后的残渣，一并清理
            sent = raw.strip(" 、，,。；;：:的")
            if not sent:
                continue
            for t in _split_one(sent, dictionary):
                if t not in seen:
                    seen.add(t)
                    out.append(t)
        return out if out else [query]
    return _split_one(query, dictionary)


def _split_one(sentence: str, dictionary: Dict[str, str]) -> List[str]:
    """单句内的锚定拆分（原 split_intents 主体）。"""
    spans = _find_anchors(sentence, dictionary)
    # 相邻锚点（中间零字符）合并为一个实体：如「深圳+供电量」实为「深圳供电量」
    merged: List[Tuple[int, int]] = []
    for s, e in spans:
        if merged and merged[-1][1] == s:
            merged[-1] = (merged[-1][0], e)
        else:
            merged.append((s, e))
    spans = merged
    if len(spans) < 2:
        return [sentence]

    prefix = sentence[:spans[0][0]]
    tail = sentence[spans[-1][1]:]
    # 尾段以分隔符开头（如「数、线路条数」）= 最后锚点的并列项，只归最后一个子查询；
    # 否则是共享谓语（如「是多少」），挂到所有子查询
    if re.match(r"^[、，,。；;：:\s]", tail):
        tail_common, tail_last = "", tail
    else:
        tail_common = tail_last = tail

    # 修饰语（如「互感器【总数】、表箱数」）归属：默认归后一锚点（「A的B」型）；
    # 中间段含分隔符时在最后一个分隔符处切开，前段（修饰语）归前一锚点
    subs: List[str] = []
    carry = ""
    n = len(spans)
    for i, (s, e) in enumerate(spans):
        seg = prefix + carry + sentence[s:e] + tail_common
        subs.append(seg.strip().rstrip("、，,。；;：: ") or sentence[s:e])
        if i < n - 1:
            middle = sentence[e:spans[i + 1][0]]
            if _SEP_RE.match(middle):
                carry = ""
            else:
                seps = [m.end() for m in _SEP_PART.finditer(middle)]
                if seps:
                    subs[-1] = (prefix + carry + sentence[s:e] + middle[:seps[-1]] + tail_common).strip().rstrip("、，,。；;：: ")
                    carry = middle[seps[-1]:]
                else:
                    carry = middle

    # 并列尾段只补到最后一个子查询（如「深圳互感器总数、表箱数」中的「表箱数」行）
    if tail_last and tail_last != tail_common and subs:
        subs[-1] = (subs[-1] + tail_last).strip().rstrip("、，,。；;：: ")

    # 去重、去空、去与原句相同者；压缩连续分隔符（如"总数、、线路"）
    _dup_sep = re.compile(r"[、，,]{2,}")
    out, seen = [], set()
    for t in subs:
        t = _dup_sep.sub("、", t).strip().rstrip("、，,。；;：: ")
        if t and t not in seen and t != sentence:
            seen.add(t)
            out.append(t)
    return out if out else [sentence]
