package com.chatbi.chat.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.config.PagingProperties;
import com.chatbi.chat.constant.OperatorConverter;
import com.chatbi.chat.entity.OlapBasicPro;
import com.chatbi.chat.entity.ChatStepTrace;
import com.chatbi.chat.entity.OlapReportGroup;
import com.chatbi.chat.enums.ErrorMessageEnum;
import com.chatbi.chat.enums.ResultCode;
import com.chatbi.chat.feign.client.QueryServerClient;
import com.chatbi.chat.feign.dto.ColumnMeta;
import com.chatbi.chat.feign.dto.GetDataSqlResponse;
import com.chatbi.chat.feign.dto.QueryDataRequest;
import com.chatbi.chat.feign.dto.QueryDataResponse;
import com.chatbi.chat.models.*;
import com.chatbi.chat.service.OlapDataService;
import com.chatbi.chat.request.AIChatParam;
import com.chatbi.chat.response.CommonVo;
import com.chatbi.chat.mapper.OlapReportGroupMapper;
import com.chatbi.chat.mapper.OlapTableProMapper;
import com.chatbi.chat.service.AiBodyService;
import com.chatbi.chat.service.OlapBasicProService;
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
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/chat-server"})
@Slf4j
public class MetricQueryController {

    @Resource
    private com.chatbi.chat.service.ChatTraceService chatTraceService;

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
    @Resource
    private OlapReportGroupMapper olapReportGroupMapper;

    private static final String DS_SEP = "==========================================";

    @RequestMapping(value = "/getdata", method = RequestMethod.POST)
    public QueryDataResponse<GetDataSqlResponse> getdata(@RequestBody DataRequestDTO dataRequestDTO,
                                                          HttpServletResponse response) throws IOException {
        long start = System.currentTimeMillis();
        log.info(">>> GET /getdata request: {}", JSON.toJSONString(dataRequestDTO));

        QueryDataRequest queryDataRequest = new QueryDataRequest();
        if (CollectionUtils.isNotEmpty(dataRequestDTO.getDimList())) {
            queryDataRequest.setDimensionIds(dataRequestDTO.getDimList().stream()
                    .map(DimDTO::getId).collect(Collectors.toList()));
        } else {
            queryDataRequest.setDimensionIds(Collections.emptyList());
        }
        if (CollectionUtils.isNotEmpty(dataRequestDTO.getIndexList())) {
            queryDataRequest.setIndicatorIds(dataRequestDTO.getIndexList().stream()
                    .map(IndexDTO::getId).collect(Collectors.toList()));
        } else {
            queryDataRequest.setIndicatorIds(Collections.emptyList());
        }
        queryDataRequest.setPreferredTableIds(Collections.emptyList());
        queryDataRequest.setPreferredModelIds(Collections.emptyList());

        // 时间范围
        if (dataRequestDTO.getTimeRange() != null) {
            QueryDataRequest.TimeRange tr = new QueryDataRequest.TimeRange();
            tr.setStart(dataRequestDTO.getTimeRange().getStart());
            tr.setEnd(dataRequestDTO.getTimeRange().getEnd());
            queryDataRequest.setTimeRange(tr);
        }

        // 时间粒度 & 指标对比
        queryDataRequest.setDateGranularity(dataRequestDTO.getDateGranularity());
        queryDataRequest.setIndicatorComparison(dataRequestDTO.getIndicatorComparison());

        // 过滤条件
        if (CollectionUtils.isNotEmpty(dataRequestDTO.getFilters())) {
            List<QueryDataRequest.Filter> queryFilters = dataRequestDTO.getFilters().stream()
                    .map(f -> {
                        QueryDataRequest.Filter qf = new QueryDataRequest.Filter();
                        Integer fieldClazz = f.getFilterField() != null ? f.getFilterField().getFieldClazz() : null;
                        qf.setType(fieldClazz != null && fieldClazz == 0 ? "dimension" : "indicator");
                        qf.setField(f.getFilterField() != null ? f.getFilterField().getKey() : null);
                        qf.setOperator(compareTypeToOperator(f.getOperator()));
                        if (CollectionUtils.isNotEmpty(f.getFilterValue())) {
                            qf.setValues(f.getFilterValue());
                        }
                        return qf;
                    }).collect(Collectors.toList());
            queryDataRequest.setFilters(queryFilters);
        }

        // TOP N / 排序 / 分组TOP
        TopnDto top = dataRequestDTO.getTop();
        if (top != null) {
            List<QueryDataRequest.Sort> sorts = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(top.getOrders())) {
                sorts = top.getOrders().stream().map(o -> {
                    QueryDataRequest.Sort s = new QueryDataRequest.Sort();
                    s.setBasicId(o.getId());
                    s.setDirection(o.getOrder());
                    return s;
                }).collect(Collectors.toList());
            }
            if (CollectionUtils.isNotEmpty(top.getGroupDims())) {
                QueryDataRequest.GroupTopN groupTopN = new QueryDataRequest.GroupTopN();
                groupTopN.setGroupByBasicIds(top.getGroupDims().stream()
                        .map(TopGroupDim::getId).collect(Collectors.toList()));
                groupTopN.setOrderBy(sorts);
                groupTopN.setN(top.getTopNum());
                queryDataRequest.setGroupTopN(groupTopN);
            } else if (top.getTopNum() != null) {
                queryDataRequest.setLimit(top.getTopNum());
                queryDataRequest.setSorts(sorts);
            } else if (CollectionUtils.isNotEmpty(sorts)) {
                queryDataRequest.setSorts(sorts);
            }
        }
        // orderList 作为 top 为空的兜底排序
        if (queryDataRequest.getSorts() == null && CollectionUtils.isNotEmpty(dataRequestDTO.getOrderList())) {
            List<QueryDataRequest.Sort> sorts = dataRequestDTO.getOrderList().stream().map(o -> {
                QueryDataRequest.Sort s = new QueryDataRequest.Sort();
                s.setBasicId(o.getId());
                s.setDirection(o.getOrder());
                return s;
            }).collect(Collectors.toList());
            queryDataRequest.setSorts(sorts);
        }

