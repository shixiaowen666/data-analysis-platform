package com.quality.engine;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.quality.dto.ChangeDTO;
import com.quality.entity.ChatAnalysisTrace;
import com.quality.entity.ChatErrorDiagnosis;
import com.quality.entity.ChatStepTrace;
import com.quality.entity.TuningRule;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 调优建议规则引擎（设计文档 03 §5.1 R1–R12）。
 * <p>
 * 输入：诊断结论 + trace + step traces + 资产快照（表描述 / 实体别名 / agg_type / 当前提示词版本 / 智能体绑定表）；
 * 输出：建议变更列表（不落库），管理员可勾选 / 修改 / 追加。
 * <p>
 * 纯函数、无 IO，便于单测；资产现值由 {@link AssetContext} 提供。
 */
public final class SuggestionEngine {

    private SuggestionEngine() {
    }

    /** 建议生成所需的资产现值（由 service 层查库填充；缺失字段按 null 处理） */
    public static final class AssetContext {
        /** 表名 → {id, note} */
        public Map<String, JSONObject> tables = new HashMap<>();
        /** 实体 english_name → {id, alias, chineseName, category} */
        public Map<String, JSONObject> entities = new HashMap<>();
        /** 表名.列名 → {tableId, basicId, summary} */
        public Map<String, JSONObject> columnAgg = new HashMap<>();
        /** 智能体绑定的表名集合 */
        public Set<String> agentTables = new HashSet<>();
        /** 智能体 id */
        public String aiBodyId;
        /** prompt-main 当前激活版本 */
        public String promptMainVersion;
        /** prompt-summary 当前激活版本 */
        public String promptSummaryVersion;
        /** recall table 路径 top_k 现值 */
        public Integer recallTopK;
    }

