package com.chatbi.chat.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.service.AiBodyService;
import com.chatbi.chat.service.MetadataService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 元数据组装：从 AiBodyService 查询结果组装成 resolver 所需的 JSON 结构。
 */
@Service
@Slf4j
public class MetadataServiceImpl implements MetadataService {

    @Resource
    private AiBodyService aiBodyService;

    @Override
    public JSONArray buildAgentIndicators(String code) {
        List<Map<String, Object>> indRows = aiBodyService.getResolverIndicators(code);
        List<Map<String, Object>> calcRows = fetchCalcIndicators(indRows);
        List<Map<String, Object>> allIndRows = new ArrayList<>(indRows);
        allIndRows.addAll(calcRows);
        return transformIndsForResolver(allIndRows, false);
    }

    @Override
    public JSONArray buildAgentDimensions(String code) {
        return transformDimsForResolver(aiBodyService.getResolverDimensions(code), false);
    }

    @Override
    public JSONArray buildAgentTableSummaries(String code) {
        return buildTableSummaries(aiBodyService.getResolverTableSummaries(code), true, false);
    }

    @Override
    public JSONArray buildAllIndicators() {
        List<Map<String, Object>> atomicRows = aiBodyService.getAllResolverIndicators();
        List<Map<String, Object>> calcRows = aiBodyService.getAllResolverCalcIndicators();
        Set<Long> atomicIds = collectIds(atomicRows, "metric_id");
        List<Map<String, Object>> merged = new ArrayList<>(atomicRows);
        if (CollectionUtils.isNotEmpty(calcRows)) {
            for (Map<String, Object> calcRow : calcRows) {
                Object idObj = calcRow.get("id");
                if (idObj instanceof Number && atomicIds.contains(((Number) idObj).longValue())) {
                    continue; // 脏数据：同一指标既有映射行又带公式，以原子形态为准
                }
                merged.add(calcRow);
            }
        }
        return transformIndsForResolver(merged, true);
    }

    @Override
    public JSONArray buildAllDimensions() {
        return transformDimsForResolver(aiBodyService.getAllResolverDimensions(), true);
    }

    @Override
    public JSONArray buildAllTableSummaries() {
        return buildTableSummaries(aiBodyService.getAllResolverTableSummaries(), false, true);
    }