        // 无排序且维度包含时间维度时，默认按最细粒度时间维度 asc 排序
        if (queryDataRequest.getSorts() == null && CollectionUtils.isNotEmpty(dataRequestDTO.getDimList())) {
            Map<String, Integer> timePriority = new LinkedHashMap<>();
            timePriority.put("ptdate", 0);
            timePriority.put("ptweek", 1);
            timePriority.put("ptmonth", 2);
            timePriority.put("ptquarter", 3);
            timePriority.put("ptyear", 4);
            dataRequestDTO.getDimList().stream()
                    .filter(d -> timePriority.containsKey(d.getDimKey()))
                    .min(Comparator.comparingInt(d -> timePriority.get(d.getDimKey())))
                    .ifPresent(d -> {
                        QueryDataRequest.Sort s = new QueryDataRequest.Sort();
                        s.setBasicId(d.getId());
                        s.setDirection("asc");
                        queryDataRequest.setSorts(Collections.singletonList(s));
                    });
        }

        if (queryDataRequest.getLimit() == null && queryDataRequest.getGroupTopN() == null) {
            QueryDataRequest.Paging paging = new QueryDataRequest.Paging();
            paging.setPage(dataRequestDTO.getPage() != null ? dataRequestDTO.getPage() : 1);
            paging.setPageSize(dataRequestDTO.getPageSize() != null ? dataRequestDTO.getPageSize() : 20);
            queryDataRequest.setPaging(paging);
        }

        // 下载模式
        if (dataRequestDTO.getDownloadFlag() != null && dataRequestDTO.getDownloadFlag() == 1) {
            QueryDataRequest.Paging paging = new QueryDataRequest.Paging();
            paging.setPage(pagingProperties.getDefaultPage());
            paging.setPageSize(pagingProperties.getDownloadPageSize());
            queryDataRequest.setPaging(paging);
            queryDataRequest.setLimit(null);
            queryDataRequest.setGroupTopN(null);
            log.info("**************************************************");
            log.info("**** 跨模块调用 开始: data-server.execute (download) ****");
            log.info("**************************************************");
            log.info(">>> GET /getdata data-server download request: {}", JSON.toJSONString(queryDataRequest));
            long callStart = System.currentTimeMillis();
            QueryDataResponse<GetDataSqlResponse> result = queryServerClient.execute(queryDataRequest);
            long cost = System.currentTimeMillis() - callStart;
            log.info("**************************************************");
            log.info("**** 跨模块调用 结束: data-server.execute (download), 耗时={}ms ****", cost);
            log.info("**************************************************");
            if (result == null) {
                log.error("<<< GET /getdata download 失败: data-server 返回 null, cost={}ms", cost);
                return null;
            }
            log.info("<<< GET /getdata download response: code={}, message={}, dataNull={}, cost={}ms",
                    result.getCode(), result.getMessage(), result.getData() == null, cost);
            if (result.getCode() != 200) {
                log.error("<<< GET /getdata download 失败: code={}, message={}", result.getCode(), result.getMessage());
                return null;
            }
            if (result.getData() == null) {
                log.error("<<< GET /getdata download 失败: data-server code=200 但 data 为 null");
                return null;
            }
            GetDataSqlResponse data = result.getData();
            log.info("<<< GET /getdata download data 详情: columns={}, recordsSize={}, sql={}",
                    data.getColumns() != null ? data.getColumns().size() : 0,
                    data.getRecords() != null ? data.getRecords().size() : 0,
                    data.getSql());
            writeExcel(response, data);
            return null;
        }

