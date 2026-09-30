package com.wm.semantic.service.impl;

import com.wm.semantic.common.config.DataSourceManager;
import com.wm.semantic.common.context.TenantContextHolder;
import com.wm.semantic.common.enums.ComparisonType;
import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.CalculatedIndicatorMeta;
import com.wm.semantic.dto.ColumnMeta;
import com.wm.semantic.dto.DataSourceInfo;
import com.wm.semantic.dto.DerivativeFilter;
import com.wm.semantic.dto.DerivativeIndicatorMeta;
import com.wm.semantic.dto.FieldMappingInfo;
import com.wm.semantic.dto.GetDataSqlResponse;
import com.wm.semantic.dto.IndicatorMeta;
import com.wm.semantic.dto.QueryDataRequest;
import com.wm.semantic.dto.QueryDataRequest.TimeRange;
import com.wm.semantic.dto.SubIndicatorMeta;
import com.wm.semantic.entity.OlapBasicProDO;
import com.wm.semantic.entity.OlapBasicProIndicatorDO;
import com.wm.semantic.mapper.OlapBasicProMapper;
import com.wm.semantic.mapper.OlapBasicProIndicatorMapper;
import com.wm.semantic.service.SqlGenerationService;
import com.wm.semantic.service.TableSelectionService;
import com.wm.semantic.support.sql.SqlDialect;
import com.wm.semantic.support.sql.TimeRangeConverter;
import com.wm.semantic.support.sql.builder.SegmentSqlBuilder;
import com.wm.semantic.support.sql.builder.SqlGeneratorService;
import com.wm.semantic.support.sql.model.SqlBuildContext;
import com.wm.semantic.support.time.TimeComparisonMerger;
import com.wm.semantic.support.time.TimeRangeShifter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * 主流程编排：选数据源 → 构造 SqlBuildContext → 走 builder → 执行 SQL
 *
 * 重构后职责收窄：
 * 1. 不再回查任何字段映射 / 表元数据，全部由 TableSelectionService 提供
 * 2. 仅负责把 DataSourceInfo 翻译成 SqlBuildContext（构造 fieldKeyMap、拼装 where 片段）
 * 3. 调用 builder 拼 SQL、执行查询
 *
 * 两层结构：
 *  - 内层（BaseSqlBuilder）：select tX.src as field_key from db.tb t0 [join ...]
 *  - 外层（SegmentSqlBuilder）：select agg, dim from (inner) mainsrc where ... group by ...
 * 因此 WHERE 片段中的字段引用一律使用 mainsrc.field_key，不再涉及物理别名。
 */
@Slf4j
@Service
public class SqlGenerationServiceImpl implements SqlGenerationService {

    @Resource
    private TableSelectionService tableSelectionService;

    @Resource
    private DataSourceManager dataSourceManager;

    @Resource
    private SqlGeneratorService sqlGeneratorService;

    @Resource
    private OlapBasicProMapper basicProMapper;

    @Resource
    private OlapBasicProIndicatorMapper indicatorMapper;

    @Resource
    private TimeRangeShifter timeRangeShifter;

    @Resource
    private TimeComparisonMerger timeComparisonMerger;

    private static final Pattern START_TIME_PATTERN = Pattern.compile("\\$\\{\\s*start_time\\s*\\}");
    private static final Pattern END_TIME_PATTERN   = Pattern.compile("\\$\\{\\s*end_time\\s*\\}");

    @Override
    public GetDataSqlResponse generateAndExecute(QueryDataRequest request) {
        validateRequest(request);

        // 同环比分流：有指标设了对比 → 走对比流程；否则走现有流程
        Map<Long, ComparisonType> comparisonMap = buildComparisonMap(request);
        boolean hasComparison = comparisonMap.values().stream()
                .anyMatch(t -> t != ComparisonType.NONE);
        if (hasComparison) {
            return executeWithComparison(request, comparisonMap);
        }
        return executeNormal(request);
    }

