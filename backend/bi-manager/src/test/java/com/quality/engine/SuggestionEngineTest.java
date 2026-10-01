package com.quality.engine;

import com.alibaba.fastjson.JSONObject;
import com.quality.dto.ChangeDTO;
import com.quality.entity.ChatAnalysisTrace;
import com.quality.entity.ChatErrorDiagnosis;
import com.quality.entity.ChatStepTrace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SuggestionEngineTest {

    private SuggestionEngine.AssetContext ctx() {
        SuggestionEngine.AssetContext c = new SuggestionEngine.AssetContext();
        JSONObject tbl = new JSONObject();
        tbl.put("id", 11);
        tbl.put("note", "线损率按电压等级统计");
        c.tables.put("view_by_voltage", tbl);
        JSONObject actual = new JSONObject();
        actual.put("id", 12);
        actual.put("note", "分区线损率");
        c.tables.put("view_fenqu", actual);
        JSONObject ent = new JSONObject();
        ent.put("id", 101);
        ent.put("alias", "线损率");
        ent.put("chineseName", "分压线损率");
        c.entities.put("ll_rate", ent);
        c.entities.put("voltage_level", new JSONObject());
        c.agentTables = Set.of("view_fenqu");
        c.aiBodyId = "7";
        c.promptMainVersion = "v1.0.5";
        c.promptSummaryVersion = "v2.0.2";
        c.recallTopK = 10;
        return c;
    }

    private ChatAnalysisTrace trace(String recallTables, String usedTables) {
        ChatAnalysisTrace t = new ChatAnalysisTrace();
        t.setQuestion("查询深圳各电压等级的分压线损率");
        t.setRecallTables(recallTables);
        t.setUsedTables(usedTables);
        return t;
    }

    @Test
    void tableSelect_R1_R3_R4_whenNotRecalled() {
        ChatErrorDiagnosis d = new ChatErrorDiagnosis();
        d.setPrimaryErrorType("TABLE_SELECT");
        d.setExpectedTable("view_by_voltage");
        d.setActualTable("view_fenqu");
        List<ChangeDTO> out = SuggestionEngine.suggest(d, trace("[\"view_fenqu\"]", "[\"view_fenqu\"]"), List.of(), List.of(), ctx());
        assertTrue(out.stream().anyMatch(c -> "TABLE_DESC".equals(c.getAssetType()) && c.getAfterValue().contains("支持按")), "R1 描述追加关键词");
        assertTrue(out.stream().anyMatch(c -> "AGENT_TABLE".equals(c.getAssetType())), "R3 绑定表");
        assertTrue(out.stream().anyMatch(c -> "RECALL_CONFIG".equals(c.getAssetType()) && "15".equals(c.getAfterValue())), "R4 top_k 10→15");
        assertEquals(1, out.get(0).getSeq());
        assertTrue(out.stream().filter(c -> "LOW".equals(c.getConfidence())).noneMatch(ChangeDTO::getAccepted));
    }

    @Test
    void tableSelect_R2_whenRecalledButNotChosen() {
        ChatErrorDiagnosis d = new ChatErrorDiagnosis();
        d.setPrimaryErrorType("TABLE_SELECT");
        d.setExpectedTable("view_by_voltage");
        d.setActualTable("view_fenqu");
        List<ChangeDTO> out = SuggestionEngine.suggest(d, trace("[\"view_fenqu\",\"view_by_voltage\",\"x\"]", "[\"view_fenqu\"]"), List.of(), List.of(), ctx());
        assertTrue(out.stream().anyMatch(c -> "TABLE_DESC".equals(c.getAssetType()) && c.getAfterValue().contains("区别")), "R2 区分描述");
        assertTrue(out.stream().anyMatch(c -> "KNOWLEDGE".equals(c.getAssetType())), "R2 知识条目");
        assertFalse(out.stream().anyMatch(c -> "RECALL_CONFIG".equals(c.getAssetType())), "召回 3 张不触发 R4");
    }

    @Test
    void metricDim_R5_R7() {
        ChatErrorDiagnosis d = new ChatErrorDiagnosis();
        d.setPrimaryErrorType("METRIC_DIM.METRIC");
        d.setExpectedEntities("[{\"type\":\"metric\",\"code\":\"ll_rate\",\"name\":\"分压线损率\"}]");
        ChatStepTrace s = new ChatStepTrace();
        s.setStepId("step_1");
        s.setUnmatchedColumns("[\"voltagelevel\"]");
        List<ChangeDTO> out = SuggestionEngine.suggest(d, trace(null, null), List.of(s), List.of(), ctx());
        ChangeDTO r5 = out.stream().filter(c -> "ENTITY_ALIAS".equals(c.getAssetType()) && c.getTargetLabel().startsWith("ll_rate")).findFirst().orElseThrow();
        assertTrue(r5.getAfterValue().startsWith("线损率,"), "保留原别名并追加: " + r5.getAfterValue());
        ChangeDTO r7 = out.stream().filter(c -> "voltage_level".equals(c.getTargetLabel())).findFirst().orElseThrow();
        assertEquals("MEDIUM", r7.getConfidence());
        assertTrue(r7.getAfterValue().contains("voltagelevel"));
    }

    @Test
    void decompose_R8_vs_R9() {
        ChatErrorDiagnosis d = new ChatErrorDiagnosis();
        d.setPrimaryErrorType("DECOMPOSE");
        d.setRootCause("月累计指标被 sum 了");
        d.setExpectedEntities("[{\"type\":\"metric\",\"code\":\"ll_rate\"}]");
        List<ChangeDTO> out = SuggestionEngine.suggest(d, trace(null, null), List.of(), List.of(), ctx());
        assertTrue(out.stream().anyMatch(c -> "AGG_TYPE".equals(c.getAssetType()) && "cumulative".equals(c.getAfterValue())));
        assertFalse(out.stream().anyMatch(c -> "PROMPT_DECOMPOSE".equals(c.getAssetType())));

        d.setRootCause("模型未按规则拆分时间范围");
        out = SuggestionEngine.suggest(d, trace(null, null), List.of(), List.of(), ctx());
        ChangeDTO r9 = out.stream().filter(c -> "PROMPT_DECOMPOSE".equals(c.getAssetType())).findFirst().orElseThrow();
        assertEquals("LOW", r9.getConfidence());
        assertFalse(r9.getAccepted());
        assertEquals("v1.0.5", r9.getBeforeValue());
    }

    @Test
    void intent_and_summary() {
        ChatErrorDiagnosis d = new ChatErrorDiagnosis();
        d.setPrimaryErrorType("INTENT");
        d.setSecondaryErrorTypes("[\"SUMMARY\"]");
        d.setRootCause("「线损」默认指综合线损率");
        List<ChangeDTO> out = SuggestionEngine.suggest(d, trace(null, null), List.of(), List.of(), ctx());
        assertTrue(out.stream().anyMatch(c -> "KNOWLEDGE".equals(c.getAssetType()) && c.getAfterValue().contains("综合线损率")));
        assertTrue(out.stream().anyMatch(c -> "PROMPT_SUMMARY".equals(c.getAssetType())));
        d.setRootCause("答案被截断");
        out = SuggestionEngine.suggest(d, trace(null, null), List.of(), List.of(), ctx());
        assertTrue(out.stream().anyMatch(c -> "SYSTEM_B_CONFIG".equals(c.getAssetType())));
    }

    @Test
    void helpers() {
        assertEquals("snapshot", SuggestionEngine.aggFromRoot("库存是快照"));
        assertEquals("avg", SuggestionEngine.aggFromRoot("线损率被求和"));
        assertNull(SuggestionEngine.aggFromRoot("规则问题"));
        assertEquals("a,b,c", SuggestionEngine.appendAlias("a,b", List.of("b", "c")));
        assertEquals("ll_rate", SuggestionEngine.nearestEntity("LL_RATE", Set.of("ll_rate", "x")));
        assertEquals("voltage_level", SuggestionEngine.nearestEntity("voltagelevel", Set.of("voltage_level", "ll_rate")));
        assertEquals(1, SuggestionEngine.levenshtein("abc", "abd"));
        List<String> kws = SuggestionEngine.keywordsNotIn("查询深圳各电压等级的分压线损率", "线损率");
        assertFalse(kws.contains("查询"));
        assertTrue(kws.stream().anyMatch(k -> k.contains("电压")), kws.toString());
    }
}