        log.info("**************************************************");
        log.info("**** 跨模块调用 开始: data-server.execute (query) ****");
        log.info("**************************************************");
        log.info(">>> GET /getdata data-server request: {}", JSON.toJSONString(queryDataRequest));
        long callStart = System.currentTimeMillis();
        QueryDataResponse<GetDataSqlResponse> result = queryServerClient.execute(queryDataRequest);
        long cost = System.currentTimeMillis() - callStart;
        log.info("**************************************************");
        log.info("**** 跨模块调用 结束: data-server.execute (query), 耗时={}ms ****", cost);
        log.info("**************************************************");
        log.info("<<< GET /getdata response: {}, cost={}ms", JSON.toJSONString(result), cost);
        return result;
    }

    private String compareTypeToOperator(Integer compareType) {
        if (compareType == null) return "=";
        switch (compareType) {
            case 0: return ">";
            case 1: return "<";
            case 2: return "=";
            case 3: return ">=";
            case 4: return "<=";
            case 5: return "in";
            case 6: return "notin";
            case 7: return "!=";
            case 8: return "like";
            case 9: return "notlike";
            default: return "=";
        }
    }

    private Integer convertOperatorToInt(String operator) {
        if (operator == null) return 2;
        switch (operator) {
            case ">": return 0;
            case "<": return 1;
            case "=": return 2;
            case ">=": return 3;
            case "<=": return 4;
            case "in": return 5;
            case "notin": return 6;
            case "!=": return 7;
            case "like": return 8;
            case "notlike": return 9;
            default: return 2;
        }
    }


    /**
     * ai查询实时返回接口
     *
     * @param
     * @return
     */
    @RequestMapping(value = "/getdata/ai", method = RequestMethod.POST)
    public JSONObject getDataForAi(@RequestBody Map<String, Object> param) {
        String requestId = param.get("request_id") != null ? param.get("request_id").toString() : "";
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("request_id不能为空");
        }
        String[] ids = requestId.split("@");
        MDC.put("trace_id",ids[1]);
        log.info("======================================查询数据请求报文：{}",JSON.toJSONString(param));
        // 从Redis获取用户信息
        String userKey = requestId + "-user";
        String userJson = stringRedisTemplate.opsForValue().get(userKey);
        if (StringUtils.isBlank(userJson)) {
            throw new IllegalStateException("用户信息已过期，请重新发起会话, requestId=" + requestId);
        }
        SaasUser user = JSON.parseObject(userJson, SaasUser.class);
        if (user == null) {
            throw new IllegalStateException("用户信息反序列化失败, requestId=" + requestId);
        }
        UserThreadLocal.set(user);
        String codeKey = requestId + "-code";
        String code = stringRedisTemplate.opsForValue().get(codeKey);
        if (StringUtils.isBlank(code)) {
            throw new IllegalStateException("查询阶段没有获取正常的智能体code，请重新发起会话, requestId=" + requestId);
        }

        // 3. 获取query（你原代码中已有的字段）
        String query = param.get("query") != null ? param.get("query").toString() : "";
        // 4. 获取query_description
        String queryDescription = param.get("query_description") != null ? param.get("query_description").toString() : "";
        // 2. 获取step_id（字符串类型）
        String stepId = param.get("step_id") != null ? param.get("step_id").toString() : "";
        String[] stepids = stepId.split("_");
        String chatSessionId=ids[0];
        String chatId=ids[1];
        String itemId=stepids[1];
        AIChatVO chatVO=new AIChatVO();
        chatVO.setChatSessionId(chatSessionId);
        chatVO.setChatId(chatId);
        chatVO.setQuestion(queryDescription);
        chatVO.setItemId(Integer.valueOf(itemId));
        // 从expected_columns解析指标维度和filter
        JSONArray expectedColumns = (JSONArray) JSON.toJSON(param.get("expected_columns"));
        List<String> expectedColumnNames = new ArrayList<>();
        List<Long> dimensionIds = new ArrayList<>();
        List<Long> indicatorIds = new ArrayList<>();
        List<QueryDataRequest.Filter> queryFilters = new ArrayList<>();
        for (int i = 0; i < expectedColumns.size(); i++) {
            JSONObject colNode = expectedColumns.getJSONObject(i);
            String name = colNode.getString("name");
            if (StringUtils.isNotBlank(name)) {
                expectedColumnNames.add(name);
            }
            // 处理filter
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
                    qf.setOperator(fObj.getString("operator"));
                    JSONArray valuesArr = fObj.getJSONArray("values");
                    String operator = fObj.getString("operator");
                    if (valuesArr != null && valuesArr.size() > 1 && "=".equals(operator)) {
                        operator = "in";
                    }
                    qf.setOperator(operator);
                    qf.setValues(valuesArr != null ? valuesArr.toJavaList(String.class) : Collections.emptyList());
                    queryFilters.add(qf);
                }
            }
        }

        // ===== 【临时逻辑】expected_columns 中的指标组合（groupcode）展开 =====
        // 指标/维度的 englishname 可能是 olap_report_group 的组合编码（group_code）：
        // 命中已上线组合时，把组合配置(group_config)里的 indicators/dimensions 展开成 olap_basic_pro，
        // 与普通指标/维度合并后一起传给 data-server。组合配置里的 timeRange/filters/sorts 等暂不生效。
        // 后续若改为正式功能，与 /group/preview 的配置口径对齐。
        List<OlapBasicPro> groupExpandedPros = new ArrayList<>();
        Set<String> groupCodes = new HashSet<>();
        Map<Long, String> groupIndicatorComparison = new HashMap<>();
        if (user != null && user.getTenantId() != null) {
            for (int i = expectedColumns.size() - 1; i >= 0; i--) {
                JSONObject colNode = expectedColumns.getJSONObject(i);
                String name = colNode.getString("name");
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                OlapReportGroup group;
                try {
                    group = olapReportGroupMapper.getOnlineByCode(name, user.getTenantId());
                } catch (Exception e) {
                    log.warn("[临时逻辑] 查询指标组合失败: name={}, error={}", name, e.getMessage());
                    continue;
                }
                if (group == null) {
                    continue;
                }
                List<Long> memberIds = parseGroupMemberIds(group);
                Map<Long, String> groupComparison = parseGroupIndicatorComparison(group);
                if (MapUtils.isNotEmpty(groupComparison)) {
                    groupIndicatorComparison.putAll(groupComparison);
                }
                if (CollectionUtils.isNotEmpty(memberIds)) {
                    List<OlapBasicPro> members = olapBasicProService.listByIds(memberIds);
                    if (CollectionUtils.isNotEmpty(members)) {
                        Set<Long> merged = groupExpandedPros.stream().map(OlapBasicPro::getId).collect(Collectors.toSet());
                        for (OlapBasicPro pro : members) {
                            if (pro.getId() != null && merged.add(pro.getId())) {
                                groupExpandedPros.add(pro);
                            }
                        }
                    }
                } else {
                    log.warn("[临时逻辑] 指标组合无有效成员: code={}, id={}, 跳过展开", name, group.getId());
                }
                groupCodes.add(name);
                expectedColumns.remove(i);
                log.info("[临时逻辑] 指标组合展开: code={}, id={}, 成员数={}, requestId={}",
                        name, group.getId(), memberIds.size(), requestId);
            }
        }
        if (!groupCodes.isEmpty()) {
            expectedColumnNames.removeAll(groupCodes);
            queryFilters.removeIf(qf -> groupCodes.contains(qf.getField()));
        }
        // ===== 【临时逻辑】结束 =====

        List<OlapBasicPro> basicProList;
        QueryDataRequest queryDataRequest = new QueryDataRequest();
        QueryDataResponse<GetDataSqlResponse> queryDataResponse = null;
        // 问答质量管理：step trace 证据
        long stepTraceStart = System.currentTimeMillis();
        Long traceResolvedTableId = null;

        // time_range 两种路径都解析（必须在 if-else 之前，因为 else 分支内会调 data-server）
        if (param.get("time_range") != null) {
            QueryDataRequest.TimeRange tr = JSON.parseObject(
                    JSON.toJSONString(param.get("time_range")), QueryDataRequest.TimeRange.class);
            queryDataRequest.setTimeRange(tr);
        }

        if (CollectionUtils.isEmpty(expectedColumnNames) && CollectionUtils.isEmpty(groupExpandedPros)) {
            basicProList = Collections.emptyList();
            log.warn("expectedColumnNames为空，跳过data-server查询, requestId={}", requestId);
        } else {
            // 校验 expected_table 与维度的匹配关系，处理 org 维度互斥替换
            String expectedTable = param.get("expected_table") != null ? param.get("expected_table").toString() : "";
            Long resolvedTableId = resolveTableColumns(expectedColumns, expectedColumnNames, expectedTable, queryFilters);
            traceResolvedTableId = resolvedTableId;

            // 按english_name查olap_basic_pro，区分维度和指标
            basicProList = new ArrayList<>(olapBasicProService.getBasicProByName(expectedColumnNames));
            // 【临时逻辑】合并指标组合展开出的指标/维度（按id去重）
            if (CollectionUtils.isNotEmpty(groupExpandedPros)) {
                Set<Long> seenIds = basicProList.stream().map(OlapBasicPro::getId).collect(Collectors.toSet());
                for (OlapBasicPro pro : groupExpandedPros) {
                    if (pro.getId() != null && seenIds.add(pro.getId())) {
                        basicProList.add(pro);
                    }
                }
            }
            // 【临时逻辑】结束
            for (OlapBasicPro pro : basicProList) {
                if (pro.getCategory() == null) continue;
                if (pro.getCategory() == 1) {
                    dimensionIds.add(pro.getId());
                } else if (pro.getCategory() == 2) {
                    indicatorIds.add(pro.getId());
                }
            }
            queryDataRequest.setDimensionIds(dimensionIds);
            queryDataRequest.setIndicatorIds(indicatorIds);
            if (resolvedTableId != null) {
                queryDataRequest.setPreferredTableIds(Collections.singletonList(resolvedTableId));
            } else {
                queryDataRequest.setPreferredTableIds(aiBodyService.getPreferredTableIds(code));
            }
            queryDataRequest.setPreferredModelIds(aiBodyService.getPreferredModelIds(code));
            queryDataRequest.setFilters(queryFilters);
            // 无排序且维度包含时间维度时，默认按最细粒度时间维度 asc 排序
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
            // ===== 【临时逻辑】问数路径同环比（以指标组合配置为准）=====
            // 同环比是否加、加哪些指标，跟随指标组合(group_config.indicatorComparison)：
            // 1. 请求显式传了 indicatorComparison → 以请求为准，不做任何默认补充；
            // 2. 请求未传 → 使用指标组合展开时收集的对比配置（"none" 已过滤，视为不加）；
            // 3. 最终存在对比配置时，若 dateGranularity 为空：有时间维度按最细时间维度推断
            //    （ptdate→day、ptweek→week、ptmonth→month、ptquarter→quarter、ptyear→year），
            //    无时间维度但有 timeRange 用 year。
            if ((queryDataRequest.getIndicatorComparison() == null || queryDataRequest.getIndicatorComparison().isEmpty())
                    && MapUtils.isNotEmpty(groupIndicatorComparison)) {
                queryDataRequest.setIndicatorComparison(new HashMap<>(groupIndicatorComparison));
                log.info("[临时逻辑] 应用指标组合同环比配置: {}, requestId={}",
                        JSON.toJSONString(groupIndicatorComparison), requestId);
            }
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
                log.info("[临时逻辑] 同环比时间粒度推断: dateGranularity={}, requestId={}",
                        queryDataRequest.getDateGranularity(), requestId);
            }
            // ===== 【临时逻辑】结束 =====
            QueryDataRequest.Paging paging = new QueryDataRequest.Paging();
            paging.setPage(pagingProperties.getDefaultPage());
            paging.setPageSize(pagingProperties.getAiPageSize());
            queryDataRequest.setPaging(paging);
            log.info("**************************************************");
            log.info("**** 跨模块调用 开始: data-server.execute (getdata/ai) ****");
            log.info("**************************************************");
            log.info("查询data-server的请求：{}", JSON.toJSONString(queryDataRequest));
            long callStart = System.currentTimeMillis();
            try {
                queryDataResponse = queryServerClient.execute(queryDataRequest);
            } catch (Exception e) {
                log.error("**************************************************");
                log.error("**** 跨模块调用 异常: data-server.execute (getdata/ai), 耗时={}ms ****", System.currentTimeMillis() - callStart);
                log.error("**************************************************");
                log.error("queryServerClient.execute调用失败", e);
            }
            log.info("**************************************************");
            log.info("**** 跨模块调用 结束: data-server.execute (getdata/ai), 耗时={}ms ****", System.currentTimeMillis() - callStart);
            log.info("**************************************************");
        }
