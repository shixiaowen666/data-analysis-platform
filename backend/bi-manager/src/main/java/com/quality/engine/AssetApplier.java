package com.quality.engine;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bi.entity.*;
import com.bi.mapper.*;
import com.quality.client.DownstreamClient;
import com.quality.entity.TuningChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 资产读写：快照（当前线上值）/ 发布（真实写库）/ 回退（恢复快照）/ 草稿 meta 覆盖（内存）。
 * 覆盖资产类型：TABLE_DESC / ENTITY_ALIAS / DIM_VALUES / AGG_TYPE / KNOWLEDGE / AGENT_TABLE /
 * PROMPT_DECOMPOSE / PROMPT_SUMMARY（System B switch）/ RECALL_CONFIG（Phase 1 仅记录）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetApplier {

    private final OlapTableProMapper tableMapper;
    private final OlapBasicProMapper basicMapper;
    private final OlapDimValueMapper dimValueMapper;
    private final OlapTableFieldMappingMapper fieldMappingMapper;
    private final AiBodyKnowledgeInfoMapper knowledgeMapper;
    private final AiBodyRelationMapper relationMapper;
    private final AiBodyMapper aiBodyMapper;
    private final DownstreamClient downstream;

    // ------------------------------------------------------------------ snapshot

    /** 读取变更目标的当前线上值（发布/回退依据）。返回 {value, version} */
    public JSONObject snapshot(TuningChange c) {
        JSONObject out = new JSONObject();
        try {
            switch (c.getAssetType()) {
                case "TABLE_DESC": {
                    OlapTablePro t = tableById(c.getTargetId(), c.getTargetLabel());
                    out.put("value", t == null ? null : t.getNote());
                    break;
                }
                case "ENTITY_ALIAS": {
                    OlapBasicPro b = basicById(c.getTargetId(), c.getTargetLabel());
                    out.put("value", b == null ? null : b.getAlias());
                    break;
                }
                case "DIM_VALUES": {
                    Long dimId = parseLong(c.getTargetId());
                    List<OlapDimValue> vs = dimId == null ? Collections.emptyList()
                            : dimValueMapper.selectList(new LambdaQueryWrapper<OlapDimValue>().eq(OlapDimValue::getDimId, dimId));
                    out.put("value", JSON.toJSONString(vs.stream().map(OlapDimValue::getDimValue).toList()));
                    break;
                }
                case "AGG_TYPE": {
                    OlapTableFieldMapping fm = fieldMappingByBasic(c.getTargetId());
                    out.put("value", fm == null ? null : fm.getSummary());
                    break;
                }
                case "KNOWLEDGE": {
                    out.put("value", ""); // 新增型变更：快照为空，回退 = 删除新增条目
                    break;
                }
                case "AGENT_TABLE": {
                    out.put("value", "未绑定");
                    break;
                }
                case "PROMPT_DECOMPOSE":
                case "PROMPT_SUMMARY": {
                    String group = "PROMPT_DECOMPOSE".equals(c.getAssetType()) ? "prompt-main" : "prompt-summary";
                    JSONObject cur = downstream.promptCurrent(group);
                    out.put("value", cur == null ? null : cur.getString("version"));
                    out.put("version", cur == null ? null : cur.getString("version"));
                    break;
                }
                default:
                    out.put("value", c.getBeforeValue());
            }
        } catch (Exception e) {
            log.warn("[quality] snapshot {} {} failed: {}", c.getAssetType(), c.getTargetId(), e.getMessage());
            out.put("value", c.getBeforeValue());
            out.put("error", e.getMessage());
        }
        return out;
    }

    // ------------------------------------------------------------------- publish

    /** 真实写库。返回 null 表示成功，否则错误信息。 */
    public String publish(TuningChange c, String aiBodyCode) {
        try {
            switch (c.getAssetType()) {
                case "TABLE_DESC": {
                    OlapTablePro t = tableById(c.getTargetId(), c.getTargetLabel());
                    if (t == null) return "表不存在: " + c.getTargetLabel();
                    t.setNote(c.getAfterValue());
                    tableMapper.updateById(t);
                    return null;
                }
                case "ENTITY_ALIAS": {
                    OlapBasicPro b = basicById(c.getTargetId(), c.getTargetLabel());
                    if (b == null) return "实体不存在: " + c.getTargetLabel();
                    b.setAlias(c.getAfterValue());
                    basicMapper.updateById(b);
                    return null;
                }
                case "DIM_VALUES": {
                    Long dimId = parseLong(c.getTargetId());
                    if (dimId == null) return "维度 id 为空";
                    for (String v : splitValues(c.getAfterValue())) {
                        Long cnt = dimValueMapper.selectCount(new LambdaQueryWrapper<OlapDimValue>()
                                .eq(OlapDimValue::getDimId, dimId).eq(OlapDimValue::getDimValue, v));
                        if (cnt == 0) {
                            OlapDimValue dv = new OlapDimValue();
                            dv.setDimId(dimId);
                            dv.setDimValue(v);
                            dv.setValueCount(0);
                            dimValueMapper.insert(dv);
                        }
                    }
                    return null;
                }
                case "AGG_TYPE": {
                    OlapTableFieldMapping fm = fieldMappingByBasic(c.getTargetId());
                    if (fm == null) return "字段映射不存在: " + c.getTargetLabel();
                    fm.setSummary(c.getAfterValue());
                    fm.setSummaryKey(c.getAfterValue());
                    fieldMappingMapper.updateById(fm);
                    return null;
                }
                case "KNOWLEDGE": {
                    AiBody body = aiBodyByCode(aiBodyCode, c.getTargetId());
                    if (body == null) return "智能体不存在";
                    AiBodyKnowledgeInfo k = new AiBodyKnowledgeInfo();
                    k.setAiBodyId(body.getId().longValue());
                    k.setTenantId(body.getTenantId());
                    k.setKnowledgeElement(c.getAfterValue());
                    k.setKnowledgeAlias("tuning:" + c.getTaskId());
                    k.setCreatedBy(0L);
                    k.setUpdatedBy(0L);
                    k.setCreatedAt(java.time.LocalDateTime.now());
                    k.setUpdatedAt(java.time.LocalDateTime.now());
                    knowledgeMapper.insert(k);
                    return null;
                }
                case "AGENT_TABLE": {
                    OlapTablePro t = tableById(c.getTargetId(), c.getTargetLabel());
                    if (t == null) return "表不存在: " + c.getTargetLabel();
                    Long cnt = relationMapper.selectCount(new LambdaQueryWrapper<AiBodyRelation>()
                            .eq(AiBodyRelation::getCode, aiBodyCode).eq(AiBodyRelation::getRelationId, t.getId()).eq(AiBodyRelation::getRelationType, 0));
                    if (cnt == 0) {
                        AiBodyRelation r = new AiBodyRelation();
                        r.setCode(aiBodyCode);
                        r.setRelationId(t.getId());
                        r.setRelationType(0);
                        r.setTenantId(t.getTenantId());
                        r.setCreatedBy(0L);
                        r.setUpdatedBy(0L);
                        r.setCreatedAt(java.time.LocalDateTime.now());
                        r.setUpdatedAt(java.time.LocalDateTime.now());
                        relationMapper.insert(r);
                    }
                    return null;
                }
                case "PROMPT_DECOMPOSE":
                case "PROMPT_SUMMARY": {
                    if (StringUtils.isBlank(c.getAfterValue())) return "提示词草稿版本为空，请先在编辑器中保存";
                    String group = "PROMPT_DECOMPOSE".equals(c.getAssetType()) ? "prompt-main" : "prompt-summary";
                    JSONObject r = downstream.promptSwitch(group, c.getAfterValue());
                    return r != null && r.getIntValue("code") == 200 ? null : "prompts/switch 失败: " + (r == null ? "null" : r.getString("message"));
                }
                case "RECALL_CONFIG":
                case "RECALL_DICT":
                case "SYSTEM_B_CONFIG":
                    log.info("[quality] {} 变更 Phase 1 仅记录，不自动写入: {}", c.getAssetType(), c.getDiffSummary());
                    return null;
                default:
                    return "不支持的资产类型: " + c.getAssetType();
            }
        } catch (Exception e) {
            log.error("[quality] publish change {} failed", c.getId(), e);
            return e.getMessage();
        }
    }

    // ------------------------------------------------------------------ rollback

    /** 恢复快照值。返回 null 成功。 */
    public String rollback(TuningChange c, String snapshotValue, String snapshotVersion, String aiBodyCode) {
        try {
            switch (c.getAssetType()) {
                case "TABLE_DESC": {
                    OlapTablePro t = tableById(c.getTargetId(), c.getTargetLabel());
                    if (t == null) return "表不存在";
                    t.setNote(snapshotValue);
                    tableMapper.updateById(t);
                    return null;
                }
                case "ENTITY_ALIAS": {
                    OlapBasicPro b = basicById(c.getTargetId(), c.getTargetLabel());
                    if (b == null) return "实体不存在";
                    b.setAlias(snapshotValue);
                    basicMapper.updateById(b);
                    return null;
                }
                case "DIM_VALUES": {
                    Long dimId = parseLong(c.getTargetId());
                    if (dimId == null) return "维度 id 为空";
                    Set<String> keep = new HashSet<>();
                    try {
                        keep.addAll(JSON.parseArray(StringUtils.defaultIfBlank(snapshotValue, "[]"), String.class));
                    } catch (Exception ignore) {
                    }
                    for (String v : splitValues(c.getAfterValue())) {
                        if (!keep.contains(v)) {
                            dimValueMapper.delete(new LambdaQueryWrapper<OlapDimValue>().eq(OlapDimValue::getDimId, dimId).eq(OlapDimValue::getDimValue, v));
                        }
                    }
                    return null;
                }
                case "AGG_TYPE": {
                    OlapTableFieldMapping fm = fieldMappingByBasic(c.getTargetId());
                    if (fm == null) return "字段映射不存在";
                    fm.setSummary(snapshotValue);
                    fm.setSummaryKey(snapshotValue);
                    fieldMappingMapper.updateById(fm);
                    return null;
                }
                case "KNOWLEDGE": {
                    knowledgeMapper.delete(new LambdaQueryWrapper<AiBodyKnowledgeInfo>()
                            .eq(AiBodyKnowledgeInfo::getKnowledgeAlias, "tuning:" + c.getTaskId())
                            .eq(AiBodyKnowledgeInfo::getKnowledgeElement, c.getAfterValue()));
                    return null;
                }
                case "AGENT_TABLE": {
                    OlapTablePro t = tableById(c.getTargetId(), c.getTargetLabel());
                    if (t == null) return "表不存在";
                    relationMapper.delete(new LambdaQueryWrapper<AiBodyRelation>()
                            .eq(AiBodyRelation::getCode, aiBodyCode).eq(AiBodyRelation::getRelationId, t.getId()).eq(AiBodyRelation::getRelationType, 0));
                    return null;
                }
                case "PROMPT_DECOMPOSE":
                case "PROMPT_SUMMARY": {
                    String ver = StringUtils.defaultIfBlank(snapshotVersion, snapshotValue);
                    if (StringUtils.isBlank(ver)) return "无快照版本可回退";
                    String group = "PROMPT_DECOMPOSE".equals(c.getAssetType()) ? "prompt-main" : "prompt-summary";
                    JSONObject r = downstream.promptSwitch(group, ver);
                    return r != null && r.getIntValue("code") == 200 ? null : "prompts/switch 回退失败";
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            log.error("[quality] rollback change {} failed", c.getId(), e);
            return e.getMessage();
        }
    }

    // --------------------------------------------------------- draft meta override

    /**
     * 草稿隔离（§6.1）：按变更列表在内存中改写 database_meta，不写库。
     * 返回 {meta, prompt_overrides}
     */
    public JSONObject buildDraftMeta(JSONObject baseMeta, List<TuningChange> changes, String aiBodyCode) {
        JSONObject meta = baseMeta == null ? new JSONObject() : JSON.parseObject(baseMeta.toJSONString());
        Map<String, String> promptOverrides = new LinkedHashMap<>();
        JSONArray tables = meta.getJSONArray("table_summaries");
        JSONArray metrics = meta.getJSONArray("available_metrics");
        JSONArray dims = meta.getJSONArray("available_dimensions");
        StringBuilder ctx = new StringBuilder(StringUtils.defaultString(meta.getString("business_context")));
        for (TuningChange c : changes) {
            if (c.getAccepted() != null && c.getAccepted() == 0) continue;
            switch (c.getAssetType()) {
                case "TABLE_DESC":
                    forEach(tables, t -> {
                        if (c.getTargetLabel() != null && c.getTargetLabel().startsWith(String.valueOf(t.getString("table_name"))))
                            t.put("description", c.getAfterValue());
                    });
                    break;
                case "ENTITY_ALIAS": {
                    String code = codeOf(c.getTargetLabel());
                    forEach(metrics, m -> {
                        if (code.equals(m.getString("metric_code"))) m.put("description", mergeAlias(m.getString("description"), c.getAfterValue()));
                    });
                    forEach(dims, d -> {
                        if (code.equals(d.getString("dimension_code"))) d.put("description", mergeAlias(d.getString("description"), c.getAfterValue()));
                    });
                    forEach(tables, t -> forEach(t.getJSONArray("columns"), col -> {
                        if (code.equals(col.getString("column_name"))) col.put("description", mergeAlias(col.getString("description"), c.getAfterValue()));
                    }));
                    break;
                }
                case "DIM_VALUES": {
                    String code = codeOf(c.getTargetLabel());
                    forEach(dims, d -> {
                        if (code.equals(d.getString("dimension_code"))) {
                            JSONArray pv = d.getJSONArray("possible_values");
                            if (pv == null) pv = new JSONArray();
                            for (String v : splitValues(c.getAfterValue())) if (!pv.contains(v)) pv.add(v);
                            d.put("possible_values", pv);
                        }
                    });
                    break;
                }
                case "AGG_TYPE": {
                    String code = codeOf(c.getTargetLabel());
                    forEach(tables, t -> forEach(t.getJSONArray("columns"), col -> {
                        if (code.equals(col.getString("column_name"))) col.put("agg_type", c.getAfterValue());
                    }));
                    break;
                }
                case "KNOWLEDGE":
                    if (StringUtils.isNotBlank(c.getAfterValue())) {
                        if (ctx.length() > 0) ctx.append("\n");
                        ctx.append("- ").append(c.getAfterValue());
                    }
                    break;
                case "AGENT_TABLE": {
                    // 把表及其字段加入 table_summaries（从全量 meta 取不到时仅加表名）
                    String tbl = c.getTargetLabel();
                    boolean exists = false;
                    for (int i = 0; tables != null && i < tables.size(); i++)
                        if (tbl.equals(tables.getJSONObject(i).getString("table_name"))) exists = true;
                    if (!exists && tables != null) {
                        JSONObject t = new JSONObject();
                        t.put("table_name", tbl);
                        OlapTablePro tp = tableById(c.getTargetId(), tbl);
                        t.put("description", tp == null ? "" : tp.getNote());
                        t.put("columns", columnsOf(tp));
                        tables.add(t);
                    }
                    break;
                }
                case "PROMPT_DECOMPOSE":
                    if (StringUtils.isNotBlank(c.getAfterValue())) promptOverrides.put("prompt-main", c.getAfterValue());
                    break;
                case "PROMPT_SUMMARY":
                    if (StringUtils.isNotBlank(c.getAfterValue())) promptOverrides.put("prompt-summary", c.getAfterValue());
                    break;
                default:
                    break;
            }
        }
        meta.put("business_context", ctx.toString());
        JSONObject out = new JSONObject();
        out.put("meta", meta);
        out.put("prompt_overrides", promptOverrides);
        return out;
    }

    // ------------------------------------------------------------------- lookup

    public OlapTablePro tableById(String id, String label) {
        Long lid = parseLong(id);
        if (lid != null) {
            OlapTablePro t = tableMapper.selectById(lid);
            if (t != null) return t;
        }
        String name = codeOf(label);
        if (StringUtils.isBlank(name)) return null;
        return tableMapper.selectOne(new LambdaQueryWrapper<OlapTablePro>().eq(OlapTablePro::getTbName, name).last("limit 1"));
    }

    public OlapBasicPro basicById(String id, String label) {
        Long lid = parseLong(id);
        if (lid != null) {
            OlapBasicPro b = basicMapper.selectById(lid);
            if (b != null) return b;
        }
        String code = codeOf(label);
        if (StringUtils.isBlank(code)) return null;
        return basicMapper.selectOne(new LambdaQueryWrapper<OlapBasicPro>().eq(OlapBasicPro::getEnglishName, code).last("limit 1"));
    }

    public OlapTableFieldMapping fieldMappingByBasic(String basicId) {
        Long bid = parseLong(basicId);
        if (bid == null) return null;
        return fieldMappingMapper.selectOne(new LambdaQueryWrapper<OlapTableFieldMapping>().eq(OlapTableFieldMapping::getBasicId, bid).last("limit 1"));
    }

    public AiBody aiBodyByCode(String code, String idFallback) {
        if (StringUtils.isNotBlank(code)) {
            AiBody b = aiBodyMapper.selectOne(new LambdaQueryWrapper<AiBody>().eq(AiBody::getCode, code).last("limit 1"));
            if (b != null) return b;
        }
        Long id = parseLong(idFallback);
        return id == null ? null : aiBodyMapper.selectById(id.intValue());
    }

    public Set<String> agentTableNames(String code) {
        List<AiBodyRelation> rels = relationMapper.selectList(new LambdaQueryWrapper<AiBodyRelation>()
                .eq(AiBodyRelation::getCode, code).eq(AiBodyRelation::getRelationType, 0));
        Set<String> out = new HashSet<>();
        for (AiBodyRelation r : rels) {
            OlapTablePro t = tableMapper.selectById(r.getRelationId());
            if (t != null) out.add(t.getTbName());
        }
        return out;
    }

    private JSONArray columnsOf(OlapTablePro tp) {
        JSONArray cols = new JSONArray();
        if (tp == null) return cols;
        for (OlapTableFieldMapping fm : fieldMappingMapper.selectList(new LambdaQueryWrapper<OlapTableFieldMapping>()
                .eq(OlapTableFieldMapping::getTableId, tp.getId()).eq(OlapTableFieldMapping::getStatus, 1))) {
            if (StringUtils.isBlank(fm.getBasicKey())) continue;
            JSONObject c = new JSONObject();
            c.put("column_name", fm.getBasicKey());
            c.put("data_type", fm.getFieldType());
            c.put("is_dimension", "dim".equals(fm.getBasicTypeKey()));
            c.put("is_measure", "index".equals(fm.getBasicTypeKey()));
            c.put("description", fm.getBasicName());
            c.put("agg_type", StringUtils.defaultString(fm.getSummary()));
            cols.add(c);
        }
        return cols;
    }

    // ------------------------------------------------------------------ helpers

    static String codeOf(String label) {
        if (StringUtils.isBlank(label)) return "";
        return label.trim().split("\\s+")[0];
    }

    static String mergeAlias(String desc, String alias) {
        if (StringUtils.isBlank(alias)) return desc;
        String d = StringUtils.defaultString(desc);
        return d.contains(alias) ? d : (StringUtils.isBlank(d) ? "" : d + "；") + "别名：" + alias;
    }

    static List<String> splitValues(String v) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(v)) return out;
        String s = v.trim();
        if (s.startsWith("[")) {
            try {
                return JSON.parseArray(s, String.class);
            } catch (Exception ignore) {
            }
        }
        for (String x : s.split("[,，;；\\n]")) if (StringUtils.isNotBlank(x)) out.add(x.trim());
        return out;
    }

    private static Long parseLong(String s) {
        try {
            return StringUtils.isBlank(s) ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private interface Visitor {
        void visit(JSONObject o);
    }

    private static void forEach(JSONArray arr, Visitor v) {
        if (arr == null) return;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o != null) v.visit(o);
        }
    }
}