    /**
     * 现有查询流程（无同环比），主体逻辑保持不变。
     */
    private GetDataSqlResponse executeNormal(QueryDataRequest request) {
        log.info("[SQL生成] ========== 开始生成并执行SQL ==========");
        long startTime = System.currentTimeMillis();

        validateRequest(request);

        // === 预处理：计算指标 + 衍生指标，展开 ID 列表 ===
        List<Long> originalIndicatorIds = new ArrayList<>(request.getIndicatorIds());
        List<Long> originalDimensionIds = request.getDimensionIds() != null
                ? new ArrayList<>(request.getDimensionIds()) : new ArrayList<Long>();

        // 粒度替换: dateGranularity 非 day 时，ptdate → 对应时间派生维度（同步替换排序引用）
        DimReplacement dimReplacement = replacePtdateByGranularity(request);

        Map<Long, CalculatedIndicatorMeta> calcMetaMap = preprocessCalculatedIndicators(request);
        Map<Long, DerivativeIndicatorMeta> derivMetaMap = preprocessDerivativeIndicators(request);

        boolean hasFormula = !calcMetaMap.isEmpty() || !derivMetaMap.isEmpty();
        Set<Long> internalDimIds = new LinkedHashSet<>();

        if (hasFormula) {
            // 展开指标 ID 列表
            Set<Long> expandedIndSet = new LinkedHashSet<>();
            for (Long id : request.getIndicatorIds()) {
                if (!calcMetaMap.containsKey(id) && !derivMetaMap.containsKey(id)) {
                    expandedIndSet.add(id);
                }
            }
            for (CalculatedIndicatorMeta cm : calcMetaMap.values()) {
                for (SubIndicatorMeta sub : cm.getSubIndicators()) {
                    expandedIndSet.add(sub.getId());
                }
            }
            for (DerivativeIndicatorMeta dm : derivMetaMap.values()) {
                expandedIndSet.add(dm.getBaseIndicatorId());
            }
            request.setIndicatorIds(new ArrayList<>(expandedIndSet));

            // 展开维度 ID 列表（衍生指标 filter 依赖的维度）
            if (!derivMetaMap.isEmpty()) {
                Set<Long> expandedDimSet = new LinkedHashSet<>(originalDimensionIds);
                for (DerivativeIndicatorMeta dm : derivMetaMap.values()) {
                    if (dm.getFilters() != null) {
                        for (DerivativeFilter df : dm.getFilters()) {
                            if (df.getDimensionId() != null
                                    && !originalDimensionIds.contains(df.getDimensionId())) {
                                expandedDimSet.add(df.getDimensionId());
                                internalDimIds.add(df.getDimensionId());
                            }
                        }
                    }
                }
                if (expandedDimSet.size() > originalDimensionIds.size()) {
                    request.setDimensionIds(new ArrayList<>(expandedDimSet));
                }
            }
        }
        // === 预处理结束 ===

        log.info("[SQL生成] Step1-选择数据源");
        List<DataSourceInfo> selectedDataSources = tableSelectionService.selectDataSource(request);
        log.info("[SQL生成] 选中数据源数量: {}", selectedDataSources.size());

        resolveViewTimeVariables(selectedDataSources, request.getTimeRange());

        log.info("[SQL生成] Step2-构造 SqlBuildContext 列表");
        List<IndicatorMeta> requestIndicators = buildRequestIndicators(request, selectedDataSources,
                calcMetaMap, derivMetaMap, originalIndicatorIds);
        Long snapshotPtdateAdded = validateSnapshotConstraints(request, requestIndicators, selectedDataSources);
        List<SqlBuildContext> contexts = buildContexts(request, selectedDataSources, requestIndicators);

        // 填充计算指标的子指标ID → fieldKey 映射 + 衍生指标的 filter fieldKey
        if (hasFormula) {
            Map<Long, String> firstFieldKeyMap = contexts.get(0).getFieldKeyMap();
            for (CalculatedIndicatorMeta cm : calcMetaMap.values()) {
                Map<Long, String> idToFieldKey = new LinkedHashMap<>();
                for (SubIndicatorMeta sub : cm.getSubIndicators()) {
                    String fieldKey = firstFieldKeyMap.get(sub.getId());
                    if (fieldKey != null) {
                        idToFieldKey.put(sub.getId(), fieldKey);
                    }
                }
                cm.setIdToFieldKey(idToFieldKey);
            }
            for (DerivativeIndicatorMeta dm : derivMetaMap.values()) {
                dm.setBaseFieldKey(firstFieldKeyMap.get(dm.getBaseIndicatorId()));
                if (dm.getFilters() != null) {
                    for (DerivativeFilter df : dm.getFilters()) {
                        df.setFieldKey(firstFieldKeyMap.get(df.getDimensionId()));
                    }
                }
            }
            if (!internalDimIds.isEmpty()) {
                for (SqlBuildContext ctx : contexts) {
                    ctx.setInternalDimIds(internalDimIds);
                }
            }
        }

        log.info("[SQL生成] Step3-生成 SQL");
        List<String> outerGroupDimensions = buildOuterGroupDimensions(contexts);
        String finalSqlWithoutPaging = sqlGeneratorService.generateFinalSql(contexts, outerGroupDimensions, requestIndicators);
        String dbDialect = contexts.get(0).getSource().getDbDialect();
        String sql = finalSqlWithoutPaging;
        if (request.getGroupTopN() != null) {
            sql = sqlGeneratorService.appendGroupTopN(sql, request.getGroupTopN(),
                    contexts.get(0).getFieldKeyMap(), dbDialect);
        }
        sql = sqlGeneratorService.appendOrderBy(sql, request.getSorts(),
                contexts.get(0).getFieldKeyMap(), dbDialect);
        sql = sqlGeneratorService.appendPaging(sql, getPage(request), getPageSize(request), dbDialect);
        log.info("[SQL生成] 构建SQL完成(无分页), sql={}", finalSqlWithoutPaging);
        log.info("[SQL生成] 构建SQL完成(分页), sql={}", sql);

        DataSource targetDs = resolveTargetDataSource(contexts);

        boolean isSimpleTopN = request.getLimit() != null;
        long total;
        if (isSimpleTopN) {
            log.info("[SQL生成] Step4-跳过COUNT(简单TopN), limit={}", request.getLimit());
            total = 0;
        } else {
            log.info("[SQL生成] Step4-查询 total");
            total = executeCount(finalSqlWithoutPaging, targetDs);
            log.info("[SQL生成] total={}", total);
        }

        log.info("[SQL生成] Step5-执行 SQL 查询");
        List<Map<String, Object>> records = executeQuery(sql, targetDs);
        log.info("[SQL生成] 查询结果 count={}", records.size());

        if (isSimpleTopN) {
            total = (long) records.size();
        }

        // 从 contexts 中收集 mapping 表的 unit（indicatorId → unit），多源取第一个非空
        Map<Long, String> mappingUnitMap = new LinkedHashMap<>();
        for (SqlBuildContext ctx : contexts) {
            if (ctx.getSource() != null && !CollectionUtils.isEmpty(ctx.getSource().getIndicatorFields())) {
                for (FieldMappingInfo f : ctx.getSource().getIndicatorFields()) {
                    if (f.getBasicId() != null && !mappingUnitMap.containsKey(f.getBasicId())) {
                        mappingUnitMap.put(f.getBasicId(), f.getUnit());
                    }
                }
            }
        }

        // 恢复原始请求用于输出列元数据（快照自动补入的 ptdate、粒度替换结果保留）
        if (hasFormula) {
            request.setIndicatorIds(originalIndicatorIds);
            List<Long> restoredDims = new ArrayList<>(originalDimensionIds);
            if (dimReplacement != null && dimReplacement.fromDimId != null) {
                restoredDims.remove(dimReplacement.fromDimId);
                restoredDims.add(dimReplacement.toDimId);
            }
            request.setDimensionIds(restoredDims);
            if (snapshotPtdateAdded != null) {
                request.getDimensionIds().add(snapshotPtdateAdded);
            }
        }
        List<ColumnMeta> columns = buildColumnMetas(request, contexts.get(0).getFieldKeyMap(),
                mappingUnitMap, calcMetaMap, derivMetaMap, internalDimIds);
        formatIndicatorValues(records, columns);

        GetDataSqlResponse response = new GetDataSqlResponse();
        response.setSql(sql);
        response.setColumns(columns);
        response.setRecords(records);
        response.setTotal(total);
        // 同环比 merge 内部信息
        response.setDateFieldKey(resolveDateFieldKey(contexts, request.getDateGranularity()));
        response.setDimensionKeys(resolveDimensionKeys(contexts, internalDimIds));
        response.setFieldKeyMap(contexts.get(0).getFieldKeyMap());
        if (request.getGroupTopN() != null) {
            response.setPage(1);
            response.setPageSize(records.size());
        } else {
            response.setPage(getPage(request));
            response.setPageSize(getPageSize(request));
        }

        log.info("[SQL生成] ========== 执行完成, 耗时{}ms ==========", System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 从 indicatorComparison Map 构建 indicatorId → ComparisonType。
     */
    private Map<Long, ComparisonType> buildComparisonMap(QueryDataRequest request) {
        Map<Long, ComparisonType> result = new LinkedHashMap<>();
        if (request.getIndicatorComparison() != null) {
            for (Map.Entry<Long, String> e : request.getIndicatorComparison().entrySet()) {
                result.put(e.getKey(), ComparisonType.from(e.getValue()));
            }
        }
        return result;
    }

    /**
     * 粒度替换：dateGranularity 非 day 时，把请求维度里的 ptdate 替换成对应时间派生维度，
     * 并同步替换 sorts / groupTopN 里的排序引用。
     *
     * @return 替换记录（from=ptdate 的 basicId, to=派生维度 basicId）；无需替换返回 null
     */
    private DimReplacement replacePtdateByGranularity(QueryDataRequest request) {
        String granularity = request.getDateGranularity();
        if (isBlank(granularity) || "day".equalsIgnoreCase(granularity.trim())) {
            return null;
        }
        List<Long> dimIds = request.getDimensionIds();
        if (CollectionUtils.isEmpty(dimIds)) {
            return null;
        }
        List<OlapBasicProDO> basics = basicProMapper.selectBatchIds(dimIds);
        Map<Long, String> englishById = new LinkedHashMap<>();
        for (OlapBasicProDO b : basics) {
            englishById.put(b.getId(), b.getEnglishName());
        }
        Long ptdateId = null;
        for (Long id : dimIds) {
            if ("ptdate".equals(englishById.get(id))) {
                ptdateId = id;
                break;
            }
        }
        if (ptdateId == null) {
            // 没有 ptdate 维度（用户选的是其他派生维度），不替换，共存
            return null;
        }
        String targetName = "pt" + granularity.trim().toLowerCase();
        OlapBasicProDO target = basicProMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OlapBasicProDO>()
                        .eq(OlapBasicProDO::getEnglishName, targetName)
                        .eq(OlapBasicProDO::getStatus, 2)
                        .last("limit 1"));
        if (target == null) {
            throw new BizException("未找到时间派生维度: " + targetName);
        }

        // 替换 dimensionIds
        List<Long> newDims = new ArrayList<>(dimIds);
        newDims.set(newDims.indexOf(ptdateId), target.getId());
        request.setDimensionIds(newDims);

        // 同步替换排序引用
        if (!CollectionUtils.isEmpty(request.getSorts())) {
            for (QueryDataRequest.Sort s : request.getSorts()) {
                if (ptdateId.equals(s.getBasicId())) {
                    s.setBasicId(target.getId());
                }
            }
        }
        // 同步替换 groupTopN 引用
        if (request.getGroupTopN() != null) {
            if (!CollectionUtils.isEmpty(request.getGroupTopN().getGroupByBasicIds())) {
                List<Long> gb = request.getGroupTopN().getGroupByBasicIds();
                for (int i = 0; i < gb.size(); i++) {
                    if (ptdateId.equals(gb.get(i))) {
                        gb.set(i, target.getId());
                    }
                }
            }
            if (!CollectionUtils.isEmpty(request.getGroupTopN().getOrderBy())) {
                for (QueryDataRequest.Sort s : request.getGroupTopN().getOrderBy()) {
                    if (ptdateId.equals(s.getBasicId())) {
                        s.setBasicId(target.getId());
                    }
                }
            }
        }
        log.info("[粒度替换] ptdate {} → {} {}, granularity={}", ptdateId, target.getId(), targetName, granularity);
        return new DimReplacement(ptdateId, target.getId());
    }

    /** 粒度替换记录。 */
    private static class DimReplacement {
        final Long fromDimId;
        final Long toDimId;

        DimReplacement(Long fromDimId, Long toDimId) {
            this.fromDimId = fromDimId;
            this.toDimId = toDimId;
        }
    }

    /**
     * 快照指标处理：请求含快照指标时，自动补入 ptdate 维度。
     * 日粒度下 sum(快照字段) = 当天快照值，现有聚合逻辑天然正确。
     *
     * @return 自动补入的 ptdate basicId；无需补入返回 null
     */
    private Long validateSnapshotConstraints(QueryDataRequest request,
                                             List<IndicatorMeta> requestIndicators,
                                             List<DataSourceInfo> selectedDataSources) {
        Map<Long, String> aggById = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(requestIndicators)) {
            for (IndicatorMeta m : requestIndicators) {
                if (m.isCalculated() || m.isDerivative()) {
                    continue;
                }
                aggById.put(m.getId(), m.getAggFunc());
            }
        }
        boolean hasSnapshot = aggById.values().stream()
                .anyMatch(a -> "snapshot".equalsIgnoreCase(a));
        if (!hasSnapshot) {
            return null;
        }

        // 快照指标仅支持日粒度（周/月/季/年粒度下 sum 多天快照值无业务意义）
        String granularity = request.getDateGranularity();
        if (!isBlank(granularity) && !"day".equalsIgnoreCase(granularity.trim())) {
            List<String> snapshotNames = new ArrayList<>();
            for (Map.Entry<Long, String> e : aggById.entrySet()) {
                if ("snapshot".equalsIgnoreCase(e.getValue())) {
                    snapshotNames.add("「" + resolveChineseName(e.getKey()) + "」(" + e.getKey() + ")");
                }
            }
            throw new BizException("快照指标" + String.join("、", snapshotNames)
                    + " 仅支持日粒度查询，当前粒度: " + granularity);
        }

        Long ptdateBasicId = null;
        if (!CollectionUtils.isEmpty(selectedDataSources) && selectedDataSources.get(0).getPtdateField() != null) {
            ptdateBasicId = selectedDataSources.get(0).getPtdateField().getBasicId();
        }
        if (ptdateBasicId == null) {
            throw new BizException("快照指标依赖时间字段，但数据源未配置 ptdate 分区字段");
        }
        if (request.getDimensionIds() == null) {
            request.setDimensionIds(new ArrayList<Long>());
        }
        if (!request.getDimensionIds().contains(ptdateBasicId)) {
            request.getDimensionIds().add(ptdateBasicId);
            log.info("[快照] 自动补入 ptdate 维度, basicId={}", ptdateBasicId);
            return ptdateBasicId;
        }
        return null;
    }