    public static List<ChangeDTO> suggest(ChatErrorDiagnosis d, ChatAnalysisTrace t, List<ChatStepTrace> steps,
                                          List<TuningRule> rules, AssetContext ctx) {
        List<ChangeDTO> out = new ArrayList<>();
        if (d == null) return out;
        Map<String, TuningRule> ruleMap = rules == null ? Collections.emptyMap()
                : rules.stream().filter(r -> r.getEnabled() == null || r.getEnabled() == 1)
                .collect(Collectors.toMap(TuningRule::getRuleCode, r -> r, (a, b) -> a));
        String type = StringUtils.defaultString(d.getPrimaryErrorType());
        String root = StringUtils.defaultString(d.getRootCause());
        String fix = StringUtils.defaultString(d.getFixActionType());
        List<String> recallTables = parseArr(t == null ? null : t.getRecallTables());
        List<String> usedTables = parseArr(t == null ? null : t.getUsedTables());
        String actualTable = StringUtils.defaultIfBlank(d.getActualTable(), usedTables.isEmpty() ? "" : usedTables.get(0));
        String question = t == null ? "" : StringUtils.defaultString(t.getQuestion());
        Set<String> secondary = new HashSet<>(parseArr(d.getSecondaryErrorTypes()));

        // ---------------- 选表 ----------------
        if (type.startsWith("TABLE_SELECT") || secondary.contains("TABLE_SELECT")) {
            String expected = d.getExpectedTable();
            if (StringUtils.isNotBlank(expected)) {
                JSONObject tbl = ctx.tables.get(expected);
                String before = tbl == null ? "" : StringUtils.defaultString(tbl.getString("note"));
                List<String> kws = keywordsNotIn(question, before);
                if (kws.isEmpty()) kws = keywordsNotIn(question, ""); // 描述已含问题关键词时仍给出候选，由管理员裁剪
                if (!recallTables.isEmpty() && !recallTables.contains(expected)) {
                    // R1 应选表未被召回
                    out.add(change(ruleMap.get("R1"), "TABLE_DESC", "BI_MANAGER", idOf(tbl), expected, "note", before,
                            appendKeywords(before, kws), "追加关键词「" + String.join("、", kws) + "」到表描述",
                            "应选表 " + expected + " 未出现在召回结果中", "HIGH"));
                } else if (StringUtils.isNotBlank(actualTable) && !actualTable.equals(expected)) {
                    // R2 已召回但模型未选
                    JSONObject actual = ctx.tables.get(actualTable);
                    String actualNote = actual == null ? "" : StringUtils.defaultString(actual.getString("note"));
                    String after = before + (before.endsWith("；") || before.isEmpty() ? "" : "；")
                            + "（与 " + actualTable + " 的区别：" + (kws.isEmpty() ? "按问题口径区分" : "支持按" + String.join("/", kws) + "查询") + "）";
                    out.add(change(ruleMap.get("R2"), "TABLE_DESC", "BI_MANAGER", idOf(tbl), expected, "note", before, after,
                            "为应选表增加区分性描述", "应选表已召回但模型选择了 " + actualTable, "HIGH"));
                    out.add(change(ruleMap.get("R2"), "KNOWLEDGE", "BI_MANAGER", ctx.aiBodyId, "智能体知识库", "knowledge_element", "",
                            "区分规则：" + expected + " 用于「" + String.join("/", kws.isEmpty() ? List.of("本问题口径") : kws) + "」；"
                                    + actualTable + " 用于「" + abbreviate(actualNote, 40) + "」",
                            "新增一条表区分知识", "帮助模型区分两张相似表", "MEDIUM"));
                }
                if (!ctx.agentTables.isEmpty() && !ctx.agentTables.contains(expected)) {
                    // R3 未绑定到智能体
                    out.add(change(ruleMap.get("R3"), "AGENT_TABLE", "BI_MANAGER", idOf(tbl), expected, "relation", "未绑定",
                            "绑定", "智能体绑定表 " + expected, "应选表不在智能体绑定表内", "HIGH"));
                }
                if (recallTables.size() > 0 && recallTables.size() <= 2 && !recallTables.contains(expected)) {
                    // R4 召回过少
                    int cur = ctx.recallTopK == null ? 10 : ctx.recallTopK;
                    int after = Math.min(20, (int) Math.ceil(cur * 1.5));
                    out.add(change(ruleMap.get("R4"), "RECALL_CONFIG", "RECALL", "table.top_k", "召回 table 路径 top_k", "top_k",
                            String.valueOf(cur), String.valueOf(after), "top_k " + cur + " → " + after, "召回结果仅 " + recallTables.size() + " 张表", "MEDIUM"));
                }
            }
        }

        // ---------------- 指标 / 维度 ----------------
        if (type.startsWith("METRIC_DIM") || secondary.stream().anyMatch(s -> s.startsWith("METRIC_DIM"))) {
            List<JSONObject> ents = parseObjArr(d.getExpectedEntities());
            for (JSONObject e : ents) {
                String code = e.getString("code");
                if (StringUtils.isBlank(code)) continue;
                boolean isDim = "dim".equalsIgnoreCase(e.getString("type"));
                JSONObject ent = ctx.entities.get(code);
                String before = ent == null ? "" : StringUtils.defaultString(ent.getString("alias"));
                String label = (ent == null ? code : code + " " + StringUtils.defaultString(ent.getString("chineseName")));
                List<String> kws = new ArrayList<>();
                if (StringUtils.isNotBlank(e.getString("alias"))) kws.add(e.getString("alias"));
                kws.addAll(keywordsNotIn(question, before + "," + label));
                if (kws.isEmpty()) kws.add(StringUtils.defaultString(e.getString("name"), code));
                String after = appendAlias(before, kws);
                out.add(change(ruleMap.get(isDim ? "R6" : "R5"), "ENTITY_ALIAS", "BI_MANAGER", idOf(ent), label, "alias", before, after,
                        "追加别名「" + String.join(", ", kws) + "」", (isDim ? "应选维度 " : "应选指标 ") + code + " 未被识别", "HIGH"));
                if (isDim && StringUtils.isNotBlank(e.getString("value"))) {
                    out.add(change(ruleMap.get("R6"), "DIM_VALUES", "BI_MANAGER", idOf(ent), label, "dim_value", "",
                            e.getString("value"), "补充枚举值「" + e.getString("value") + "」", "filter 值未命中枚举", "MEDIUM"));
                }
            }
            // R7 unmatched columns
            for (ChatStepTrace s : steps == null ? Collections.<ChatStepTrace>emptyList() : steps) {
                for (String col : parseArr(s.getUnmatchedColumns())) {
                    String nearest = nearestEntity(col, ctx.entities.keySet());
                    JSONObject ent = nearest == null ? null : ctx.entities.get(nearest);
                    String before = ent == null ? "" : StringUtils.defaultString(ent.getString("alias"));
                    out.add(change(ruleMap.get("R7"), "ENTITY_ALIAS", "BI_MANAGER", idOf(ent),
                            nearest == null ? "（需人工指定实体）" : nearest, "alias", before, appendAlias(before, List.of(col)),
                            "把未映射列名「" + col + "」作为别名", s.getStepId() + " 存在未映射列 " + col, nearest == null ? "LOW" : "MEDIUM"));
                }
            }
        }

        // ---------------- 拆分 ----------------
        if (type.startsWith("DECOMPOSE") || secondary.contains("DECOMPOSE")) {
            String agg = aggFromRoot(root);
            if (agg != null || "AGG_TYPE".equals(fix)) {
                // R8 口径类
                String target = StringUtils.defaultIfBlank(d.getFixActionDetail(), firstMetric(d));
                JSONObject col = target == null ? null : ctx.columnAgg.get(target);
                String before = col == null ? "" : StringUtils.defaultString(col.getString("summary"));
                out.add(change(ruleMap.get("R8"), "AGG_TYPE", "BI_MANAGER", col == null ? null : col.getString("basicId"),
                        StringUtils.defaultString(target, "（需指定列）"), "summary", before, StringUtils.defaultString(agg, "snapshot"),
                        "聚合方式 " + before + " → " + agg, "根因提到口径：" + abbreviate(root, 60), "HIGH"));
            }
            if (agg == null || "PROMPT_DECOMPOSE".equals(fix)) {
                // R9 规则类：打开编辑器
                out.add(change(ruleMap.get("R9"), "PROMPT_DECOMPOSE", "SYSTEM_B", "prompt-main", "拆解提示词 prompt-main", "content",
                        StringUtils.defaultString(ctx.promptMainVersion), "", "基于 " + ctx.promptMainVersion + " 新建草稿（需在编辑器中完成）",
                        "非口径类拆分错误，需修订规则", "LOW"));
            }
        }

        // ---------------- 意图 ----------------
        if (type.startsWith("INTENT") || secondary.contains("INTENT")) {
            out.add(change(ruleMap.get("R10"), "KNOWLEDGE", "BI_MANAGER", ctx.aiBodyId, "智能体知识库", "knowledge_element", "",
                    StringUtils.defaultIfBlank(root, "（请填写业务知识条目）"), "追加业务知识条目", "意图识别错误", "MEDIUM"));
        }

        // ---------------- 总结 ----------------
        if (type.startsWith("SUMMARY") || secondary.contains("SUMMARY")) {
            if (root.contains("截断")) {
                out.add(change(ruleMap.get("R12"), "SYSTEM_B_CONFIG", "SYSTEM_B", "TRUNCATE_*", "System B 截断配置", "TRUNCATE", "", "",
                        "Phase 2：调整截断配置", "根因提到截断（本期仅提示）", "LOW"));
            } else {
                out.add(change(ruleMap.get("R11"), "PROMPT_SUMMARY", "SYSTEM_B", "prompt-summary", "总结提示词 prompt-summary", "content",
                        StringUtils.defaultString(ctx.promptSummaryVersion), "", "基于 " + ctx.promptSummaryVersion + " 新建草稿（需在编辑器中完成）",
                        "总结错误，需修订总结提示词", "LOW"));
            }
        }

        // 去重 + seq
        List<ChangeDTO> dedup = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChangeDTO c : out) {
            String k = c.getAssetType() + "|" + c.getTargetId() + "|" + c.getField() + "|" + c.getAfterValue();
            if (seen.add(k)) dedup.add(c);
        }
        for (int i = 0; i < dedup.size(); i++) {
            dedup.get(i).setSeq(i + 1);
            dedup.get(i).setAccepted(!"LOW".equals(dedup.get(i).getConfidence()));
        }
        return dedup;
    }

    // ------------------------------------------------------------------ helpers

    private static ChangeDTO change(TuningRule rule, String assetType, String module, String targetId, String label, String field,
                                    String before, String after, String diff, String reason, String confidence) {
        ChangeDTO c = new ChangeDTO();
        c.setSource("SUGGEST");
        c.setRuleCode(rule == null ? null : rule.getRuleCode());
        c.setAssetType(assetType);
        c.setTargetModule(module);
        c.setTargetId(targetId);
        c.setTargetLabel(label);
        c.setField(field);
        c.setBeforeValue(before);
        c.setAfterValue(after);
        c.setDiffSummary(diff);
        c.setReason(reason);
        c.setConfidence(rule != null && StringUtils.isNotBlank(rule.getConfidence()) ? rule.getConfidence() : confidence);
        return c;
    }

    private static String idOf(JSONObject o) {
        return o == null ? null : o.getString("id");
    }

    static final Pattern CJK_TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}|[A-Za-z_]{3,}");
    /** 停用词 / 分隔词：先把问题按这些词切开，再在片段内取词，避免贪婪匹配把「查询深圳各电」连成一个词 */
    static final String[] STOP = {"是多少", "有多少", "帮我", "请问", "查询", "统计", "分析", "多少", "情况", "今年", "去年", "本月", "上月", "每个",
            "各个", "一下", "数据", "指标", "维度", "最近", "按照", "排名", "前十", "前五", "各", "的", "按", "和", "与", "及", "在", "中", "了", "吗", "呢", "？", "?", "，", ",", "。"};

    /** 从问题中抽取 2–6 字中文词 / 英文词，剔除停用词和已出现在 existing 中的词 */
    static List<String> keywordsNotIn(String question, String existing) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(question)) return out;
        String q = question;
        for (String st : STOP) q = q.replace(st, " ");
        String ex = StringUtils.defaultString(existing);
        for (String seg : q.split("\\s+")) {
            Matcher m = CJK_TOKEN.matcher(seg);
            while (m.find()) {
                String w = m.group();
                if (w.length() > 4 && w.matches("[\\u4e00-\\u9fa5]+")) w = w.substring(0, 4);
                if (ex.contains(w) || out.contains(w)) continue;
                out.add(w);
                if (out.size() >= 4) return out;
            }
        }
        return out;
    }

    static String appendKeywords(String before, List<String> kws) {
        if (kws.isEmpty()) return before;
        String sep = StringUtils.isBlank(before) ? "" : (before.endsWith("；") || before.endsWith(";") ? "" : "；");
        return before + sep + "支持按" + String.join("/", kws) + "查询";
    }

    static String appendAlias(String before, List<String> kws) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : StringUtils.defaultString(before).split("[,，;；]")) if (StringUtils.isNotBlank(s)) set.add(s.trim());
        for (String k : kws) if (StringUtils.isNotBlank(k)) set.add(k.trim());
        return String.join(",", set);
    }

    static String aggFromRoot(String root) {
        if (root == null) return null;
        if (root.contains("快照")) return "snapshot";
        if (root.contains("累计")) return "cumulative";
        if (root.contains("率") && (root.contains("sum") || root.contains("求和") || root.contains("相加"))) return "avg";
        if (root.contains("平均")) return "avg";
        return null;
    }

    private static String firstMetric(ChatErrorDiagnosis d) {
        for (JSONObject e : parseObjArr(d.getExpectedEntities())) {
            if (!"dim".equalsIgnoreCase(e.getString("type")) && StringUtils.isNotBlank(e.getString("code"))) return e.getString("code");
        }
        return null;
    }

    /** 最近实体：忽略大小写/下划线的包含关系，其次编辑距离 ≤ 2 */
    static String nearestEntity(String col, Set<String> names) {
        if (StringUtils.isBlank(col) || names == null) return null;
        String c = col.toLowerCase().replace("_", "");
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String n : names) {
            String nn = n.toLowerCase().replace("_", "");
            if (nn.equals(c)) return n;
            if (nn.contains(c) || c.contains(nn)) {
                int dist = Math.abs(nn.length() - c.length());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = n;
                }
            }
        }
        if (best != null) return best;
        for (String n : names) {
            int dist = levenshtein(c, n.toLowerCase().replace("_", ""));
            if (dist <= 2 && dist < bestDist) {
                bestDist = dist;
                best = n;
            }
        }
        return best;
    }

    static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
        return dp[a.length()][b.length()];
    }

    static List<String> parseArr(String json) {
        if (StringUtils.isBlank(json)) return new ArrayList<>();
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static List<JSONObject> parseObjArr(String json) {
        if (StringUtils.isBlank(json)) return new ArrayList<>();
        try {
            JSONArray a = JSON.parseArray(json);
            List<JSONObject> out = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) out.add(a.getJSONObject(i));
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static String abbreviate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
