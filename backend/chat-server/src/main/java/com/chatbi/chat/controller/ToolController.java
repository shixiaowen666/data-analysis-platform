package com.chatbi.chat.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.config.PagingProperties;
import com.chatbi.chat.entity.OlapBasicPro;
import com.chatbi.chat.feign.client.QueryServerClient;
import com.chatbi.chat.feign.dto.ColumnMeta;
import com.chatbi.chat.feign.dto.GetDataSqlResponse;
import com.chatbi.chat.feign.dto.QueryDataRequest;
import com.chatbi.chat.feign.dto.QueryDataResponse;
import com.chatbi.chat.mapper.OlapTableProMapper;
import com.chatbi.chat.models.*;
import com.chatbi.chat.service.AiBodyService;
import com.chatbi.chat.service.OlapBasicProService;
import com.chatbi.chat.service.OlapDataService;
import com.chatbi.chat.utils.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/chat-server/tool"})
@Slf4j
public class ToolController {

    @Autowired
    private QueryServerClient queryServerClient;
    @Autowired
    private OlapBasicProService olapBasicProService;
    @Autowired
    private OlapDataService olapDataService;
    @Resource
    private AiBodyService aiBodyService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PagingProperties pagingProperties;
    @Resource
    private OlapTableProMapper olapTableProMapper;

    /**
     * getdata/ai v1：operator 透传，key（englishName）优先解析 id
     */
    @RequestMapping(value = "/getdata/ai/v1", method = RequestMethod.POST)
    public JSONObject getDataForAiV1(@RequestBody Map<String, Object> param) {
        String requestId = param.get("request_id") != null ? param.get("request_id").toString() : "";
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("request_id不能为空");
        }
        String[] ids = requestId.split("@");
        MDC.put("trace_id", ids[1]);
        log.info("======================================查询数据请求报文v1：{}", JSON.toJSONString(param));

        // 用户信息由 LoginFilter 从 Header token 解析，直接取 UserThreadLocal
        SaasUser user = UserThreadLocal.get();
        if (user == null) {
            throw new IllegalStateException("未获取到用户信息，请检查Authorization header中的token");
        }

        // agent code 仍需从 Redis 获取
        String codeKey = requestId + "-code";
        String code = stringRedisTemplate.opsForValue().get(codeKey);
        if (StringUtils.isBlank(code)) {
            throw new IllegalStateException("查询阶段没有获取正常的智能体code，请重新发起会话, requestId=" + requestId);
        }

        String query = param.get("query") != null ? param.get("query").toString() : "";
        String queryDescription = param.get("query_description") != null ? param.get("query_description").toString() : "";
        String stepId = param.get("step_id") != null ? param.get("step_id").toString() : "";
        String[] stepids = stepId.split("_");
        String chatSessionId = ids[0];
        String chatId = ids[1];
        String itemId = stepids[1];

        AIChatVO chatVO = new AIChatVO();
        chatVO.setChatSessionId(chatSessionId);
        chatVO.setChatId(chatId);
        chatVO.setQuestion(queryDescription);
        chatVO.setItemId(Integer.valueOf(itemId));

        // 解析 expected_columns：name 即 key（englishName），operator 透传
        JSONArray expectedColumns = (JSONArray) JSON.toJSON(param.get("expected_columns"));
        List<String> columnKeys = new ArrayList<>();
        List<QueryDataRequest.Filter> queryFilters = new ArrayList<>();

        for (int i = 0; i < expectedColumns.size(); i++) {
            JSONObject colNode = expectedColumns.getJSONObject(i);
            String name = colNode.getString("name");
            if (StringUtils.isNotBlank(name)) {
                columnKeys.add(name);
            }
            // 处理 filter，operator 透传
            Object filterNode = colNode.get("filter");
            JSONArray columnFilters = new JSONArray();
            if (filterNode instanceof JSONArray) {
                columnFilters = (JSONArray) filterNode;
            } else if (filterNode instanceof JSONObject) {
                columnFilters.add(filterNode);
            }
            if (CollectionUtils.isNotEmpty(columnFilters)) {
                for (int j = 0; j < columnFilters.size(); j++) {
                    JSONObject fObj = columnFilters.getJSONObject(j);
                    if (fObj == null) continue;
                    QueryDataRequest.Filter qf = new QueryDataRequest.Filter();
                    qf.setType("dimension");
                    qf.setField(name);
                    // operator 透传，不做任何转换
                    String operator = fObj.getString("operator");
                    JSONArray valuesArr = fObj.getJSONArray("values");
                    if (valuesArr != null && valuesArr.size() > 1 && "=".equals(operator)) {
                        operator = "in";
                    }
                    qf.setOperator(operator);
                    qf.setValues(valuesArr != null ? valuesArr.toJavaList(String.class) : Collections.emptyList());
                    queryFilters.add(qf);
                }
            }
        }