//        //给页面整理指标维度和过滤器，以及时间过滤的数据。为了兼容老页面。
//        List<FilterDTO> oldFilters = transFilters(expectedColumns, basicProList);
//        DateDimTypeDTO dateDto = transDate(param.get("time_range").toString());
//        //拿到指标列表，拿到维度列表。
        DataRequestDTO dataRequestDTO = new DataRequestDTO();
        // 从basicProList补充dimList和indexList
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
        dataRequestDTO.setDimList(dims);
        dataRequestDTO.setIndexList(idxs);
        // 补充时间范围
        if (queryDataRequest.getTimeRange() != null) {
            DataRequestDTO.TimeRangeDTO tr = new DataRequestDTO.TimeRangeDTO();
            tr.setStart(queryDataRequest.getTimeRange().getStart());
            tr.setEnd(queryDataRequest.getTimeRange().getEnd());
            dataRequestDTO.setTimeRange(tr);
        }
        // 补充过滤条件
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
                fd.setOperator(convertOperatorToInt(qf.getOperator()));
                fd.setFilterValue(qf.getValues());
                filterDTOs.add(fd);
            }
            dataRequestDTO.setFilters(filterDTOs);
        }

        //更新历史
        String stepType="query";
        olapDataService.updateChatRecord(chatVO,dataRequestDTO,queryDataResponse, stepType);

        // 问答质量管理：落 chat_step_trace（非致命）
        try {
            ChatStepTrace st = new ChatStepTrace();
            st.setChatId(chatId);
            st.setStepId(stepId);
            st.setQueryDescription(StringUtils.abbreviate(queryDescription, 1000));
            Object expectedTableObj = param.get("expected_table");
            st.setExpectedTable(expectedTableObj == null ? null : expectedTableObj.toString());
            st.setExpectedColumns(JSON.toJSONString(param.get("expected_columns")));
            st.setResolvedTableId(traceResolvedTableId);
            if (traceResolvedTableId != null) {
                st.setResolvedTableName(expectedTableObj == null ? null : expectedTableObj.toString());
            }
            st.setResolvedIndicatorIds(JSON.toJSONString(idxs.stream().map(IndexDTO::getId).collect(Collectors.toList())));
            st.setResolvedIndicatorNames(JSON.toJSONString(idxs.stream().map(IndexDTO::getIndName).collect(Collectors.toList())));
            st.setResolvedDimensionIds(JSON.toJSONString(dims.stream().map(DimDTO::getId).collect(Collectors.toList())));
            st.setResolvedDimensionNames(JSON.toJSONString(dims.stream().map(DimDTO::getDimName).collect(Collectors.toList())));
            Set<String> matched = basicProList.stream().map(OlapBasicPro::getEnglishName).collect(Collectors.toSet());
            List<String> unmatched = expectedColumnNames.stream().filter(n -> !matched.contains(n)).collect(Collectors.toList());
            st.setUnmatchedColumns(JSON.toJSONString(unmatched));
            st.setFilters(JSON.toJSONString(queryFilters));
            st.setTimeRange(queryDataRequest.getTimeRange() == null ? null : JSON.toJSONString(queryDataRequest.getTimeRange()));
            st.setGroupExpanded(groupExpandedPros.isEmpty() ? 0 : 1);
            st.setElapsedMs((int) (System.currentTimeMillis() - stepTraceStart));
            if (queryDataResponse != null && queryDataResponse.getCode() == 200 && queryDataResponse.getData() != null) {
                st.setSqlText(queryDataResponse.getData().getSql());
                st.setRowCount(queryDataResponse.getData().getRecords() == null ? 0 : queryDataResponse.getData().getRecords().size());
            } else if (queryDataResponse != null) {
                st.setErrorMessage(StringUtils.abbreviate(queryDataResponse.getMessage(), 1000));
            } else if (!expectedColumnNames.isEmpty()) {
                st.setErrorMessage("data-server响应为空");
            }
            chatTraceService.saveStepTrace(st);
        } catch (Exception traceErr) {
            log.warn("[trace] step trace build failed (non-fatal): {}", traceErr.getMessage());
        }

        JSONObject resultObj=new JSONObject();
        log.info("[data-server] {}", DS_SEP);
        if(null != queryDataResponse && queryDataResponse.getCode()==200){
            GetDataSqlResponse dataRes = queryDataResponse.getData();
            if (dataRes.getRecords() == null || dataRes.getRecords().isEmpty()) {
                log.warn("[data-server] 查询: {} | 结果: ⚠️  EMPTY, 返回 0 条", queryDescription);
            } else {
                List<Map<String, Object>> records = dataRes.getRecords();
                int previewSize = Math.min(5, records.size());
                log.info("[data-server] 查询: {} | 结果: ✅ SUCCESS, 返回 {} 条", queryDescription, records.size());
                log.info("[data-server] 数据预览(前{}/{}): {}", previewSize, records.size(),
                        buildCompactPreview(records, previewSize));
            }
            resultObj = buildAiResultJson(dataRes, requestId, stepId);
        } else {
            String errMsg = queryDataResponse != null ? queryDataResponse.getMessage() : "data-server响应为空";
            log.error("[data-server] 查询: {} | 结果: ❌ ERROR, msg={}", queryDescription, errMsg);
            resultObj.put("status", "fail");
            resultObj.put("error_message", errMsg);
        }
        log.info("[data-server] {}", DS_SEP);
        return resultObj;
    }

    /**
     * 构建 getdata/ai 接口返回的 JSON，将 data-server 的 ColumnMeta 转为 columns/rows 格式
     */
    private JSONObject buildAiResultJson(GetDataSqlResponse dataRes, String requestId, String stepId) {
        JSONObject resultObj = new JSONObject();
        resultObj.put("request_id", requestId);
        resultObj.put("step_id", stepId);
        resultObj.put("status", "success");
        resultObj.put("error_message", "");
        // columns
        JSONArray tableColumns = new JSONArray();
        for (ColumnMeta col : dataRes.getColumns()) {
            JSONObject column = new JSONObject();
            column.put("name", col.getKey());
            column.put("description", col.getName());
            column.put("data_type", "string");
            column.put("unit", col.getUnit() != null ? col.getUnit() : "");
            tableColumns.add(column);
        }
        // rows
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

    private String buildCompactPreview(List<Map<String, Object>> records, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("; ");
            Map<String, Object> row = records.get(i);
            boolean first = true;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    private void writeExcel(HttpServletResponse response, GetDataSqlResponse data) throws IOException {
        List<ColumnMeta> columns = data.getColumns();
        List<Map<String, Object>> records = data.getRecords();

        // 表头（中文名）
        List<List<String>> headers = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(columns)) {
            for (ColumnMeta col : columns) {
                headers.add(Collections.singletonList(
                        StringUtils.isNotBlank(col.getName()) ? col.getName() : col.getKey()));
                keys.add(col.getKey());
            }
        }

        // 数据行
        List<List<Object>> rows = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(records)) {
            for (Map<String, Object> record : records) {
                List<Object> row = new ArrayList<>();
                for (String key : keys) {
                    row.add(record.get(key));
                }
                rows.add(row);
            }
        }

        log.info("writeExcel: headers={}, rows={}", headers.size(), rows.size());
        String fileName = "数据导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));
        EasyExcel.write(response.getOutputStream()).head(headers).sheet("数据").doWrite(rows);
        log.info("writeExcel 完成: fileName={}", fileName);
    }

    private void transIndexAndDim(List<OlapBasicPro> basicProList, DataRequestDTO olapTableDataRequestDTO) {
        List<DimDTO>  dims=new ArrayList<>();
        List<IndexDTO> indexs=new ArrayList<>();
        for (OlapBasicPro basicProDTO : basicProList) {
            if (basicProDTO.getCategory() == null) {
                continue;
            }
            if (basicProDTO.getCategory() == 1) {
                DimDTO dim = new DimDTO();
                dim.setId(basicProDTO.getId());
                dim.setDimKey(basicProDTO.getEnglishName());
                dim.setDimName(basicProDTO.getChineseName());
                dims.add(dim);
            } else if (basicProDTO.getCategory() == 2) {
                IndexDTO index = new IndexDTO();
                index.setId(basicProDTO.getId());
                index.setIndKey(basicProDTO.getEnglishName());
                index.setIndName(basicProDTO.getChineseName());
                index.setDecimalPoint(-1);
                index.setFieldStyle(0);
                indexs.add(index);
            }
        }
        olapTableDataRequestDTO.setIndexList(indexs);
        olapTableDataRequestDTO.setDimList(dims);
    }

    private DateDimTypeDTO transDate(String timeRange) {
        DateDimTypeDTO dataDto = new DateDimTypeDTO();
        dataDto.setTimeType(2);
        dataDto.setTimeFilterType("ptdate");

        if (timeRange == null
                || timeRange.trim().isEmpty()
                || "{}".equals(timeRange.trim())) {
            log.info("timeRange为空，不进行时间解析");
        } else {
            String beginT = timeRange.split("start=")[1].split(",")[0].trim();
            String endT = timeRange.split("end=")[1].split("}")[0].trim();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            long beginMillis = LocalDate.parse(beginT, formatter)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            long endMillis = LocalDate.parse(endT, formatter)
                    .atTime(23, 59, 59, 999_000_000)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            List<Object> data = new ArrayList<>();
            data.add(beginMillis);
            data.add(endMillis);
            dataDto.setData(data);}
        return dataDto;
    }

    private List<FilterDTO> transFilters(JSONArray expectedColumns, List<OlapBasicPro> basicProList) {
        // 7. 获取expected_columns
        //把expected_columns里的filter拍平成一行一条
        List<JSONObject> expectedColumnFilterList = new ArrayList<>();
        List<String> expectedColumnNames = new ArrayList<>();
        for (int i = 0; i < expectedColumns.size(); i++) {
            JSONObject colNode = expectedColumns.getJSONObject(i);
            String name = colNode.getString("name");
            if (StringUtils.isNotBlank(name)) {
                expectedColumnNames.add(name);
            }
            //filter处理
//            JSONArray columnFilters = colNode.getJSONArray("filter");
            Object filterNode = colNode.get("filter");
            JSONArray columnFilters = new JSONArray();
            if (filterNode instanceof JSONArray) {
                columnFilters = (JSONArray) filterNode;
            } else if (filterNode instanceof JSONObject) {
                columnFilters.add(filterNode);
            }
            if (CollectionUtils.isNotEmpty(columnFilters)) {
                for (int j = 0; j < columnFilters.size(); j++) {
                    JSONObject filterObj = columnFilters.getJSONObject(j);
                    if (filterObj == null) {
                        continue;
                    }
                    JSONObject filterLine = new JSONObject(new LinkedHashMap<>());
                    filterLine.put("column", name);
                    filterLine.put("operator", filterObj.getString("operator"));
                    filterLine.put("values", filterObj.getJSONArray("values"));
                    expectedColumnFilterList.add(filterLine);
                }
            }
        }
        log.info("兼容数组和obj的的filter列表：{}", JSON.toJSONString(expectedColumnFilterList));
        //转换filter
        List<FilterDTO> filterList=new ArrayList<>();
        if (CollectionUtils.isNotEmpty(expectedColumnFilterList)) {
            for (int i = 0; i < expectedColumnFilterList.size(); i++) {
                JSONObject filterObj = expectedColumnFilterList.get(i);
                if (filterObj == null) {
                    continue;
                }
                FilterDTO filter = new FilterDTO();
                String column=filterObj.getString("column");
                OlapBasicPro olapBasicProDTO = null;
                if (CollectionUtils.isNotEmpty(basicProList)) {
                    for(OlapBasicPro olapBasicPro:basicProList){
                        if(column.equals(olapBasicPro.getEnglishName())){
                            olapBasicProDTO = olapBasicPro;
                        }
                    }
                }
                FilterFieldDTO indexKeyDTO = new FilterFieldDTO();
                indexKeyDTO.setId(olapBasicProDTO.getId());
                indexKeyDTO.setKey(column);
                //下面一行是中文名
                indexKeyDTO.setName(olapBasicProDTO.getChineseName());
                //下面一行是判断它是维度还是指标,FieldClazz的枚举值是0维度1指标，category的分类是 1 维度 2 指标**/
                indexKeyDTO.setFieldClazz(olapBasicProDTO.getCategory()-1);
                filter.setFilterField(indexKeyDTO);
                //对面能不能拿到id
                String op=filterObj.getString("operator");
                //op处理成compareType
                filter.setOperator(OperatorConverter.convertOperator(op));
                // (value = "逻辑操作运算符 ， 0 :and , 1: or , 2: not")
                filter.setLogicType(0);
                //处理value
                Object valueObj = filterObj.get("values");
                if (valueObj instanceof JSONArray) {
                    //多值情况下转为in
                    if(op.equals("=")){
                        filter.setOperator(OperatorConverter.convertOperator("in"));
                    }
                    JSONArray valueArray = (JSONArray) valueObj;
                    filter.setFilterValue(valueArray.toJavaList(String.class));
                    // 按数组处理
                } else {
                    // 按单个值处理
                    filter.setFilterValue(Collections.singletonList(valueObj.toString()));
                }
                filterList.add(filter);
            }
        }
        log.info("解析后的filter参数：{}",JSON.toJSONString(filterList));
        return filterList;
    }

    // ========== 维度-表校验 & org 维度互斥替换 ==========

    private static final Set<String> ORG_DIM_KEYS = Set.of("gdj", "city_org_name", "dis_org_name", "branch_org_name");

    /**
     * 校验 expected_columns 中的字段是否属于 expected_table，
     * 处理 gdj 与 city/dis/branch_org_name 的互斥替换。
     *
     * @return tableId 校验通过（含替换后通过），null 表示应降级走原逻辑
     */
    /**
     * 【临时逻辑】解析指标组合 group_config 里的 indicatorComparison（key=指标id，value=none/pop/yoy/both）。
     * "none" 视为不加对比，直接过滤。
     */
    private Map<Long, String> parseGroupIndicatorComparison(OlapReportGroup group) {
        Map<Long, String> result = new HashMap<>();
        try {
            JSONObject config = JSON.parseObject(group.getGroupConfig());
            JSONObject comparison = config.getJSONObject("indicatorComparison");
            if (comparison != null) {
                for (String key : comparison.keySet()) {
                    String value = comparison.getString(key);
                    if (StringUtils.isBlank(value) || "none".equalsIgnoreCase(value)) {
                        continue;
                    }
                    try {
                        result.put(Long.parseLong(key), value);
                    } catch (NumberFormatException e) {
                        log.warn("[临时逻辑] 指标组合对比配置id非法: key={}, groupId={}", key, group.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[临时逻辑] 指标组合对比配置解析失败: id={}, error={}", group.getId(), e.getMessage());
        }
        return result;
    }

    /**
     * 【临时逻辑】解析指标组合 group_config 里的 indicators/dimensions 成员 id 列表
     */
    private List<Long> parseGroupMemberIds(OlapReportGroup group) {
        List<Long> ids = new ArrayList<>();
        try {
            JSONObject config = JSON.parseObject(group.getGroupConfig());
            JSONArray indicators = config.getJSONArray("indicators");
            if (CollectionUtils.isNotEmpty(indicators)) {
                for (int i = 0; i < indicators.size(); i++) {
                    Long id = indicators.getJSONObject(i).getLong("id");
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            JSONArray dimensions = config.getJSONArray("dimensions");
            if (CollectionUtils.isNotEmpty(dimensions)) {
                for (int i = 0; i < dimensions.size(); i++) {
                    Long id = dimensions.getJSONObject(i).getLong("id");
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[临时逻辑] 指标组合配置解析失败: id={}, error={}", group.getId(), e.getMessage());
        }
        return ids;
    }

    private Long resolveTableColumns(JSONArray expectedColumns, List<String> expectedColumnNames,
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
        log.info("表字段校验: expected_table='{}', columns={}, tableFields={}", expectedTable, expectedColumnNames, tableFields);

        List<String> mismatched = expectedColumnNames.stream()
                .filter(n -> !tableFields.contains(n))
                .collect(Collectors.toList());

        if (mismatched.isEmpty()) {
            log.info("维度校验通过: expected_table='{}', tableId={}", expectedTable, tableId);
            return tableId;
        }

        // 尝试 org 维度互斥替换
        boolean resolved = resolveOrgMismatch(mismatched, tableFields,
                expectedColumns, expectedColumnNames, queryFilters);
        if (!resolved) {
            log.warn("维度校验失败，不匹配字段: {}，表 '{}' 不包含这些字段，降级走原逻辑", mismatched, expectedTable);
            return null;
        }

        // 替换后重校验
        List<String> stillMismatched = expectedColumnNames.stream()
                .filter(n -> !tableFields.contains(n))
                .collect(Collectors.toList());
        if (!stillMismatched.isEmpty()) {
            log.warn("org维度替换后仍有不匹配字段: {}，降级走原逻辑", stillMismatched);
            return null;
        }
        log.info("org维度替换后校验通过: expected_table='{}', tableId={}", expectedTable, tableId);
        return tableId;
    }

    /**
     * 处理 gdj 与 city/dis/branch_org_name 的互斥关系，直接修改数据结构。
     * gdj 有多个 filter value 时可能拆分成多个 org 维度列。
     *
     * @return true 替换/拆分/合并成功，false 无法处理
     */
    private boolean resolveOrgMismatch(List<String> mismatched, Set<String> tableFields,
                                        JSONArray expectedColumns, List<String> expectedColumnNames,
                                        List<QueryDataRequest.Filter> queryFilters) {
        for (String name : mismatched) {
            if (!ORG_DIM_KEYS.contains(name)) {
                continue;
            }
            if ("gdj".equals(name)) {
                // SPLIT: gdj → city/dis/branch_org_name（根据 filter value 拆分）
                return splitGdjToOrgColumns(expectedColumns, expectedColumnNames, queryFilters, tableFields);
            } else if (tableFields.contains("gdj")) {
                // MERGE: city/dis/branch_org_name → gdj
                return mergeOrgColumnsToGdj(expectedColumns, expectedColumnNames, queryFilters, name);
            }
        }
        return false;
    }

    /**
     * 拆分 gdj → city/dis/branch_org_name，根据每个 filter value 判定目标维度 key。
     */
    private boolean splitGdjToOrgColumns(JSONArray expectedColumns, List<String> expectedColumnNames,
                                          List<QueryDataRequest.Filter> queryFilters, Set<String> tableFields) {
        // 找到 gdj 列，提取所有 filter values
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
        if (gdjCol == null) {
            return false;
        }

        List<String> allFilterValues = extractFilterValues(gdjCol);
        log.info("gdj 拆分开始: filterValues={}", allFilterValues);
        if (allFilterValues.isEmpty()) {
            // 无 filter value 时 1→3：三个 org 维全建，都不带 filter
            expectedColumns.remove(gdjIdx);
            expectedColumnNames.remove("gdj");
            queryFilters.removeIf(f -> "gdj".equals(f.getField()));
            boolean anyAdded = addOrgColumnNoFilter(expectedColumns, expectedColumnNames, "city_org_name", tableFields);
            anyAdded |= addOrgColumnNoFilter(expectedColumns, expectedColumnNames, "dis_org_name", tableFields);
            anyAdded |= addOrgColumnNoFilter(expectedColumns, expectedColumnNames, "branch_org_name", tableFields);
            log.info("gdj 拆分完成(无filter): 替换前 维度=gdj(无值) → 替换后 city/dis/branch_org_name(无值), 生效列数={}", anyAdded ? 1 : 0);
            return anyAdded;
        }

        // 按映射规则分组
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

        // 删除 gdj 列
        expectedColumns.remove(gdjIdx);
        expectedColumnNames.remove("gdj");
        queryFilters.removeIf(f -> "gdj".equals(f.getField()));

        // 为每个有值且表支持的目标 org key 新增列
        boolean anyAdded = addOrgColumn(expectedColumns, expectedColumnNames, queryFilters,
                "city_org_name", cityValues, tableFields);
        anyAdded |= addOrgColumn(expectedColumns, expectedColumnNames, queryFilters,
                "dis_org_name", disValues, tableFields);
        anyAdded |= addOrgColumn(expectedColumns, expectedColumnNames, queryFilters,
                "branch_org_name", branchValues, tableFields);

        if (anyAdded) {
            log.info("gdj 拆分完成: 替换前 维度=gdj, 值={}"
                    + " | 替换后 city_org_name={}, dis_org_name={}, branch_org_name={}, 未识别={}",
                    allFilterValues, cityValues, disValues, branchValues, unknownValues);
        }
        if (!unknownValues.isEmpty()) {
            log.warn("gdj 拆分: 以下 filter value 无法识别 org 层级，已丢弃: {}", unknownValues);
        }
        return anyAdded;
    }

    /**
     * 合并 city/dis/branch_org_name → gdj，收集所有 org 列的 filter value 合并到一个 gdj 列。
     */
    private boolean mergeOrgColumnsToGdj(JSONArray expectedColumns, List<String> expectedColumnNames,
                                          List<QueryDataRequest.Filter> queryFilters, String firstMismatched) {
        List<String> mergedValues = new ArrayList<>();
        List<String> removedDims = new ArrayList<>();
        // 找到所有 city/dis/branch_org_name 列并收集 filter values
        for (int i = expectedColumns.size() - 1; i >= 0; i--) {
            JSONObject col = expectedColumns.getJSONObject(i);
            String colName = col.getString("name");
            if ("city_org_name".equals(colName) || "dis_org_name".equals(colName) || "branch_org_name".equals(colName)) {
                List<String> vals = extractFilterValues(col);
                log.info("gdj 合并: {} (filterValues={}) → gdj", colName, vals);
                mergedValues.addAll(vals);
                removedDims.add(colName);
                expectedColumns.remove(i);
            }
        }
        if (removedDims.isEmpty()) {
            return false;
        }
        // 更新 expectedColumnNames
        expectedColumnNames.removeAll(removedDims);
        expectedColumnNames.add("gdj");
        // 更新 queryFilters
        queryFilters.removeIf(f -> removedDims.contains(f.getField()));
        // 新增 gdj 列
        JSONObject gdjCol = new JSONObject();
        gdjCol.put("name", "gdj");
        gdjCol.put("role", "dimension");
        if (!mergedValues.isEmpty()) {
            JSONObject filterObj = new JSONObject();
            filterObj.put("operator", "=");
            filterObj.put("values", mergedValues);
            gdjCol.put("filter", filterObj);
            // 合并后在 queryFilters 中新增一条 gdj 的 filter
            QueryDataRequest.Filter qf = new QueryDataRequest.Filter();
            qf.setType("dimension");
            qf.setField("gdj");
            qf.setOperator(mergedValues.size() > 1 ? "in" : "=");
            qf.setValues(mergedValues);
            queryFilters.add(qf);
            // 如果有多值且在原逻辑中已有一个 gdj filter（来自之前的解析），也同步更新
            // 注意：原 expectedColumns 的 filter 解析已经在主流程完成了，这里需要重新处理
            // 已经在 queryFilters 中 remove 旧的并 add 新的，无需额外处理
        }
        expectedColumns.add(gdjCol);
        log.info("gdj 合并完成: 替换前 维度={}, 值={} | 替换后 维度=gdj, 值={}",
                removedDims, mergedValues, mergedValues);
        return true;
    }

    /**
     * 从 expected_column 的 JSON 节点中提取所有 filter values。
     */
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

    /**
     * 如果表支持该 org 维度 key 且有值，则向 expectedColumns 新增一列（带 filter）。
     */
    private boolean addOrgColumn(JSONArray expectedColumns, List<String> expectedColumnNames,
                                  List<QueryDataRequest.Filter> queryFilters,
                                  String orgKey, List<String> filterValues, Set<String> tableFields) {
        if (!tableFields.contains(orgKey) || filterValues.isEmpty()) {
            return false;
        }
        JSONObject col = new JSONObject();
        col.put("name", orgKey);
        col.put("role", "dimension");
        JSONObject filterObj = new JSONObject();
        filterObj.put("operator", "=");
        filterObj.put("values", new JSONArray(filterValues));
        col.put("filter", filterObj);
        expectedColumns.add(col);
        expectedColumnNames.add(orgKey);

        QueryDataRequest.Filter qf = new QueryDataRequest.Filter();
        qf.setType("dimension");
        qf.setField(orgKey);
        qf.setOperator(filterValues.size() > 1 ? "in" : "=");
        qf.setValues(filterValues);
        queryFilters.add(qf);
        return true;
    }

    /**
     * 如果表支持该 org 维度 key，则新增一列（不带 filter）。
     */
    private boolean addOrgColumnNoFilter(JSONArray expectedColumns, List<String> expectedColumnNames,
                                          String orgKey, Set<String> tableFields) {
        if (!tableFields.contains(orgKey)) {
            return false;
        }
        JSONObject col = new JSONObject();
        col.put("name", orgKey);
        col.put("role", "dimension");
        col.put("filter", new JSONArray());
        expectedColumns.add(col);
        expectedColumnNames.add(orgKey);
        return true;
    }
}