    /** 数请求里的时间维度数量（ptdate/ptweek/ptmonth/ptquarter/ptyear）。 */
    private int countTimeDimensions(QueryDataRequest request) {
        if (CollectionUtils.isEmpty(request.getDimensionIds())) {
            return 0;
        }
        List<OlapBasicProDO> basics = basicProMapper.selectBatchIds(request.getDimensionIds());
        int count = 0;
        for (OlapBasicProDO b : basics) {
            String en = b.getEnglishName();
            if (en == null) {
                continue;
            }
            String name = en.trim().toLowerCase();
            if ("ptdate".equals(name) || name.startsWith("ptweek")
                    || name.startsWith("ptmonth") || name.startsWith("ptquarter")
                    || name.startsWith("ptyear")) {
                count++;
            }
        }
        return count;
    }

    /** 查指标中文名（用于报错文案）。 */
    private String resolveChineseName(Long basicId) {
        if (basicId == null) {
            return "";
        }
        OlapBasicProDO basic = basicProMapper.selectById(basicId);
        return basic != null && !isBlank(basic.getChineseName()) ? basic.getChineseName() : "";
    }

    /**
     * 同环比执行流程：
     * 时间换算 → 并发执行本期/上期/同期 SQL → Java merge → 展开列元数据。
     */
    private GetDataSqlResponse executeWithComparison(QueryDataRequest request,
                                                     Map<Long, ComparisonType> comparisonMap) {
        if (isBlank(request.getDateGranularity())) {
            throw new BizException("同环比请求必须指定 dateGranularity");
        }
        // 同环比仅支持一个时间维度（0 个=整周期对比，1 个=逐周期对比）
        if (countTimeDimensions(request) > 1) {
            throw new BizException("同环比请求仅支持一个时间维度");
        }
        if (request.getTimeRange() == null || isBlank(request.getTimeRange().getStart())
                || isBlank(request.getTimeRange().getEnd())) {
            throw new BizException("同环比请求必须指定 timeRange");
        }
        log.info("[同环比] 开始执行, granularity={}, comparison={}",
                request.getDateGranularity(), comparisonMap);

        boolean needPop = comparisonMap.values().stream().anyMatch(ComparisonType::includesPop);
        boolean needYoy = comparisonMap.values().stream().anyMatch(ComparisonType::includesYoy);
        ComparisonType overall = needPop && needYoy ? ComparisonType.BOTH
                : needPop ? ComparisonType.POP : ComparisonType.YOY;

        // 时间换算
        TimeRange currentRange = request.getTimeRange();
        TimeRange popRange = needPop
                ? timeRangeShifter.shift(currentRange, ComparisonType.POP, request.getDateGranularity()) : null;
        TimeRange yoyRange = needYoy
                ? timeRangeShifter.shift(currentRange, ComparisonType.YOY, request.getDateGranularity()) : null;
        long popOffsetDays = needPop
                ? timeRangeShifter.popOffsetDays(currentRange, request.getDateGranularity()) : 0;

        // 并发执行 2-3 条 SQL（每条走完整现有链路）
        // 捕获当前线程的租户 ID，异步线程里手动设置，否则 MyBatis 租户拦截器取不到租户
        Long tenantId = TenantContextHolder.get();
        GetDataSqlResponse currentResp;
        GetDataSqlResponse popResp = null;
        GetDataSqlResponse yoyResp = null;
        if (needPop && needYoy) {
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f1 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(request)));
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f2 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(cloneWithRange(request, popRange))));
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f3 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(cloneWithRange(request, yoyRange))));
            try {
                currentResp = f1.get();
                popResp = f2.get();
                yoyResp = f3.get();
            } catch (Exception e) {
                throw new BizException("同环比并发执行失败: " + e.getMessage());
            }
        } else if (needPop) {
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f1 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(request)));
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f2 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(cloneWithRange(request, popRange))));
            try {
                currentResp = f1.get();
                popResp = f2.get();
            } catch (Exception e) {
                throw new BizException("同环比并发执行失败: " + e.getMessage());
            }
        } else {
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f1 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(request)));
            java.util.concurrent.CompletableFuture<GetDataSqlResponse> f2 =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> runWithTenant(tenantId, () -> executeNormal(cloneWithRange(request, yoyRange))));
            try {
                currentResp = f1.get();
                yoyResp = f2.get();
            } catch (Exception e) {
                throw new BizException("同环比并发执行失败: " + e.getMessage());
            }
        }

        // Java merge
        List<Map<String, Object>> mergedRows = timeComparisonMerger.merge(
                currentResp.getRecords(),
                popResp != null ? popResp.getRecords() : null,
                yoyResp != null ? yoyResp.getRecords() : null,
                currentResp.getDateFieldKey(),
                currentResp.getDimensionKeys(),
                resolveComparisonIndicatorKeys(currentResp, comparisonMap),
                popOffsetDays,
                overall,
                request.getDateGranularity());

        // 列元数据展开
        List<ColumnMeta> columns = expandComparisonColumns(
                currentResp.getColumns(), comparisonMap, request);

        currentResp.setRecords(mergedRows);
        currentResp.setColumns(columns);
        // total 保持 executeNormal 按未分页 SQL COUNT 出的真实总数，
        // 不能被页内合并行数覆盖，否则前端拿不到真实 total 无法翻页
        log.info("[同环比] 执行完成, 本期行数={}, 合并后行数={}",
                currentResp.getRecords().size(), mergedRows.size());
        return currentResp;
    }

    /**
     * 异步线程里设置租户上下文后执行任务，执行完清理。
     */
    private GetDataSqlResponse runWithTenant(Long tenantId, java.util.function.Supplier<GetDataSqlResponse> task) {
        try {
            TenantContextHolder.set(tenantId);
            return task.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * 克隆请求并替换 timeRange，用于对比期 SQL 执行。
     */
    private QueryDataRequest cloneWithRange(QueryDataRequest request, TimeRange range) {
        QueryDataRequest clone = new QueryDataRequest();
        clone.setDimensionIds(request.getDimensionIds() != null
                ? new ArrayList<>(request.getDimensionIds()) : null);
        clone.setIndicatorIds(request.getIndicatorIds() != null
                ? new ArrayList<>(request.getIndicatorIds()) : null);
        clone.setPreferredTableIds(request.getPreferredTableIds());
        clone.setPreferredModelIds(request.getPreferredModelIds());
        clone.setTimeRange(range);
        clone.setFilters(request.getFilters());
        // 对比期必须查全量参与 merge：不能带分页/TopN limit，
        // 否则按相同 offset 切片，位置和本期页对不上，上期值会错位或缺失
        clone.setLimit(null);
        clone.setSorts(request.getSorts());
        clone.setPaging(null);
        clone.setGroupTopN(request.getGroupTopN());
        clone.setDateGranularity(request.getDateGranularity());
        // 对比期请求不再带 comparison，避免递归
        clone.setIndicatorComparison(null);
        return clone;
    }

    /**
     * 对比列展开：按 comparisonMap 给原始列追加 _prev/_ratio/_yoy/_yoy_ratio 列。
     */
    private List<ColumnMeta> expandComparisonColumns(List<ColumnMeta> originColumns,
                                                     Map<Long, ComparisonType> comparisonMap,
                                                     QueryDataRequest request) {
        List<ColumnMeta> result = new ArrayList<>();
        // indicatorId → fieldKey 映射从 originColumns 反推
        Map<Long, String> idToKey = new LinkedHashMap<>();
        if (request.getIndicatorIds() != null) {
            for (ColumnMeta c : originColumns) {
                if ("indicator".equals(c.getType())) {
                    for (Long id : request.getIndicatorIds()) {
                        if (idToKey.containsKey(id)) {
                            continue;
                        }
                        idToKey.put(id, c.getKey());
                        break;
                    }
                }
            }
        }

        for (ColumnMeta c : originColumns) {
            result.add(c);
            if (!"indicator".equals(c.getType())) {
                continue;
            }
            // 找到该列对应的 indicatorId
            Long matchedId = null;
            for (Map.Entry<Long, String> e : idToKey.entrySet()) {
                if (e.getValue() != null && e.getValue().equals(c.getKey())) {
                    matchedId = e.getKey();
                    break;
                }
            }
            if (matchedId == null) {
                continue;
            }
            ComparisonType ct = comparisonMap.getOrDefault(matchedId, ComparisonType.NONE);
            if (ct == ComparisonType.NONE) {
                continue;
            }
            String baseName = c.getName() != null ? c.getName() : c.getKey();
            if (ct.includesPop()) {
                result.add(buildCompareColumn(c.getKey() + "_prev", baseName + "-上期", c));
                result.add(buildRatioColumn(c.getKey() + "_ratio", baseName + "-环比"));
            }
            if (ct.includesYoy()) {
                result.add(buildCompareColumn(c.getKey() + "_yoy", baseName + "-同期", c));
                result.add(buildRatioColumn(c.getKey() + "_yoy_ratio", baseName + "-同比"));
            }
        }
        return result;
    }

    private ColumnMeta buildCompareColumn(String key, String name, ColumnMeta origin) {
        ColumnMeta meta = new ColumnMeta();
        meta.setKey(key);
        meta.setName(name);
        meta.setType("indicator");
        meta.setUnit(origin.getUnit());
        meta.setPrecision(origin.getPrecision());
        return meta;
    }

    private ColumnMeta buildRatioColumn(String key, String name) {
        ColumnMeta meta = new ColumnMeta();
        meta.setKey(key);
        meta.setName(name);
        meta.setType("indicator");
        meta.setUnit("%");
        meta.setPrecision(1);
        return meta;
    }

    /**
     * 按粒度取时间列 fieldKey：
     * day 或无粒度 → ptdate 的 fieldKey；
     * 非日粒度 → 时间派生维度的 fieldKey（如 ptweek），merge 按周期值偏移用。
     */
    private String resolveDateFieldKey(List<SqlBuildContext> contexts, String granularity) {
        if (CollectionUtils.isEmpty(contexts)) {
            return null;
        }
        SqlBuildContext first = contexts.get(0);
        String g = granularity == null ? "day" : granularity.trim().toLowerCase();
        if (!"day".equals(g) && !CollectionUtils.isEmpty(first.getDimensionFields())) {
            for (FieldMappingInfo f : first.getDimensionFields()) {
                if (g.equals(f.getTimeGranularity())) {
                    String fieldKey = first.getFieldKeyMap().get(f.getBasicId());
                    if (!isBlank(fieldKey)) {
                        return fieldKey;
                    }
                }
            }
        }
        FieldMappingInfo ptdate = first.getPtdateField();
        if (ptdate == null) {
            return null;
        }
        String fieldKey = first.getFieldKeyMap().get(ptdate.getBasicId());
        return isBlank(fieldKey) ? ptdate.getBasicKey() : fieldKey;
    }

    /**
     * 非日期维度列 key 列表（无日期维度时为全部维度）。
     */
    private List<String> resolveDimensionKeys(List<SqlBuildContext> contexts, Set<Long> internalDimIds) {
        if (CollectionUtils.isEmpty(contexts)) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        SqlBuildContext first = contexts.get(0);
        Long ptdateBasicId = first.getPtdateField() != null ? first.getPtdateField().getBasicId() : null;
        if (!CollectionUtils.isEmpty(first.getDimensionFields())) {
            for (FieldMappingInfo f : first.getDimensionFields()) {
                if (internalDimIds != null && internalDimIds.contains(f.getBasicId())) {
                    continue;
                }
                if (ptdateBasicId != null && ptdateBasicId.equals(f.getBasicId())) {
                    continue;
                }
                // 时间派生维度（ptweek 等）作为时间列处理，不进普通维度 key
                if (!isBlank(f.getTimeGranularity())) {
                    continue;
                }
                String fieldKey = first.getFieldKeyMap().get(f.getBasicId());
                if (!isBlank(fieldKey)) {
                    keys.add(fieldKey);
                }
            }
        }
        return keys;
    }

    /**
     * 需要对比的指标列 key 列表：comparisonMap 里标记了对比的指标，取其 fieldKey。
     */
    private List<String> resolveComparisonIndicatorKeys(GetDataSqlResponse resp,
                                                        Map<Long, ComparisonType> comparisonMap) {
        List<String> keys = new ArrayList<>();
        if (comparisonMap == null || comparisonMap.isEmpty()) {
            return keys;
        }
        Map<Long, String> fieldKeyMap = resp.getFieldKeyMap();
        if (fieldKeyMap == null) {
            return keys;
        }
        for (Map.Entry<Long, ComparisonType> e : comparisonMap.entrySet()) {
            if (e.getValue() == ComparisonType.NONE) {
                continue;
            }
            String fieldKey = fieldKeyMap.get(e.getKey());
            if (!isBlank(fieldKey)) {
                keys.add(fieldKey);
            }
        }
        return keys;
    }

    /**
     * 把每个 DataSourceInfo 包成 SqlBuildContext。
     *
     * 顺序：
     *  1. 先生成 fieldKeyMap（basicId -> field_key），保证后续 where 拼装和 builder 取到的 alias 一致
     *  2. 再拼装 where 片段（基于 mainsrc.field_key）
     */
    private List<SqlBuildContext> buildContexts(
            QueryDataRequest request,
            List<DataSourceInfo> selectedDataSources,
            List<IndicatorMeta> requestIndicators
    ) {
        List<SqlBuildContext> contexts = new ArrayList<SqlBuildContext>();
        List<Long> dimensionIds = request.getDimensionIds();

        for (DataSourceInfo ds : selectedDataSources) {
            SqlBuildContext ctx = new SqlBuildContext();
            ctx.setSource(ds);
            ctx.setDimensionIds(dimensionIds);
            ctx.setDimensionFields(ds.getDimensionFields() == null
                    ? Collections.<FieldMappingInfo>emptyList()
                    : new ArrayList<FieldMappingInfo>(ds.getDimensionFields()));
            ctx.setRequestIndicators(requestIndicators);
            ctx.setOwnIndicatorIds(buildOwnIndicatorIds(ds));
            ctx.setPtdateField(ds.getPtdateField());

            // 快照自动补入的 ptdate 在请求维度里，但数据源选择阶段未填充其 fieldMapping → 补上
            if (ds.getPtdateField() != null
                    && dimensionIds != null
                    && dimensionIds.contains(ds.getPtdateField().getBasicId())) {
                boolean present = false;
                for (FieldMappingInfo f : ctx.getDimensionFields()) {
                    if (f.getBasicId() != null && f.getBasicId().equals(ds.getPtdateField().getBasicId())) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    ctx.getDimensionFields().add(ds.getPtdateField());
                }
            }

            prepareFieldKeyMap(ctx, requestIndicators);

            Map<String, String> dimRefMap = buildDimensionFieldRefMap(ctx);
            Map<String, String> indRefMap = buildIndicatorFieldRefMap(ctx);
            ctx.setWhereCondition(buildWhereForContext(request, ctx, dimRefMap));
            ctx.setHavingCondition(buildHavingForContext(request, ctx, indRefMap));
            contexts.add(ctx);
        }
        return contexts;
    }

    /**
     * 生成 basicId -> field_key 映射。
     *
     * 维度字段：来自 ctx.dimensionFields[i].basicKey；
     * 指标字段：来自当前数据源 ds.indicatorFields 的 fieldKey；当前数据源未拥有的指标在
     * fieldKeyMap 里仍然要有占位，使用 IndicatorMeta.alias 作 fallback。
     *
     * field_key 缺失时回退到 "f_<basicId>"，保证内层 alias 一定合法。
     */
    private void prepareFieldKeyMap(SqlBuildContext ctx, List<IndicatorMeta> requestIndicators) {
        Map<Long, String> map = ctx.getFieldKeyMap();

        if (!CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            for (FieldMappingInfo f : ctx.getDimensionFields()) {
                if (f.getBasicId() == null) {
                    continue;
                }
                map.put(f.getBasicId(), pickFieldKey(f.getBasicKey(), f.getBasicId()));
            }
        }

        if (ctx.getSource() != null && !CollectionUtils.isEmpty(ctx.getSource().getIndicatorFields())) {
            for (FieldMappingInfo f : ctx.getSource().getIndicatorFields()) {
                if (f.getBasicId() == null) {
                    continue;
                }
                map.put(f.getBasicId(), pickFieldKey(f.getBasicKey(), f.getBasicId()));
            }
        }

        // 当前数据源未拥有的指标：仍然需要 field_key 作为外层输出 alias，
        // 否则 0 占位列没法跟其他 segment 列对齐。回退到 IndicatorMeta.alias，再回退到 f_<id>。
        if (!CollectionUtils.isEmpty(requestIndicators)) {
            for (IndicatorMeta meta : requestIndicators) {
                if (meta.getId() == null || map.containsKey(meta.getId())) {
                    continue;
                }
                map.put(meta.getId(), pickFieldKey(meta.getAlias(), meta.getId()));
            }
        }
    }

    private String pickFieldKey(String preferred, Long basicId) {
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred.trim();
        }
        log.warn("[SQL生成] field_key 缺失, 回退使用 f_{}", basicId);
        return "f_" + basicId;
    }

    /**
     * 当前数据源拥有的指标 ID 集合。
     */
    private Set<Long> buildOwnIndicatorIds(DataSourceInfo ds) {
        Set<Long> ids = new LinkedHashSet<Long>();
        if (!CollectionUtils.isEmpty(ds.getIndicatorFields())) {
            for (FieldMappingInfo f : ds.getIndicatorFields()) {
                if (f.getBasicId() != null) {
                    ids.add(f.getBasicId());
                }
            }
        }
        return ids;
    }

    /**
     * 构建维度字段引用 Map：可匹配的名称（fieldKey / srcField） -> f.`field_key`。
     */
    private Map<String, String> buildDimensionFieldRefMap(SqlBuildContext ctx) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            return map;
        }
        for (FieldMappingInfo f : ctx.getDimensionFields()) {
            if (f.getBasicId() == null) {
                continue;
            }
            String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
            if (isBlank(fieldKey)) {
                continue;
            }
            String columnRef = SegmentSqlBuilder.OUTER_ALIAS + "." + quote(fieldKey, ctx.getSource().getDbDialect());
            map.put(normalizeFilterField(fieldKey), columnRef);
            if (!isBlank(f.getBasicKey())) {
                map.putIfAbsent(normalizeFilterField(f.getBasicKey()), columnRef);
            }
            if (!isBlank(f.getSrcField())) {
                map.putIfAbsent(normalizeFilterField(f.getSrcField()), columnRef);
            }
        }
        return map;
    }

    /**
     * 构建指标字段引用 Map：可匹配的名称 -> agg(f.`field_key`)。
     * HAVING 条件中指标需要带聚合函数。
     */
    private Map<String, String> buildIndicatorFieldRefMap(SqlBuildContext ctx) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (ctx.getSource() == null || CollectionUtils.isEmpty(ctx.getSource().getIndicatorFields())) {
            return map;
        }
        for (FieldMappingInfo f : ctx.getSource().getIndicatorFields()) {
            if (f.getBasicId() == null) {
                continue;
            }
            String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
            if (isBlank(fieldKey)) {
                continue;
            }
            String agg = isBlank(f.getAggFunc()) ? "sum" : f.getAggFunc().trim().toLowerCase(Locale.ROOT);
            String inner = SegmentSqlBuilder.OUTER_ALIAS + "." + quote(fieldKey, ctx.getSource().getDbDialect());
            String aggExpr;
            if ("countdistinct".equals(agg) || "count_distinct".equals(agg)) {
                aggExpr = "count(distinct " + inner + ")";
            } else {
                aggExpr = agg + "(" + inner + ")";
            }
            map.put(normalizeFilterField(fieldKey), aggExpr);
            if (!isBlank(f.getBasicKey())) {
                map.putIfAbsent(normalizeFilterField(f.getBasicKey()), aggExpr);
            }
            if (!isBlank(f.getSrcField())) {
                map.putIfAbsent(normalizeFilterField(f.getSrcField()), aggExpr);
            }
        }
        return map;
    }

    /**
     * 构建 WHERE 片段（维度过滤 + timeRange）。
     *
     * filter 自动分类：先在 dimRefMap 查，命中则归 WHERE；否则跳过（归 HAVING 由另一方法处理）。
     * 时间派生维度（ptweek/ptmonth/ptquarter/ptyear）不拼 filter——它们由 timeRange 过滤。
     */
    private String buildWhereForContext(QueryDataRequest request, SqlBuildContext ctx,
                                        Map<String, String> dimRefMap) {
        List<String> conditions = new ArrayList<String>();

        Map<String, Boolean> dimNumericMap = buildDimensionNumericMap(ctx);
        Set<String> derivedDimKeys = buildDerivedDimFieldKeys(ctx);

        if (request != null && !CollectionUtils.isEmpty(request.getFilters())) {
            Map<String, String> indRefMap = buildIndicatorFieldRefMap(ctx);
            for (QueryDataRequest.Filter filter : request.getFilters()) {
                if (filter == null || isBlank(filter.getField())) {
                    continue;
                }
                String normalized = normalizeFilterField(filter.getField());
                if (!CollectionUtils.isEmpty(derivedDimKeys) && derivedDimKeys.contains(normalized)) {
                    log.info("[SQL生成] 时间派生维度过滤由 timeRange 承担, 跳过 filter: {}", filter.getField());
                    continue;
                }
                String columnRef = dimRefMap.get(normalized);
                if (isBlank(columnRef)) {
                    if (indRefMap.containsKey(normalized)) {
                        continue;
                    }
                    log.warn("[SQL生成] filter 字段未匹配到维度或指标, field={}", filter.getField());
                    continue;
                }
                boolean isNumeric = dimNumericMap.getOrDefault(normalized, false);
                String cond = buildSingleCondition(columnRef, filter, isNumeric);
                if (cond != null) {
                    conditions.add(cond);
                }
            }
        }

        if (request != null && request.getTimeRange() != null) {
            String timeCond = buildTimeRangeCondition(request.getTimeRange(), ctx);
            if (!isBlank(timeCond)) {
                conditions.add(timeCond);
            }
        }

        return String.join(" ", conditions);
    }

    /**
     * 时间派生维度（timeGranularity 非空的维度）的 fieldKey/basicKey 集合（normalized 小写），
     * 用于跳过它们的 filter 条件——派生维度过滤由 timeRange 承担。
     */
    private Set<String> buildDerivedDimFieldKeys(SqlBuildContext ctx) {
        Set<String> keys = new LinkedHashSet<>();
        if (CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            return keys;
        }
        for (FieldMappingInfo f : ctx.getDimensionFields()) {
            if (isBlank(f.getTimeGranularity())) {
                continue;
            }
            String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
            if (!isBlank(fieldKey)) {
                keys.add(normalizeFilterField(fieldKey));
            }
            if (!isBlank(f.getBasicKey())) {
                keys.add(normalizeFilterField(f.getBasicKey()));
            }
        }
        return keys;
    }

    private Map<String, Boolean> buildDimensionNumericMap(SqlBuildContext ctx) {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
        if (!CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            for (FieldMappingInfo f : ctx.getDimensionFields()) {
                if (f.getBasicId() == null) continue;
                String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
                if (!isBlank(fieldKey)) {
                    boolean numeric = isNumericField(f.getSrcFieldType());
                    map.put(normalizeFilterField(fieldKey), numeric);
                    if (!isBlank(f.getBasicKey())) {
                        map.putIfAbsent(normalizeFilterField(f.getBasicKey()), numeric);
                    }
                    if (!isBlank(f.getSrcField())) {
                        map.putIfAbsent(normalizeFilterField(f.getSrcField()), numeric);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 构建 HAVING 片段（指标过滤）。
     *
     * filter 自动分类：先在 dimRefMap 查不到，再在 indRefMap 查命中则归 HAVING。
     */
    private String buildHavingForContext(QueryDataRequest request, SqlBuildContext ctx,
                                         Map<String, String> indRefMap) {
        if (request == null || CollectionUtils.isEmpty(request.getFilters()) || indRefMap.isEmpty()) {
            return "";
        }
        Map<String, String> dimRefMap = buildDimensionFieldRefMap(ctx);
        Map<String, Boolean> indNumericMap = buildIndicatorNumericMap(ctx);
        List<String> conditions = new ArrayList<String>();
        for (QueryDataRequest.Filter filter : request.getFilters()) {
            if (filter == null || isBlank(filter.getField())) {
                continue;
            }
            String normalized = normalizeFilterField(filter.getField());
            if (dimRefMap.containsKey(normalized)) {
                continue;
            }
            String aggExpr = indRefMap.get(normalized);
            if (isBlank(aggExpr)) {
                continue;
            }
            boolean isNumeric = indNumericMap.getOrDefault(normalized, false);
            String cond = buildSingleCondition(aggExpr, filter, isNumeric);
            if (cond != null) {
                conditions.add(cond);
            }
        }
        return String.join(" ", conditions);
    }

    private Map<String, Boolean> buildIndicatorNumericMap(SqlBuildContext ctx) {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
        if (ctx.getSource() != null && !CollectionUtils.isEmpty(ctx.getSource().getIndicatorFields())) {
            for (FieldMappingInfo f : ctx.getSource().getIndicatorFields()) {
                if (f.getBasicId() == null) continue;
                String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
                if (!isBlank(fieldKey)) {
                    boolean numeric = isNumericField(f.getSrcFieldType());
                    map.put(normalizeFilterField(fieldKey), numeric);
                    if (!isBlank(f.getBasicKey())) {
                        map.putIfAbsent(normalizeFilterField(f.getBasicKey()), numeric);
                    }
                    if (!isBlank(f.getSrcField())) {
                        map.putIfAbsent(normalizeFilterField(f.getSrcField()), numeric);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 拼装单个 filter 条件片段，返回 "and columnRef op value" 格式。
     *
     * @param isNumeric 字段是否为数值类型，数值类型不加引号，避免数据库隐式类型转换。
     */
    private String buildSingleCondition(String columnRef, QueryDataRequest.Filter filter, boolean isNumeric) {
        String operator = normalizeOperator(filter.getOperator());
        List<String> values = filter.getValues() == null ? Collections.<String>emptyList() : filter.getValues();
        if (values.isEmpty()) {
            return null;
        }
        String left = "and " + columnRef;
        if ("in".equals(operator) || "not in".equals(operator)) {
            return left + " " + operator + " (" + (isNumeric ? joinValues(values) : joinQuotedValues(values)) + ")";
        }
        if ("like".equals(operator) || "not like".equals(operator)) {
            return left + " " + operator + " " + quoteValue(values.get(0));
        }
        return left + " " + operator + " " + (isNumeric ? values.get(0) : quoteValue(values.get(0)));
    }

    /**
     * 判断字段类型是否为数值型（int/bigint/decimal/float/double 等）。
     * 数值类型过滤值不加引号，避免 like sum(f.amount) > '1000' 的问题。
     */
    private boolean isNumericField(String srcFieldType) {
        if (srcFieldType == null) return false;
        String lower = srcFieldType.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("int") || lower.startsWith("bigint") || lower.startsWith("smallint")
                || lower.startsWith("tinyint") || lower.startsWith("mediumint")
                || lower.startsWith("decimal") || lower.startsWith("numeric") || lower.startsWith("number")
                || lower.startsWith("float") || lower.startsWith("double") || lower.startsWith("real")
                || lower.equals("bit");
    }

    /**
     * 生成 timeRange WHERE 条件，固定使用 ptdate 字段。
     */
    private String buildTimeRangeCondition(QueryDataRequest.TimeRange timeRange, SqlBuildContext ctx) {
        if (isBlank(timeRange.getStart()) && isBlank(timeRange.getEnd())) {
            return "";
        }
        FieldMappingInfo ptdateField = ctx.getPtdateField();
        if (ptdateField == null) {
            log.warn("[SQL生成] 未找到 ptdate 字段映射");
            return "";
        }
        String fieldKey = ctx.getFieldKeyMap().get(ptdateField.getBasicId());
        if (isBlank(fieldKey)) {
            fieldKey = ptdateField.getBasicKey();
            if (isBlank(fieldKey)) {
                return "";
            }
            // 确保 fieldKeyMap 里有 ptdate 的 key，否则外部引用会找不到
            ctx.getFieldKeyMap().put(ptdateField.getBasicId(), fieldKey);
        }
        String columnRef = SegmentSqlBuilder.OUTER_ALIAS + "." + quote(fieldKey, ctx.getSource().getDbDialect());
        return TimeRangeConverter.buildCondition(columnRef, timeRange.getStart(), timeRange.getEnd());
    }

    private void resolveViewTimeVariables(List<DataSourceInfo> sources, QueryDataRequest.TimeRange timeRange) {
        if (timeRange == null) {
            return;
        }
        boolean hasStart = !isBlank(timeRange.getStart());
        boolean hasEnd   = !isBlank(timeRange.getEnd());
        if (!hasStart && !hasEnd) {
            return;
        }
        for (DataSourceInfo ds : sources) {
            if (ds.getTbTypeKey() == null || ds.getTbTypeKey() != 1 || isBlank(ds.getViewSql())) {
                continue;
            }
            String sql = ds.getViewSql();
            if (hasStart) {
                sql = START_TIME_PATTERN.matcher(sql).replaceAll(timeRange.getStart());
            }
            if (hasEnd) {
                sql = END_TIME_PATTERN.matcher(sql).replaceAll(timeRange.getEnd());
            }
            ds.setViewSql(sql);
        }
    }

    /**
     * 多源场景外层 group by 的列名（与 segment 输出 alias 一致，统一用 field_key）。
     *
     * 单源场景该结果不会被用到，多源 UNION ALL 后才进入 OuterSqlWrapper。
     */
    private List<String> buildOuterGroupDimensions(List<SqlBuildContext> contexts) {
        if (CollectionUtils.isEmpty(contexts)) {
            return Collections.emptyList();
        }
        List<String> dims = new ArrayList<String>();
        SqlBuildContext first = contexts.get(0);
        Set<Long> internalDims = first.getInternalDimIds();
        if (!CollectionUtils.isEmpty(first.getDimensionFields())) {
            for (FieldMappingInfo f : first.getDimensionFields()) {
                if (internalDims != null && internalDims.contains(f.getBasicId())) {
                    continue;
                }
                String fieldKey = first.getFieldKeyMap().get(f.getBasicId());
                if (!isBlank(fieldKey)) {
                    dims.add(fieldKey);
                }
            }
        }
        return dims;
    }

    /**
     * 把指标 ID 列表转成 IndicatorMeta（外层 alias 用 field_key）。
     */
    private List<IndicatorMeta> buildRequestIndicators(QueryDataRequest request,
                                                        List<DataSourceInfo> selectedDataSources,
                                                        Map<Long, CalculatedIndicatorMeta> calcMetaMap,
                                                        Map<Long, DerivativeIndicatorMeta> derivMetaMap,
                                                        List<Long> originalIndicatorIds) {
        Map<Long, IndicatorMeta> byId = new LinkedHashMap<Long, IndicatorMeta>();
        for (DataSourceInfo ds : selectedDataSources) {
            if (CollectionUtils.isEmpty(ds.getIndicatorFields())) {
                continue;
            }
            for (FieldMappingInfo f : ds.getIndicatorFields()) {
                if (f.getBasicId() == null || byId.containsKey(f.getBasicId())) {
                    continue;
                }
                IndicatorMeta meta = new IndicatorMeta();
                meta.setId(f.getBasicId());
                meta.setFieldName(f.getSrcField());
                meta.setAggFunc(f.getAggFunc() == null ? "sum" : f.getAggFunc());
                meta.setAlias(isBlank(f.getBasicKey()) ? f.getAlias() : f.getBasicKey());
                byId.put(f.getBasicId(), meta);
            }
        }
        // 单源时，用户未请求的子指标不输出 SQL 列（公式已内联聚合表达式）
        // 多源时子指标列是 UNION ALL 中间列，外层公式依赖，必须输出
        boolean singleSource = selectedDataSources != null && selectedDataSources.size() == 1;
        List<IndicatorMeta> result = new ArrayList<IndicatorMeta>();
        if (request.getIndicatorIds() != null) {
            for (Long id : request.getIndicatorIds()) {
                IndicatorMeta meta = byId.get(id);
                if (meta == null) {
                    throw new BizException("指标元数据缺失, indicatorId=" + id);
                }
                if (singleSource && originalIndicatorIds != null && !originalIndicatorIds.contains(id)) {
                    meta.setInternal(true);
                }
                result.add(meta);
            }
        }
        // 追加计算指标
        if (calcMetaMap != null && !calcMetaMap.isEmpty()) {
            for (CalculatedIndicatorMeta calcMeta : calcMetaMap.values()) {
                IndicatorMeta meta = new IndicatorMeta();
                meta.setId(calcMeta.getIndicatorId());
                meta.setCalculated(true);
                meta.setFormulaMeta(calcMeta);
                meta.setAlias(calcMeta.getEnglishName());
                meta.setAggFunc("sum");
                result.add(meta);
            }
        }
        // 追加衍生指标
        if (derivMetaMap != null && !derivMetaMap.isEmpty()) {
            for (DerivativeIndicatorMeta derivMeta : derivMetaMap.values()) {
                IndicatorMeta meta = new IndicatorMeta();
                meta.setId(derivMeta.getIndicatorId());
                meta.setDerivative(true);
                meta.setDerivativeMeta(derivMeta);
                meta.setAlias("deriv_" + derivMeta.getIndicatorId() + "_key");
                meta.setAggFunc("sum");
                result.add(meta);
            }
        }
        return result;
    }

    /**
     * 根据 context 里的 metaDataSourceId 从 DataSourceManager 获取数据库连接。
     */
    private DataSource resolveTargetDataSource(List<SqlBuildContext> contexts) {
        if (!CollectionUtils.isEmpty(contexts)) {
            DataSourceInfo src = contexts.get(0).getSource();
            if (src.getMetaDataSourceId() != null) {
                DataSource ds = dataSourceManager.get(src.getMetaDataSourceId());
                if (ds != null) {
                    log.info("[SQL路由] 命中 meta_data_source, sourceId={}", src.getMetaDataSourceId());
                    return ds;
                }
                throw new BizException("未找到数据源连接, metaDataSourceId=" + src.getMetaDataSourceId());
            }
        }
        throw new BizException("数据源信息缺失");
    }

    private long executeCount(String finalSqlWithoutPaging, DataSource ds) {
        String countSql = "select count(1) as total_count from (" + finalSqlWithoutPaging + ") totalTable";
        log.info("[SQL执行] 准备执行Count SQL: {}", countSql);
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                return rs.getLong("total_count");
            }
            return 0L;
        } catch (Exception e) {
            log.error("Count SQL执行失败: {}", countSql, e);
            throw new BizException(translateSqlError(e));
        }
    }

    private List<Map<String, Object>> executeQuery(String sql, DataSource ds) {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        log.info("[SQL执行] 准备执行SQL: {}", sql);
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }
        } catch (Exception e) {
            log.error("SQL执行失败: {}", sql, e);
            throw new BizException(translateSqlError(e));
        }
        return results;
    }

    /**
     * 根据 MySQL 错误码翻译为可读提示。
     */
    private String translateSqlError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException) {
                int errorCode = ((java.sql.SQLException) cause).getErrorCode();
                String msg = cause.getMessage();
                switch (errorCode) {
                    case 1054: return "字段 " + extractColumn(msg) + " 不存在，请检查字段映射配置";
                    case 1146: return "数据表 " + extractTable(msg) + " 未找到，请检查数据源配置";
                    case 1064: return "SQL 语法错误，请检查字段映射或模型配置";
                    default:   return "数据查询失败: " + msg;
                }
            }
            cause = cause.getCause();
        }
        return "数据查询失败: " + e.getMessage();
    }

    private String extractColumn(String msg) {
        if (msg == null) return "";
        int start = msg.indexOf('\'');
        int end = msg.indexOf('\'', start + 1);
        return start >= 0 && end > start ? msg.substring(start, end + 1) : "";
    }

    private String extractTable(String msg) {
        return extractColumn(msg);
    }

    /**
     * 构建列元数据：先维度后指标，附带 unit/precision。
     */
    private List<ColumnMeta> buildColumnMetas(QueryDataRequest request, Map<Long, String> fieldKeyMap,
                                              Map<Long, String> mappingUnitMap,
                                              Map<Long, CalculatedIndicatorMeta> calcMetaMap,
                                              Map<Long, DerivativeIndicatorMeta> derivMetaMap,
                                              Set<Long> internalDimIds) {
        List<ColumnMeta> columns = new ArrayList<ColumnMeta>();
        List<Long> dimensionIds = request.getDimensionIds() != null ? request.getDimensionIds()
                : Collections.<Long>emptyList();
        List<Long> indicatorIds = request.getIndicatorIds() != null ? request.getIndicatorIds()
                : Collections.<Long>emptyList();

        // 批量加载 basic 元数据
        Set<Long> allIds = new LinkedHashSet<Long>();
        allIds.addAll(dimensionIds);
        allIds.addAll(indicatorIds);
        Map<Long, OlapBasicProDO> basicMap = new LinkedHashMap<Long, OlapBasicProDO>();
        if (!allIds.isEmpty()) {
            List<OlapBasicProDO> basics = basicProMapper.selectBatchIds(allIds);
            for (OlapBasicProDO b : basics) {
                basicMap.put(b.getId(), b);
            }
        }
        // 批量加载指标扩展信息
        Map<Long, OlapBasicProIndicatorDO> indicatorMap = new LinkedHashMap<Long, OlapBasicProIndicatorDO>();
        if (!indicatorIds.isEmpty()) {
            List<OlapBasicProIndicatorDO> indicators = indicatorMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OlapBasicProIndicatorDO>()
                            .in(OlapBasicProIndicatorDO::getOlapBasicProId, indicatorIds));
            for (OlapBasicProIndicatorDO ind : indicators) {
                indicatorMap.put(ind.getOlapBasicProId(), ind);
            }
        }

        // 维度列（跳过内部维度）
        for (Long dimId : dimensionIds) {
            if (internalDimIds != null && internalDimIds.contains(dimId)) {
                continue;
            }
            ColumnMeta meta = new ColumnMeta();
            String key = fieldKeyMap.get(dimId);
            meta.setKey(key);
            OlapBasicProDO basic = basicMap.get(dimId);
            meta.setName(basic != null ? basic.getChineseName() : key);
            meta.setType("dimension");
            columns.add(meta);
        }
        // 指标列
        for (Long indId : indicatorIds) {
            ColumnMeta meta = new ColumnMeta();
            String key = fieldKeyMap.get(indId);
            meta.setKey(key);
            OlapBasicProDO basic = basicMap.get(indId);
            meta.setName(basic != null ? basic.getChineseName() : key);
            meta.setType("indicator");
            OlapBasicProIndicatorDO ind = indicatorMap.get(indId);
            // mapping 优先，indicator 兜底
            String unit = mappingUnitMap != null ? mappingUnitMap.get(indId) : null;
            if (unit == null && ind != null) {
                unit = ind.getUnit();
            }
            meta.setUnit(unit);
            if (ind != null) {
                meta.setPrecision(ind.getDecimalPlaces() != null ? ind.getDecimalPlaces().intValue() : null);
            }
            columns.add(meta);
        }
        return columns;
    }

    /**
     * 对指标值按 precision 做小数位处理。
     */
    private void formatIndicatorValues(List<Map<String, Object>> records, List<ColumnMeta> columns) {
        if (records.isEmpty() || columns.isEmpty()) return;
        for (Map<String, Object> row : records) {
            for (ColumnMeta col : columns) {
                if (!"indicator".equals(col.getType()) || col.getPrecision() == null) continue;
                Object val = row.get(col.getKey());
                if (val instanceof Number) {
                    BigDecimal bd = new BigDecimal(val.toString());
                    row.put(col.getKey(), bd.setScale(col.getPrecision(), RoundingMode.HALF_UP));
                }
            }
        }
    }

    private void validateRequest(QueryDataRequest request) {
        if (request == null
                || (CollectionUtils.isEmpty(request.getDimensionIds())
                    && CollectionUtils.isEmpty(request.getIndicatorIds()))) {
            throw new BizException("校验请求失败：dimensionIds 和 indicatorIds 不能同时为空");
        }
    }

    private Integer getPage(QueryDataRequest request) {
        if (request.getLimit() != null) return 1;
        return request.getPaging() == null ? null : request.getPaging().getPage();
    }

    private Integer getPageSize(QueryDataRequest request) {
        if (request.getLimit() != null) return request.getLimit();
        return request.getPaging() == null ? null : request.getPaging().getPageSize();
    }

    private String normalizeOperator(String operator) {
        if (operator == null) {
            return "=";
        }
        String op = operator.trim().toLowerCase(Locale.ROOT);
        if ("==".equals(op)) {
            return "=";
        }
        if ("notin".equals(op)) {
            return "not in";
        }
        if ("notlike".equals(op)) {
            return "not like";
        }
        if ("in".equals(op) || "not in".equals(op)
                || "like".equals(op) || "not like".equals(op)
                || "=".equals(op) || "!=".equals(op)
                || ">".equals(op) || "<".equals(op)
                || ">=".equals(op) || "<=".equals(op)) {
            return op;
        }
        return "=";
    }

    private String joinQuotedValues(List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            result.add(quoteValue(value));
        }
        return String.join(", ", result);
    }

    private String joinValues(List<String> values) {
        return String.join(", ", values);
    }

    private String quoteValue(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private String quote(String value, String dbDialect) {
        return SqlDialect.from(dbDialect).quote(value);
    }

    private String normalizeFilterField(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 预处理：识别请求中的计算指标，解析公式 JSON，返回 calcMetaMap。
     */
    private Map<Long, CalculatedIndicatorMeta> preprocessCalculatedIndicators(QueryDataRequest request) {
        Map<Long, CalculatedIndicatorMeta> result = new LinkedHashMap<>();
        List<Long> ids = request.getIndicatorIds();
        if (CollectionUtils.isEmpty(ids)) {
            return result;
        }
        List<OlapBasicProIndicatorDO> indicators = indicatorMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OlapBasicProIndicatorDO>()
                        .in(OlapBasicProIndicatorDO::getOlapBasicProId, ids));
        for (OlapBasicProIndicatorDO ind : indicators) {
            if (isBlank(ind.getCalculatedProduction())) {
                continue;
            }
            CalculatedIndicatorMeta meta = parseCalculatedProduction(ind.getCalculatedProduction());
            meta.setIndicatorId(ind.getOlapBasicProId());
            result.put(meta.getIndicatorId(), meta);
        }
        // 批量查 englishName
        if (!result.isEmpty()) {
            List<OlapBasicProDO> basics = basicProMapper.selectBatchIds(new ArrayList<>(result.keySet()));
            for (OlapBasicProDO basic : basics) {
                CalculatedIndicatorMeta meta = result.get(basic.getId());
                if (meta != null) {
                    meta.setEnglishName(basic.getEnglishName());
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private CalculatedIndicatorMeta parseCalculatedProduction(String json) {
        com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
        CalculatedIndicatorMeta meta = new CalculatedIndicatorMeta();
        meta.setFormula(obj.getString("formula"));
        meta.setDisplayFormula(obj.getString("displayFormula"));
        List<SubIndicatorMeta> subIndicators = new ArrayList<>();
        List<com.alibaba.fastjson2.JSONObject> indicatorList =
                (List<com.alibaba.fastjson2.JSONObject>) obj.get("indicatorList");
        if (indicatorList != null) {
            for (com.alibaba.fastjson2.JSONObject item : indicatorList) {
                SubIndicatorMeta sub = new SubIndicatorMeta();
                sub.setId(item.getLong("id"));
                sub.setLetter(item.getString("letter"));
                sub.setName(item.getString("name"));
                subIndicators.add(sub);
            }
        }
        meta.setSubIndicators(subIndicators);
        return meta;
    }

    /**
     * 预处理：识别请求中的衍生指标，解析 derivative_production JSON。
     */
    private Map<Long, DerivativeIndicatorMeta> preprocessDerivativeIndicators(QueryDataRequest request) {
        Map<Long, DerivativeIndicatorMeta> result = new LinkedHashMap<>();
        List<Long> ids = request.getIndicatorIds();
        if (CollectionUtils.isEmpty(ids)) {
            return result;
        }
        List<OlapBasicProIndicatorDO> indicators = indicatorMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OlapBasicProIndicatorDO>()
                        .in(OlapBasicProIndicatorDO::getOlapBasicProId, ids));
        for (OlapBasicProIndicatorDO ind : indicators) {
            if (isBlank(ind.getDerivativeProduction())) {
                continue;
            }
            DerivativeIndicatorMeta meta = parseDerivativeProduction(ind.getDerivativeProduction());
            meta.setIndicatorId(ind.getOlapBasicProId());
            result.put(meta.getIndicatorId(), meta);
        }
        return result;
    }

    /**
     * 解析衍生指标 JSON，兼容两种格式：
     *
     * 新格式:
     *   {"baseIndicatorId":1990, "baseIndicatorName":"...",
     *    "filters":[{"dimensionId":1641,"operator":"=","values":["版本2"]}]}
     *
     * 老格式（管理端历史数据）:
     *   {"indicatorId":1990, "indicatorName":"...",
     *    "dimensionList":[{"dimensionId":1641,"symbol":{"key":"=","value":"="},"dimensionValueList":"版本2"}]}
     */
    @SuppressWarnings("unchecked")
    private DerivativeIndicatorMeta parseDerivativeProduction(String json) {
        com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
        DerivativeIndicatorMeta meta = new DerivativeIndicatorMeta();

        // 基础指标 ID：新格式 baseIndicatorId，老格式 indicatorId
        Long baseId = obj.getLong("baseIndicatorId");
        if (baseId == null) {
            baseId = obj.getLong("indicatorId");
        }
        meta.setBaseIndicatorId(baseId);
        String baseName = obj.getString("baseIndicatorName");
        if (isBlank(baseName)) {
            baseName = obj.getString("indicatorName");
        }
        meta.setBaseIndicatorName(baseName);

        List<DerivativeFilter> filters = new ArrayList<>();
        // 新格式 filters
        List<com.alibaba.fastjson2.JSONObject> filterList =
                (List<com.alibaba.fastjson2.JSONObject>) obj.get("filters");
        if (filterList != null) {
            for (com.alibaba.fastjson2.JSONObject item : filterList) {
                filters.add(buildFilterFromNew(item));
            }
        }
        // 老格式 dimensionList
        if (filters.isEmpty()) {
            List<com.alibaba.fastjson2.JSONObject> dimList =
                    (List<com.alibaba.fastjson2.JSONObject>) obj.get("dimensionList");
            if (dimList != null) {
                for (com.alibaba.fastjson2.JSONObject item : dimList) {
                    filters.add(buildFilterFromLegacy(item));
                }
            }
        }
        meta.setFilters(filters);
        return meta;
    }

    /** 新格式 filter 项解析。 */
    private DerivativeFilter buildFilterFromNew(com.alibaba.fastjson2.JSONObject item) {
        DerivativeFilter df = new DerivativeFilter();
        df.setDimensionId(item.getLong("dimensionId"));
        df.setOperator(item.getString("operator"));
        com.alibaba.fastjson2.JSONArray arr = item.getJSONArray("values");
        if (arr != null) {
            List<String> vals = new ArrayList<>();
            for (Object v : arr) {
                vals.add(v != null ? v.toString() : "");
            }
            df.setValues(vals);
        }
        return df;
    }

    /** 老格式 dimensionList 项解析：symbol.value → operator，dimensionValueList → 单元素 values。 */
    private DerivativeFilter buildFilterFromLegacy(com.alibaba.fastjson2.JSONObject item) {
        DerivativeFilter df = new DerivativeFilter();
        df.setDimensionId(item.getLong("dimensionId"));
        String operator = null;
        com.alibaba.fastjson2.JSONObject symbol = item.getJSONObject("symbol");
        if (symbol != null) {
            operator = symbol.getString("value");
            if (isBlank(operator)) {
                operator = symbol.getString("key");
            }
        }
        df.setOperator(operator);
        String value = item.getString("dimensionValueList");
        if (!isBlank(value)) {
            List<String> vals = new ArrayList<>();
            vals.add(value);
            df.setValues(vals);
        }
        return df;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