        // 按 key（englishName）查 olap_basic_pro，区分维度和指标
        List<OlapBasicPro> basicProList = olapBasicProService.getBasicProByName(columnKeys);
        // 校验：传入的 key 如果查不到则报错
        Set<String> foundNames = basicProList.stream()
                .map(OlapBasicPro::getEnglishName)
                .collect(Collectors.toSet());
        for (String k : columnKeys) {
            if (!foundNames.contains(k)) {
                throw new IllegalArgumentException("字段 key '" + k + "' 在 olap_basic_pro 中未找到");
            }
        }

        List<Long> dimensionIds = new ArrayList<>();
        List<Long> indicatorIds = new ArrayList<>();
        for (OlapBasicPro pro : basicProList) {
            if (pro.getCategory() == null) continue;
            if (pro.getCategory() == 1) {
                dimensionIds.add(pro.getId());
            } else if (pro.getCategory() == 2) {
                indicatorIds.add(pro.getId());
            }
        }

        // 校验 expected_table 与维度的匹配关系
        String expectedTable = param.get("expected_table") != null ? param.get("expected_table").toString() : "";
        Long resolvedTableId = resolveTableColumns(expectedColumns, columnKeys, expectedTable, queryFilters);

        // 构建查询请求
        QueryDataRequest queryDataRequest = new QueryDataRequest();
        queryDataRequest.setDimensionIds(dimensionIds);
        queryDataRequest.setIndicatorIds(indicatorIds);
        if (resolvedTableId != null) {
            queryDataRequest.setPreferredTableIds(Collections.singletonList(resolvedTableId));
        } else {
            queryDataRequest.setPreferredTableIds(aiBodyService.getPreferredTableIds(code));
        }
        queryDataRequest.setPreferredModelIds(aiBodyService.getPreferredModelIds(code));
        queryDataRequest.setFilters(queryFilters);

        // 时间范围
        if (param.get("time_range") != null) {
            QueryDataRequest.TimeRange tr = JSON.parseObject(
                    JSON.toJSONString(param.get("time_range")), QueryDataRequest.TimeRange.class);
            queryDataRequest.setTimeRange(tr);
        }

        // 维度包含时间维度时，默认按最细粒度时间维度 asc 排序
        Map<String, Integer> timePriority = new LinkedHashMap<>();
        timePriority.put("ptdate", 0);
        timePriority.put("ptweek", 1);
        timePriority.put("ptmonth", 2);
        timePriority.put("ptquarter", 3);
        timePriority.put("ptyear", 4);
        basicProList.stream()
                .filter(p -> p.getCategory() != null && p.getCategory() == 1 && timePriority.containsKey(p.getEnglishName()))
                .min(Comparator.comparingInt(p -> timePriority.get(p.getEnglishName())))
                .ifPresent(p -> {
                    QueryDataRequest.Sort s = new QueryDataRequest.Sort();
                    s.setBasicId(p.getId());
                    s.setDirection("asc");
                    queryDataRequest.setSorts(Collections.singletonList(s));
                });