    @Override
    public JSONArray buildAllBusinessContexts() {
        JSONArray result = new JSONArray();
        List<Map<String, Object>> rows = aiBodyService.getAllKnowledgeElements();
        if (CollectionUtils.isEmpty(rows)) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            JSONObject item = new JSONObject(new LinkedHashMap<>());
            item.put("ai_body_id", row.get("ai_body_id"));
            item.put("knowledge_element", StringUtils.defaultIfBlank((String) row.get("knowledge_element"), ""));
            result.add(item);
        }
        return result;
    }

    @Override
    public String buildAgentBusinessContext(String code) {
        try {
            com.chatbi.chat.entity.AiBody aiBody = aiBodyService.getAiBodyByCode(code);
            if (aiBody == null || CollectionUtils.isEmpty(aiBody.getAiBodyKnowledgeInfoList())) {
                return "";
            }
            return aiBody.getAiBodyKnowledgeInfoList().stream()
                    .map(com.chatbi.chat.models.AiBodyKnowledgeInfo::getKnowledgeElement)
                    .filter(StringUtils::isNotBlank)
                    .map(c -> "- " + c)
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

    private Set<Long> collectIds(List<Map<String, Object>> rows, String key) {
        Set<Long> ids = new HashSet<>();
        if (CollectionUtils.isEmpty(rows)) {
            return ids;
        }
        for (Map<String, Object> row : rows) {
            Object idObj = row.get(key);
            if (idObj instanceof Number) {
                ids.add(((Number) idObj).longValue());
            }
        }
        return ids;
    }

    private JSONArray transformIndsForResolver(List<Map<String, Object>> indRows, boolean withIds) {
        JSONArray result = new JSONArray();
        if (indRows == null) {
            return result;
        }
        for (Map<String, Object> row : indRows) {
            JSONObject target = new JSONObject(new LinkedHashMap<>());
            if (withIds) {
                Object idObj = row.get("metric_id") != null ? row.get("metric_id") : row.get("id");
                target.put("metric_id", idObj);
            }
            target.put("metric_name", StringUtils.defaultIfBlank((String) row.get("metric_name"), ""));
            target.put("metric_code", StringUtils.defaultIfBlank((String) row.get("metric_code"), ""));
            target.put("description", StringUtils.defaultIfBlank((String) row.get("description"), ""));
            target.put("unit", StringUtils.defaultIfBlank((String) row.get("unit"), ""));
            target.put("data_type", StringUtils.defaultIfBlank((String) row.get("data_type"), ""));
            String calcFormula = (String) row.get("calculated_production");
            String deriveFormula = (String) row.get("derivative_production");
            if (StringUtils.isNotBlank(calcFormula)) {
                target.put("metric_type", "calc");
                target.put("formula", calcFormula);
            } else if (StringUtils.isNotBlank(deriveFormula)) {
                target.put("metric_type", "derive");
                target.put("formula", deriveFormula);
            } else {
                target.put("metric_type", "atom");
                target.put("formula", "");
            }
            result.add(target);
        }
        return result;
    }

    private JSONArray transformDimsForResolver(List<Map<String, Object>> dimRows, boolean withIds) {
        JSONArray result = new JSONArray();
        if (dimRows == null) {
            return result;
        }
        for (Map<String, Object> row : dimRows) {
            JSONObject target = new JSONObject(new LinkedHashMap<>());
            if (withIds) {
                target.put("dimension_id", row.get("dimension_id"));
            }
            target.put("dimension_name", StringUtils.defaultIfBlank((String) row.get("dimension_name"), ""));
            target.put("dimension_code", StringUtils.defaultIfBlank((String) row.get("dimension_code"), ""));
            target.put("description", StringUtils.defaultIfBlank((String) row.get("description"), ""));
            target.put("data_type", StringUtils.defaultIfBlank((String) row.get("data_type"), ""));
            target.put("possible_values", toPossibleValuesArray(row.get("possible_values")));
            result.add(target);
        }
        return result;
    }

    private JSONArray toPossibleValuesArray(Object possibleValues) {
        if (possibleValues == null) {
            return new JSONArray();
        }
        if (possibleValues instanceof JSONArray) {
            return (JSONArray) possibleValues;
        }
        try {
            String jsonStr = possibleValues.toString();
            if (jsonStr.trim().startsWith("[")) {
                return JSONArray.parseArray(jsonStr);
            }
            JSONObject jsonObj = JSONObject.parseObject(jsonStr);
            JSONArray arr = new JSONArray();
            for (String key : jsonObj.keySet()) {
                arr.add(jsonObj.getString(key));
            }
            return arr;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * 按表分组组装表结构摘要，columns 含 agg_type 聚合方式，并追加 5 个固定时间维度。
     */
    private JSONArray buildTableSummaries(List<Map<String, Object>> rows, boolean appendFixedTimeDims, boolean withBasicIds) {
        JSONArray tableSummaries = new JSONArray();
        if (CollectionUtils.isEmpty(rows)) {
            return tableSummaries;
        }
        Map<String, List<Map<String, Object>>> groupedRows = rows.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("tableName")) + "||" + String.valueOf(row.get("tbDescription")),
                LinkedHashMap::new,
                Collectors.toList()
        ));
        for (List<Map<String, Object>> group : groupedRows.values()) {
            if (CollectionUtils.isEmpty(group)) {
                continue;
            }
            Map<String, Object> firstRow = group.get(0);
            JSONObject tableSummary = new JSONObject(new LinkedHashMap<>());
            tableSummary.put("table_name", firstRow.get("tableName"));
            tableSummary.put("description", firstRow.get("tbDescription"));
            JSONArray columns = new JSONArray();
            for (Map<String, Object> row : group) {
                JSONObject column = new JSONObject(new LinkedHashMap<>());
                if (withBasicIds) {
                    column.put("basic_id", row.get("basicId"));
                }
                column.put("column_name", row.get("columnName"));
                column.put("data_type", row.get("dataType"));
                column.put("is_dimension", row.get("isDimension"));
                column.put("is_measure", row.get("isMeasure"));
                column.put("description", row.get("columnDescription"));
                column.put("agg_type", row.get("summary") == null ? "" : row.get("summary"));
                columns.add(column);
            }
            // 追加固定5条时间维度数据（agent 模式追加，全量模式不追加）
            if (appendFixedTimeDims) {
                columns.add(buildFixedTimeDim("ptdate", "日期"));
                columns.add(buildFixedTimeDim("ptmonth", "月份"));
                columns.add(buildFixedTimeDim("ptquarter", "季度"));
                columns.add(buildFixedTimeDim("ptweek", "自然周"));
                columns.add(buildFixedTimeDim("ptyear", "年度"));
            }
            tableSummary.put("columns", columns);
            tableSummaries.add(tableSummary);
        }
        return tableSummaries;
    }

    private JSONObject buildFixedTimeDim(String columnName, String description) {
        JSONObject dim = new JSONObject(new LinkedHashMap<>());
        dim.put("column_name", columnName);
        dim.put("data_type", "");
        dim.put("is_dimension", 1);
        dim.put("is_measure", 0);
        dim.put("description", description);
        dim.put("agg_type", "");
        return dim;
    }

    /**
     * 查 code 原子指标可支撑的计算/派生指标（已上线）。
     * 派生指标在 SQL 内已过滤；计算指标在此做严格校验：引用的原子指标全部在集合内才保留。
     */
    private List<Map<String, Object>> fetchCalcIndicators(List<Map<String, Object>> indRows) {
        List<Long> atomicIds = new ArrayList<>(collectIds(indRows, "metric_id"));
        if (atomicIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> atomicIdSet = new HashSet<>(atomicIds);
        List<Map<String, Object>> calcRows = aiBodyService.getResolverCalcIndicators(atomicIds);
        List<Map<String, Object>> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(calcRows)) {
            return result;
        }
        for (Map<String, Object> row : calcRows) {
            if (!isCalcFullySupported(row, atomicIdSet)) {
                continue;
            }
            Object idObj = row.get("id");
            if (idObj instanceof Number && atomicIdSet.contains(((Number) idObj).longValue())) {
                continue; // 脏数据：该指标同时存在映射行，以原子形态为准
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 严格校验计算指标：formula indicatorList 中所有引用 id 都在可用原子指标集合内。
     * 派生指标（无 calculated_production）在 SQL 内已过滤，直接放行。
     */
    private boolean isCalcFullySupported(Map<String, Object> row, Set<Long> atomicIdSet) {
        String calcFormula = (String) row.get("calculated_production");
        if (StringUtils.isBlank(calcFormula)) {
            return true;
        }
        try {
            JSONObject json = JSONObject.parseObject(calcFormula);
            JSONArray indicatorList = json.getJSONArray("indicatorList");
            if (indicatorList == null || indicatorList.isEmpty()) {
                log.warn("计算指标公式indicatorList为空，排除: id={}", row.get("id"));
                return false;
            }
            for (int i = 0; i < indicatorList.size(); i++) {
                Long refId = indicatorList.getJSONObject(i).getLong("id");
                if (refId == null || !atomicIdSet.contains(refId)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("计算指标公式解析失败，排除: id={}, error={}", row.get("id"), e.getMessage());
            return false;
        }
    }
}
