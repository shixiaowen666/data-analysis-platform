package com.quality.engine;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerifyJudgeTest {

    private static JSONObject j(String s) {
        return JSON.parseObject(s);
    }

    @Test
    void passWhenAllDimensionsMatch() {
        VerifyJudge.Judgement r = VerifyJudge.judge(
                j("{\"table\":\"t1\",\"metrics\":[\"m1\"],\"dims\":[\"d1\"],\"answer_keywords\":[\"深圳\"],\"answer_value\":3.21}"),
                j("{\"status\":\"success\",\"used_tables\":[\"t1\"],\"resolved_metrics\":[\"m1\",\"m2\"],\"resolved_dims\":[\"d1\"],\"answer\":\"深圳线损率 3.2%\"}"));
        assertTrue(r.pass, r.detail.toJSONString());
        assertEquals("ok", r.detail.getString("table"));
        assertEquals("ok", r.detail.getString("answer_value"));
    }

    @Test
    void failOnWrongTableOrStatus() {
        VerifyJudge.Judgement r = VerifyJudge.judge(j("{\"table\":\"t1\"}"),
                j("{\"status\":\"success\",\"used_tables\":[\"t2\"]}"));
        assertFalse(r.pass);
        assertTrue(r.detail.getString("table").startsWith("fail"));
        r = VerifyJudge.judge(null, j("{\"status\":\"failure\"}"));
        assertFalse(r.pass);
        assertNull(VerifyJudge.judge(null, null).detail.getString("table"));
    }

    @Test
    void verdictTable() {
        assertEquals("FIXED", VerifyJudge.verdict("ORIGIN", false, true));
        assertEquals("IMPROVED", VerifyJudge.verdict("SIMILAR", false, true));
        assertEquals("STILL_FAIL", VerifyJudge.verdict("ORIGIN", false, false));
        assertEquals("DEGRADED", VerifyJudge.verdict("REGRESSION", true, false));
        assertEquals("PASS", VerifyJudge.verdict("REGRESSION", true, true));
        assertEquals("PASS", VerifyJudge.verdict("MANUAL", null, true));
    }

    @Test
    void conclusion() {
        assertEquals("PASS", VerifyJudge.conclusion(true, 0, true));
        assertEquals("PASS_WITH_WARN", VerifyJudge.conclusion(true, 2, true));
        assertEquals("FAIL", VerifyJudge.conclusion(false, 0, true));
        assertEquals("PASS", VerifyJudge.conclusion(null, 0, false));
    }

    @Test
    void numbers() {
        assertEquals(2, VerifyJudge.numbersIn("3.2% 和 1,200 条").size());
        assertEquals(1200.0, VerifyJudge.numbersIn("1,200").get(0));
    }
}