        // ===== 【临时逻辑】问数路径同环比时间粒度 =====
        // v1 无指标组合，同环比只跟随请求显式传的 indicatorComparison，不做默认补充；
        // 存在对比配置时，若 dateGranularity 为空：有时间维度按最细时间维度推断
        // （ptdate→day、ptweek→week、ptmonth→month、ptquarter→quarter、ptyear→year），
        // 无时间维度但有 timeRange 用 year。
        if (MapUtils.isNotEmpty(queryDataRequest.getIndicatorComparison())
                && StringUtils.isBlank(queryDataRequest.getDateGranularity())) {
            boolean hasTimeDim = basicProList.stream().anyMatch(p -> p.getCategory() != null && p.getCategory() == 1
                    && timePriority.containsKey(p.getEnglishName()));
            if (hasTimeDim) {
                Map<String, String> timeDimGranularity = new LinkedHashMap<>();
                timeDimGranularity.put("ptdate", "day");
                timeDimGranularity.put("ptweek", "week");
                timeDimGranularity.put("ptmonth", "month");
                timeDimGranularity.put("ptquarter", "quarter");
                timeDimGranularity.put("ptyear", "year");
                basicProList.stream()
                        .filter(p -> p.getCategory() != null && p.getCategory() == 1
                                && timePriority.containsKey(p.getEnglishName()))
                        .min(Comparator.comparingInt(p -> timePriority.get(p.getEnglishName())))
                        .ifPresent(p -> queryDataRequest.setDateGranularity(timeDimGranularity.get(p.getEnglishName())));
            } else if (queryDataRequest.getTimeRange() != null) {
                queryDataRequest.setDateGranularity("year");
            }
            log.info("[临时逻辑] 同环比时间粒度推断v1: dateGranularity={}, requestId={}",
                    queryDataRequest.getDateGranularity(), requestId);
        }
        // ===== 【临时逻辑】结束 =====

        QueryDataRequest.Paging paging = new QueryDataRequest.Paging();
        paging.setPage(pagingProperties.getDefaultPage());
        paging.setPageSize(pagingProperties.getAiPageSize());
        queryDataRequest.setPaging(paging);

        log.info("查询data-server的请求v1：{}", JSON.toJSONString(queryDataRequest));
        long callStart = System.currentTimeMillis();
        QueryDataResponse<GetDataSqlResponse> queryDataResponse = null;
        try {
            queryDataResponse = queryServerClient.execute(queryDataRequest);
        } catch (Exception e) {
            log.error("queryServerClient.execute调用失败v1, 耗时={}ms", System.currentTimeMillis() - callStart, e);
        }
        log.info("data-server.execute 完成v1, 耗时={}ms", System.currentTimeMillis() - callStart);

        // 构建返回给前端的 DataRequestDTO（用于历史记录）
        DataRequestDTO dataRequestDTO = buildDataRequestDTO(basicProList, queryDataRequest, queryFilters);
        String stepType = "query";
        olapDataService.updateChatRecord(chatVO, dataRequestDTO, queryDataResponse, stepType);

