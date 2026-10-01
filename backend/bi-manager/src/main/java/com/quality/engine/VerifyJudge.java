package com.quality.engine;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证判定（设计文档 03 §6.3）：逐题多维判定 + verdict。
 * <p>
 * expected: {table, tables[], metrics[], dims[], answer_keywords[], answer_value?}
 * result:   System B analyze 响应摘要 {status, used_tables[], resolved_metrics[], resolved_dims[], answer}
 */
public final class VerifyJudge {

    private VerifyJudge() {
    }

    public static final class Judgement {
        public boolean pass;
        public JSONObject detail = new JSONObject();
    }

    public static Judgement judge(JSONObject expected, JSONObject result) {
        Judgement j = new Judgement();
        if (result == null) {
            j.pass = false;
            j.detail.put("status", "fail(no result)");
            return j;
        }
        boolean ok = true;
        // 执行状态 必检
        String status = result.getString("status");
        boolean statusOk = "success".equals(status) || "partial_failure".equals(status) && Boolean.TRUE.equals(result.getBoolean("allow_partial"));
        j.detail.put("status", statusOk ? "ok" : "fail(" + status + ")");
        ok &= statusOk;

        if (expected != null) {
            // 表
            String table = expected.getString("table");
            JSONArray tables = expected.getJSONArray("tables");
            List<String> used = strList(result.getJSONArray("used_tables"));
            if (StringUtils.isNotBlank(table)) {
                boolean t = used.contains(table);
                j.detail.put("table", t ? "ok" : "fail(expect " + table + ", got " + used + ")");
                ok &= t;
            } else if (tables != null && !tables.isEmpty()) {
                List<String> missing = new ArrayList<>();
                for (String x : strList(tables)) if (!used.contains(x)) missing.add(x);
                j.detail.put("table", missing.isEmpty() ? "ok" : "fail(missing " + missing + ")");
                ok &= missing.isEmpty();
            }
            // 指标
            ok &= subset("metrics", strList(expected.getJSONArray("metrics")), strList(result.getJSONArray("resolved_metrics")), j);
            // 维度
            ok &= subset("dims", strList(expected.getJSONArray("dims")), strList(result.getJSONArray("resolved_dims")), j);
            // 答案关键词
            List<String> kws = strList(expected.getJSONArray("answer_keywords"));
            String answer = StringUtils.defaultString(result.getString("answer"));
            if (!kws.isEmpty()) {
                List<String> missing = new ArrayList<>();
                for (String k : kws) if (!answer.contains(k)) missing.add(k);
                j.detail.put("answer", missing.isEmpty() ? "ok" : "fail(missing " + missing + ")");
                ok &= missing.isEmpty();
            }
            // 答案数值
            if (expected.get("answer_value") != null) {
                Double exp = toDouble(expected.get("answer_value"));
                boolean hit = exp != null && numbersIn(answer).stream().anyMatch(v -> Math.abs(v - exp) <= Math.abs(exp) * 0.01 + 1e-9);
                j.detail.put("answer_value", hit ? "ok" : "fail(expect " + exp + ")");
                ok &= hit;
            }
        }
        j.pass = ok;
        return j;
    }

    /** verdict 规则表（§6.3） */
    public static String verdict(String caseType, Boolean beforePass, boolean afterPass) {
        boolean isOrigin = "ORIGIN".equals(caseType);
        if (beforePass == null) {
            // 无 before 基线（SIMILAR 无诊断）：只做变化检测
            return afterPass ? "PASS" : "FAIL";
        }
        if (!beforePass && afterPass) return isOrigin ? "FIXED" : "IMPROVED";
        if (!beforePass) return "STILL_FAIL";
        if (!afterPass) return "DEGRADED";
        return "PASS";
    }

    /** 报告结论：PASS / PASS_WITH_WARN / FAIL */
    public static String conclusion(Boolean originFixed, int degraded, boolean hasOrigin) {
        if (hasOrigin && !Boolean.TRUE.equals(originFixed)) return "FAIL";
        return degraded > 0 ? "PASS_WITH_WARN" : "PASS";
    }

    // ------------------------------------------------------------------ helpers

    private static boolean subset(String key, List<String> expected, List<String> actual, Judgement j) {
        if (expected.isEmpty()) return true;
        List<String> missing = new ArrayList<>();
        for (String e : expected) if (!actual.contains(e)) missing.add(e);
        j.detail.put(key, missing.isEmpty() ? "ok" : "fail(missing " + missing + ")");
        return missing.isEmpty();
    }

    static List<String> strList(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.size(); i++) {
            Object o = a.get(i);
            if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }

    private static final Pattern NUM = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    static List<Double> numbersIn(String s) {
        List<Double> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = NUM.matcher(s);
        while (m.find()) {
            try {
                out.add(Double.parseDouble(m.group().replace(",", "")));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    private static Double toDouble(Object o) {
        try {
            return o instanceof Number ? ((Number) o).doubleValue() : Double.parseDouble(String.valueOf(o).replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