        JSONObject resultObj = new JSONObject();
        if (null != queryDataResponse && queryDataResponse.getCode() == 200) {
            GetDataSqlResponse dataRes = queryDataResponse.getData();
            if (dataRes.getRecords() == null || dataRes.getRecords().isEmpty()) {
                log.warn("v1 查询结果为空: {}", queryDescription);
            } else {
                log.info("v1 查询成功: {}, 返回 {} 条", queryDescription, dataRes.getRecords().size());
            }
            resultObj = buildResultJson(dataRes, requestId, stepId);
        } else {
            String errMsg = queryDataResponse != null ? queryDataResponse.getMessage() : "data-server响应为空";
            log.error("v1 查询失败: {}, msg={}", queryDescription, errMsg);
            resultObj.put("status", "fail");
            resultObj.put("error_message", errMsg);
        }
        return resultObj;
    }

    private JSONObject buildResultJson(GetDataSqlResponse dataRes, String requestId, String stepId) {
        JSONObject resultObj = new JSONObject();
        resultObj.put("request_id", requestId);
        resultObj.put("step_id", stepId);
        resultObj.put("status", "success");
        resultObj.put("error_message", "");
        JSONArray tableColumns = new JSONArray();
        for (ColumnMeta col : dataRes.getColumns()) {
            JSONObject column = new JSONObject();
            column.put("name", col.getKey());
            column.put("description", col.getName());
            column.put("data_type", "string");
            column.put("unit", col.getUnit() != null ? col.getUnit() : "");
            tableColumns.add(column);
        }
        JSONArray dataResult = new JSONArray();
        List<Map<String, Object>> dataList = dataRes.getRecords();
        if (dataList != null) {
            for (Map<String, Object> row : dataList) {
                dataResult.add(new JSONObject(row));
            }
        }
        JSONObject datas = new JSONObject();
        datas.put("columns", tableColumns);
        datas.put("rows", dataResult);
        resultObj.put("data", datas);
        return resultObj;
    }

    private DataRequestDTO buildDataRequestDTO(List<OlapBasicPro> basicProList,
                                               QueryDataRequest queryDataRequest,
                                               List<QueryDataRequest.Filter> queryFilters) {
        DataRequestDTO dto = new DataRequestDTO();
        List<DimDTO> dims = new ArrayList<>();
        List<IndexDTO> idxs = new ArrayList<>();
        for (OlapBasicPro pro : basicProList) {
            if (pro.getCategory() == null) continue;
            if (pro.getCategory() == 1) {
                DimDTO dim = new DimDTO();
                dim.setId(pro.getId());
                dim.setDimKey(pro.getEnglishName());
                dim.setDimName(pro.getChineseName());
                dims.add(dim);
            } else if (pro.getCategory() == 2) {
                IndexDTO idx = new IndexDTO();
                idx.setId(pro.getId());
                idx.setIndKey(pro.getEnglishName());
                idx.setIndName(pro.getChineseName());
                idxs.add(idx);
            }
        }
        dto.setDimList(dims);
        dto.setIndexList(idxs);
        if (queryDataRequest.getTimeRange() != null) {
            DataRequestDTO.TimeRangeDTO tr = new DataRequestDTO.TimeRangeDTO();
            tr.setStart(queryDataRequest.getTimeRange().getStart());
            tr.setEnd(queryDataRequest.getTimeRange().getEnd());
            dto.setTimeRange(tr);
        }
        if (CollectionUtils.isNotEmpty(queryFilters)) {
            List<FilterDTO> filterDTOs = new ArrayList<>();
            for (QueryDataRequest.Filter qf : queryFilters) {
                FilterDTO fd = new FilterDTO();
                FilterFieldDTO ffd = new FilterFieldDTO();
                OlapBasicPro pro = basicProList.stream()
                        .filter(p -> qf.getField() != null && qf.getField().equals(p.getEnglishName()))
                        .findFirst().orElse(null);
                ffd.setFieldClazz("dimension".equals(qf.getType()) ? 0 : 1);
                ffd.setKey(qf.getField());
                ffd.setName(pro != null ? pro.getChineseName() : qf.getField());
                fd.setFilterField(ffd);
                fd.setOperator(parseOperatorToInt(qf.getOperator()));
                fd.setFilterValue(qf.getValues());
                filterDTOs.add(fd);
            }
            dto.setFilters(filterDTOs);
        }
        return dto;
    }

    private Integer parseOperatorToInt(String operator) {
        if (operator == null) return 2;
        switch (operator) {
            case ">": case "gt": return 0;
            case "<": case "lt": return 1;
            case "=": case "eq": return 2;
            case ">=": case "gte": return 3;
            case "<=": case "lte": return 4;
            case "in": return 5;
            case "notin": case "not in": return 6;
            case "!=": case "ne": return 7;
            case "like": return 8;
            case "notlike": case "not like": return 9;
            default: return 2;
        }
    }

    // ========== 维度-表校验 & org 维度互斥替换 ==========

    private static final Set<String> ORG_DIM_KEYS = Set.of("gdj", "city_org_name", "dis_org_name", "branch_org_name");

    private Long resolveTableColumns(JSONArray expectedColumns, List<String> columnKeys,
                                     String expectedTable, List<QueryDataRequest.Filter> queryFilters) {
        if (StringUtils.isBlank(expectedTable)) {
            return null;
        }
        Long tableId = olapTableProMapper.getTableIdByName(expectedTable);
        if (tableId == null) {
            log.warn("expected_table '{}' 在 olap_table_pro 中未找到，降级走原逻辑", expectedTable);
            return null;
        }
        Set<String> tableFields = olapTableProMapper.getTableFields(tableId).stream()
                .map(f -> String.valueOf(f.get("basic_key")))
                .collect(Collectors.toSet());
        log.info("表字段校验v1: expected_table='{}', columns={}, tableFields={}", expectedTable, columnKeys, tableFields);

        List<String> mismatched = columnKeys.stream()
                .filter(n -> !tableFields.contains(n))
                .collect(Collectors.toList());

        if (mismatched.isEmpty()) {
            log.info("维度校验通过v1: expected_table='{}', tableId={}", expectedTable, tableId);
            return tableId;
        }

        boolean resolved = resolveOrgMismatch(mismatched, tableFields,
                expectedColumns, columnKeys, queryFilters);
        if (!resolved) {
            log.warn("维度校验失败v1，不匹配字段: {}，降级走原逻辑", mismatched);
            return null;
        }

        List<String> stillMismatched = columnKeys.stream()
                .filter(n -> !tableFields.contains(n))
                .collect(Collectors.toList());
        if (!stillMismatched.isEmpty()) {
            log.warn("org维度替换后仍有不匹配字段v1: {}，降级走原逻辑", stillMismatched);
            return null;
        }
        log.info("org维度替换后校验通过v1: expected_table='{}', tableId={}", expectedTable, tableId);
        return tableId;
    }

    private boolean resolveOrgMismatch(List<String> mismatched, Set<String> tableFields,
                                       JSONArray expectedColumns, List<String> columnKeys,
                                       List<QueryDataRequest.Filter> queryFilters) {
        for (String name : mismatched) {
            if (!ORG_DIM_KEYS.contains(name)) continue;
            if ("gdj".equals(name)) {
                return splitGdjToOrgColumns(expectedColumns, columnKeys, queryFilters, tableFields);
            } else if (tableFields.contains("gdj")) {
                return mergeOrgColumnsToGdj(expectedColumns, columnKeys, queryFilters, name);
            }
        }
        return false;
    }

    private boolean splitGdjToOrgColumns(JSONArray expectedColumns, List<String> columnKeys,
                                         List<QueryDataRequest.Filter> queryFilters, Set<String> tableFields) {
        JSONObject gdjCol = null;
        int gdjIdx = -1;
        for (int i = 0; i < expectedColumns.size(); i++) {
            JSONObject col = expectedColumns.getJSONObject(i);
            if ("gdj".equals(col.getString("name"))) {
                gdjCol = col;
                gdjIdx = i;
                break;
            }
        }
        if (gdjCol == null) return false;

        List<String> allFilterValues = extractFilterValues(gdjCol);
        log.info("gdj 拆分开始v1: filterValues={}", allFilterValues);
        if (allFilterValues.isEmpty()) {
            expectedColumns.remove(gdjIdx);
            columnKeys.remove("gdj");
            queryFilters.removeIf(f -> "gdj".equals(f.getField()));
            boolean anyAdded = addOrgColumnNoFilter(expectedColumns, columnKeys, "city_org_name", tableFields);
            anyAdded |= addOrgColumnNoFilter(expectedColumns, columnKeys, "dis_org_name", tableFields);
            anyAdded |= addOrgColumnNoFilter(expectedColumns, columnKeys, "branch_org_name", tableFields);
            return anyAdded;
        }

        List<String> cityValues = new ArrayList<>();
        List<String> disValues = new ArrayList<>();
        List<String> branchValues = new ArrayList<>();
        List<String> unknownValues = new ArrayList<>();
        for (String val : allFilterValues) {
            if (val.endsWith("供电分局")) {
                branchValues.add(val);
            } else if ("深圳供电局".equals(val)) {
                cityValues.add(val);
            } else if (val.endsWith("供电局")) {
                disValues.add(val);
            } else {
                unknownValues.add(val);
            }
        }

        expectedColumns.remove(gdjIdx);
        columnKeys.remove("gdj");
        queryFilters.removeIf(f -> "gdj".equals(f.getField()));

        boolean anyAdded = addOrgColumn(expectedColumns, columnKeys, queryFilters,
                "city_org_name", cityValues, tableFields);
        anyAdded |= addOrgColumn(expectedColumns, columnKeys, queryFilters,
                "dis_org_name", disValues, tableFields);
        anyAdded |= addOrgColumn(expectedColumns, columnKeys, queryFilters,
                "branch_org_name", branchValues, tableFields);

        if (anyAdded) {
            log.info("gdj 拆分完成v1: 替换前 维度=gdj, 值={} | 替换后 city={}, dis={}, branch={}, 未识别={}",
                    allFilterValues, cityValues, disValues, branchValues, unknownValues);
        }
        if (!unknownValues.isEmpty()) {
            log.warn("gdj 拆分v1: 以下 filter value 无法识别 org 层级，已丢弃: {}", unknownValues);
        }
        return anyAdded;
    }

    private boolean mergeOrgColumnsToGdj(JSONArray expectedColumns, List<String> columnKeys,
                                         List<QueryDataRequest.Filter> queryFilters, String firstMismatched) {
        List<String> mergedValues = new ArrayList<>();
        List<String> removedDims = new ArrayList<>();
        for (int i = expectedColumns.size() - 1; i >= 0; i--) {
            JSONObject col = expectedColumns.getJSONObject(i);
            String colName = col.getString("name");
            if ("city_org_name".equals(colName) || "dis_org_name".equals(colName) || "branch_org_name".equals(colName)) {
                List<String> vals = extractFilterValues(col);
                mergedValues.addAll(vals);
                removedDims.add(colName);
                expectedColumns.remove(i);
            }
        }
        if (removedDims.isEmpty()) return false;

        columnKeys.removeAll(removedDims);
        columnKeys.add("gdj");
        queryFilters.removeIf(f -> removedDims.contains(f.getField()));

        JSONObject gdjCol = new JSONObject();
        gdjCol.put("name", "gdj");
        gdjCol.put("role", "dimension");
        if (!mergedValues.isEmpty()) {
            JSONObject filterObj = new JSONObject();
            filterObj.put("operator", "=");
            filterObj.put("values", mergedValues);
            gdjCol.put("filter", filterObj);
            QueryDataRequest.Filter qf = new QueryDataRequest.Filter();
            qf.setType("dimension");
            qf.setField("gdj");
            qf.setOperator(mergedValues.size() > 1 ? "in" : "=");
            qf.setValues(mergedValues);
            queryFilters.add(qf);
        }
        expectedColumns.add(gdjCol);
        log.info("gdj 合并完成v1: 替换前 维度={}, 值={} | 替换后 维度=gdj, 值={}",
                removedDims, mergedValues, mergedValues);
        return true;
    }

    private List<String> extractFilterValues(JSONObject col) {
        List<String> values = new ArrayList<>();
        Object filterNode = col.get("filter");
        JSONArray filters = new JSONArray();
        if (filterNode instanceof JSONArray) {
            filters = (JSONArray) filterNode;
        } else if (filterNode instanceof JSONObject) {
            filters.add(filterNode);
        }
        for (int j = 0; j < filters.size(); j++) {
            JSONObject f = filters.getJSONObject(j);
            JSONArray vals = f.getJSONArray("values");
            if (vals != null) {
                for (int k = 0; k < vals.size(); k++) {
                    String v = vals.getString(k);
                    if (StringUtils.isNotBlank(v)) {
                        values.add(v);
                    }
                }
            }
        }
        return values;
    }

    private boolean addOrgColumn(JSONArray expectedColumns, List<String> columnKeys,
                                 List<QueryDataRequest.Filter> queryFilters,
                                 String orgKey, List<String> filterValues, Set<String> tableFields) {
        if (!tableFields.contains(orgKey) || filterValues.isEmpty()) return false;
        JSONObject col = new JSONObject();
        col.put("name", orgKey);
        col.put("role", "dimension");
        JSONObject filterObj = new JSONObject();
        filterObj.put("operator", "=");
        filterObj.put("values", new JSONArray(filterValues));
        col.put("filter", filterObj);
        expectedColumns.add(col);
        columnKeys.add(orgKey);

        QueryDataRequest.Filter qf = new QueryDataRequest.Filter();
        qf.setType("dimension");
        qf.setField(orgKey);
        qf.setOperator(filterValues.size() > 1 ? "in" : "=");
        qf.setValues(filterValues);
        queryFilters.add(qf);
        return true;
    }

    private boolean addOrgColumnNoFilter(JSONArray expectedColumns, List<String> columnKeys,
                                         String orgKey, Set<String> tableFields) {
        if (!tableFields.contains(orgKey)) return false;
        JSONObject col = new JSONObject();
        col.put("name", orgKey);
        col.put("role", "dimension");
        col.put("filter", new JSONArray());
        expectedColumns.add(col);
        columnKeys.add(orgKey);
        return true;
    }
}
