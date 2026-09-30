package com.wm.semantic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wm.semantic.common.enums.TimeGranularity;
import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.CandidateTable;
import com.wm.semantic.dto.DataSourceInfo;
import com.wm.semantic.dto.FieldMappingInfo;
import com.wm.semantic.dto.QueryDataRequest;
import com.wm.semantic.dto.IndicatorCheckResult;
import com.wm.semantic.dto.ModelJoinInfo;
import com.wm.semantic.dto.TableIndicatorDto;
import com.wm.semantic.entity.*;
import com.wm.semantic.mapper.*;

import com.wm.semantic.service.TableSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 表选择决策服务
 *
 * 根据维度ID和指标ID自动选择最优数据表或数据模型
 * 数据源类型：1-单表，2-data模型
 */
@Service
public class TableSelectionServiceImpl implements TableSelectionService {
    private static final Logger log = LoggerFactory.getLogger(TableSelectionServiceImpl.class);

    @Resource
    private OlapBasicProMapper basicProMapper;

    @Resource
    private OlapBasicProDimensionMapper dimensionMapper;

    @Resource
    private OlapTableProMapper tableProMapper;

    @Resource
    private OlapTableFieldMappingMapper fieldMappingMapper;

    @Resource
    private OlapDataModelDimensionMapper dataModelDimensionMapper;

    @Resource
    private MetaDataSourceMapper metaDataSourceMapper;

    @Resource
    private OlapDataModelMapper dataModelMapper;

/**
     * 根据维度ID和指标ID选择最优数据源
     *
     * 核心逻辑：
     * 1. 维度阶段：筛选包含所有维度的候选数据源（预选表信息提前过滤）
     * 2. 指标阶段：贪心选择覆盖最多指标的表
     * 3. 返回每个数据源的维度和指标字段信息
     *
     * @param request 请求参数
     * @return 选中的数据源列表（包含维度和指标字段信息）
     */
    @Override
    public List<DataSourceInfo> selectDataSource(QueryDataRequest request) {
        List<Long> dimensionIds = request.getDimensionIds();
        List<Long> indicatorIds = request.getIndicatorIds();
        List<Long> preferredTableIds = request.getPreferredTableIds();
        List<Long> preferredModelIds = request.getPreferredModelIds();

        boolean hasPreferred = !CollectionUtils.isEmpty(preferredTableIds) || !CollectionUtils.isEmpty(preferredModelIds);

        log.info("[数据源选择] 收到请求, dimensionIds={}, indicatorIds={}, preferredTableIds={}, preferredModelIds={}",
                dimensionIds, indicatorIds, preferredTableIds, preferredModelIds);

        // 分离时间派生维度（ptweek/ptmonth/ptquarter/ptyear），不参与候选数据源筛选
        List<Long> realDimIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(dimensionIds)) {
            List<OlapBasicProDO> basics = basicProMapper.selectBatchIds(dimensionIds);
            for (Long id : dimensionIds) {
                String englishName = null;
                for (OlapBasicProDO b : basics) {
                    if (id.equals(b.getId())) { englishName = b.getEnglishName(); break; }
                }
                if (getTimeGranularityByName(englishName) != null) {
                    continue; // 时间派生维度，跳过
                }
                realDimIds.add(id);
            }
        }

        // Step1: 维度阶段 - 筛选包含所有维度的候选数据源（预选表信息提前过滤）
        List<CandidateTable> dimCandidates;
        if (CollectionUtils.isEmpty(realDimIds)) {
            dimCandidates = findAllCandidates(preferredTableIds, preferredModelIds);
        } else {
            dimCandidates = findDimCandidates(realDimIds, preferredTableIds, preferredModelIds);
        }
        log.info("[数据源选择] Step1-维度阶段候选, count={}", dimCandidates.size());

        if (dimCandidates.isEmpty()) {
            if (hasPreferred) {
                throw new BizException(String.format("维度阶段：预选表/模型[%s]中无法找到包含维度[%s]的数据源",
                        preferredTableIds != null ? preferredTableIds : preferredModelIds, realDimIds));
            }
            throw new BizException(String.format("维度阶段无可用数据源：维度[%s]无法关联", realDimIds));
        }

        // Step1.5: 指标阶段匹配
        List<CandidateTable> validCandidates;
        IndicatorCheckResult indicatorResult;
        if (CollectionUtils.isEmpty(indicatorIds)) {
            validCandidates = dimCandidates;
            indicatorResult = new IndicatorCheckResult();
            indicatorResult.setCovered(true);
            indicatorResult.setAllIndicators(Collections.<Long>emptySet());
            indicatorResult.setIndicatorMap(Collections.<String, Set<Long>>emptyMap());
            log.info("[数据源选择] Step1.5-无指标请求, 跳过指标匹配, 候选={}", validCandidates.size());
        } else {
            validCandidates = new ArrayList<>();
            indicatorResult = checkTotalIndicatorCoverage(dimCandidates, indicatorIds, validCandidates);
            log.info("[数据源选择] Step1.5-批量查询指标, 总指标数={}, 是否覆盖={}", indicatorResult.getAllIndicators().size(), indicatorResult.isCovered());
            if (!indicatorResult.isCovered()) {
                Set<Long> missing = new HashSet<>(indicatorIds);
                missing.removeAll(indicatorResult.getAllIndicators());
                throw new BizException(String.format("指标阶段无可用数据源：指标[%s]无法关联，缺少: %s", indicatorIds, missing));
            }
        }

        // 第三步：贪心算法 / 无指标时直接转换
        List<DataSourceInfo> finalSelected;
        if (CollectionUtils.isEmpty(indicatorIds)) {
            finalSelected = new ArrayList<>();
            for (CandidateTable ct : validCandidates) {
                DataSourceInfo ds = "table".equals(ct.getType())
                        ? DataSourceInfo.fromTable(ct.getId())
                        : DataSourceInfo.fromModel(ct.getId());
                ds.setTableName(ct.getName());
                ds.setIndicatorFields(Collections.<FieldMappingInfo>emptyList());
                finalSelected.add(ds);
            }
        } else {
            finalSelected = greedySelectMinTableModel(validCandidates, indicatorResult.getIndicatorMap(), indicatorIds);
        }

        // Step4: 元数据填充 - 把字段映射、join 关系一次性灌入 DataSourceInfo
        log.info("[数据源选择] Step4-元数据填充开始, selectedSize={}", finalSelected.size());
        enrichSelectedDataSources(finalSelected, dimensionIds);
        log.info("[数据源选择] Step4-元数据填充完成");

        return finalSelected;
    }

    /**
     * 为已选中的数据源补全：表名/库名、维度字段映射、指标字段映射(含 aggFunc/alias)、
     * 模型场景下的 fact 表信息和 join 关系（含复合关联键）。
     *
     * 核心逻辑：
     * 1. 收集所有相关 tableId（单表自身 / 模型涉及的全部 fact+dim 表）
     * 2. 批量查 olap_table_pro / olap_table_field_mapping / olap_basic_pro
     * 3. 模型场景按 (factTbId, dimTbId) 聚合 mapping 行，合并复合 join key
     * 4. 给 dimensionFields / indicatorFields 设置 srcField + tableId
     *    维度字段优先 fact 表；指标字段强制 fact 表（符合 LOGIC-ARCHITECTURE §7.4）
     *
     * @param selectedDataSources 贪心算法选出的数据源列表
     * @param dimensionIds 用户请求的维度 ID 列表
     */
    private void enrichSelectedDataSources(List<DataSourceInfo> selectedDataSources, List<Long> dimensionIds) {
        if (CollectionUtils.isEmpty(selectedDataSources)) {
            return;
        }

        // 1) 收集每个数据源涉及到的全部 tableId，并提前缓存模型的 join mapping
        Map<Long, OlapDataModelDO> modelCache = new HashMap<Long, OlapDataModelDO>();
        Map<Long, List<OlapDataModelDimensionDO>> modelDimCache = new HashMap<Long, List<OlapDataModelDimensionDO>>();
        Set<Long> allTableIds = new LinkedHashSet<Long>();
        for (DataSourceInfo ds : selectedDataSources) {
            if (ds.getType() != null && ds.getType() == 1 && ds.getTableId() != null) {
                allTableIds.add(ds.getTableId());
                continue;
            }
            if (ds.getType() != null && ds.getType() == 2 && ds.getModelId() != null) {
                OlapDataModelDO model = dataModelMapper.selectById(ds.getModelId());
                if (model == null || model.getStatus() == null || model.getStatus() != 1) {
                    throw new BizException("模型未找到或已停用, modelId=" + ds.getModelId());
                }
                modelCache.put(ds.getModelId(), model);
                if (model.getFactTableId() != null) {
                    allTableIds.add(model.getFactTableId());
                }
                List<OlapDataModelDimensionDO> dimMappings = dataModelDimensionMapper.selectList(
                        new LambdaQueryWrapper<OlapDataModelDimensionDO>()
                                .eq(OlapDataModelDimensionDO::getModelId, ds.getModelId()));
                modelDimCache.put(ds.getModelId(), dimMappings);
                if (!CollectionUtils.isEmpty(dimMappings)) {
                    for (OlapDataModelDimensionDO dmd : dimMappings) {
                        if (dmd.getDimTableId() != null) {
                            allTableIds.add(dmd.getDimTableId());
                        }
                    }
                }
            }
        }

        // 2) 批量加载物理表元信息
        Map<Long, OlapTableProDO> tableMap = new HashMap<Long, OlapTableProDO>();
        if (!allTableIds.isEmpty()) {
            List<OlapTableProDO> tables = tableProMapper.selectBatchIds(allTableIds);
            for (OlapTableProDO t : tables) {
                tableMap.put(t.getId(), t);
            }
        }

        // 3) 批量加载字段映射（涵盖维度+指标）
        Set<Long> allBasicIds = new LinkedHashSet<Long>();
        if (!CollectionUtils.isEmpty(dimensionIds)) {
            allBasicIds.addAll(dimensionIds);
        }
        for (DataSourceInfo ds : selectedDataSources) {
            if (!CollectionUtils.isEmpty(ds.getIndicatorFields())) {
                for (FieldMappingInfo f : ds.getIndicatorFields()) {
                    if (f.getBasicId() != null) {
                        allBasicIds.add(f.getBasicId());
                    }
                }
            }
        }
        // 预加载 ptdate 的 basic_id 确保字段映射被查出来
        OlapBasicProDO ptdateBasic = basicProMapper.selectOne(
                new LambdaQueryWrapper<OlapBasicProDO>()
                        .eq(OlapBasicProDO::getEnglishName, "ptdate")
                        .eq(OlapBasicProDO::getStatus, 2)
                        .last("limit 1"));
        if (ptdateBasic != null) {
            allBasicIds.add(ptdateBasic.getId());
        }

        Map<Long, Map<Long, OlapTableFieldMappingDO>> tableFieldMap = new LinkedHashMap<Long, Map<Long, OlapTableFieldMappingDO>>();
        if (!allTableIds.isEmpty() && !allBasicIds.isEmpty()) {
            List<OlapTableFieldMappingDO> mappings = fieldMappingMapper.selectList(
                    new LambdaQueryWrapper<OlapTableFieldMappingDO>()
                            .in(OlapTableFieldMappingDO::getTableId, allTableIds)
                            .in(OlapTableFieldMappingDO::getBasicId, allBasicIds)
                            .eq(OlapTableFieldMappingDO::getStatus, (short) 1)
            );
            for (OlapTableFieldMappingDO m : mappings) {
                tableFieldMap.computeIfAbsent(m.getTableId(), k -> new LinkedHashMap<Long, OlapTableFieldMappingDO>())
                        .putIfAbsent(m.getBasicId(), m);
            }
        }
        log.info("[数据源选择] Step4-字段映射加载, tableCount={}, basicCount={}",
                allTableIds.size(), allBasicIds.size());

        // 4) basic 元数据
        Map<Long, OlapBasicProDO> basicMap = new HashMap<Long, OlapBasicProDO>();
        if (!allBasicIds.isEmpty()) {
            List<OlapBasicProDO> basics = basicProMapper.selectBatchIds(allBasicIds);
            for (OlapBasicProDO b : basics) {
                basicMap.put(b.getId(), b);
            }
        }

        // 4.5) 直接加载 meta_data_source（通过 olap_table_pro.source_id）
        Map<Long, MetaDataSourceDO> metaDataSourceMap = new HashMap<Long, MetaDataSourceDO>();
        Set<Long> sourceIds = new LinkedHashSet<Long>();
        for (OlapTableProDO t : tableMap.values()) {
            if (t.getSourceId() != null) {
                sourceIds.add(t.getSourceId());
            }
        }
        if (!sourceIds.isEmpty()) {
            List<MetaDataSourceDO> dataSources = metaDataSourceMapper.selectBatchIds(sourceIds);
            for (MetaDataSourceDO ds : dataSources) {
                metaDataSourceMap.put(ds.getId(), ds);
            }
        }

        // 4.6) 加载 ptdate 字段映射（用于 timeRange WHERE 过滤）
        if (ptdateBasic != null) {
            if (!basicMap.containsKey(ptdateBasic.getId())) {
                basicMap.put(ptdateBasic.getId(), ptdateBasic);
            }
            for (DataSourceInfo ds : selectedDataSources) {
                Long factTableId = ds.getType() != null && ds.getType() == 2
                        ? ds.getFactTableId() : ds.getTableId();
                FieldMappingInfo ptdateField = buildSingleFieldMapping(ptdateBasic.getId(), factTableId,
                        tableFieldMap.get(factTableId), basicMap, false, ds.getDbDialect());
                ds.setPtdateField(ptdateField);
            }
        }

        // 5) 逐个填充
        for (DataSourceInfo ds : selectedDataSources) {
            if (ds.getType() != null && ds.getType() == 1) {
                fillTableSource(ds, dimensionIds, tableMap, tableFieldMap, basicMap, metaDataSourceMap);
            } else if (ds.getType() != null && ds.getType() == 2) {
                fillModelSource(ds, dimensionIds, modelCache.get(ds.getModelId()),
                        modelDimCache.get(ds.getModelId()),
                        tableMap, tableFieldMap, basicMap, metaDataSourceMap);
            }
        }
    }

    /**
     * 填充单表 DataSourceInfo。
     */
    private void fillTableSource(
            DataSourceInfo ds,
            List<Long> dimensionIds,
            Map<Long, OlapTableProDO> tableMap,
            Map<Long, Map<Long, OlapTableFieldMappingDO>> tableFieldMap,
            Map<Long, OlapBasicProDO> basicMap,
            Map<Long, MetaDataSourceDO> metaDataSourceMap
    ) {
        OlapTableProDO table = tableMap.get(ds.getTableId());
        if (table == null) {
            throw new BizException("未找到物理表信息, tableId=" + ds.getTableId());
        }
        ds.setTableName(table.getTbName());
        ds.setDbName(table.getDbName());
        ds.setTbTypeKey(table.getTbTypeKey() != null ? Integer.valueOf(table.getTbTypeKey()) : null);
        ds.setViewSql(table.getViewSql());

        String dbDialect = resolveEngine(metaDataSourceMap, table.getSourceId());
        ds.setDbDialect(dbDialect);
        ds.setMetaDataSourceId(table.getSourceId());

        Map<Long, OlapTableFieldMappingDO> fields = tableFieldMap.get(ds.getTableId());
        ds.setDimensionFields(buildDimensionFieldsWithTime(dimensionIds, ds.getTableId(), fields,
                basicMap, dbDialect, ds.getPtdateField()));

        List<FieldMappingInfo> indicatorFields = new ArrayList<FieldMappingInfo>();
        if (ds.getIndicatorFields() != null) {
            for (FieldMappingInfo placeholder : ds.getIndicatorFields()) {
                Long basicId = placeholder.getBasicId();
                FieldMappingInfo info = buildSingleFieldMapping(basicId, ds.getTableId(), fields, basicMap, true, dbDialect);
                if (info == null) {
                    throw new BizException("指标字段映射缺失, indicatorId=" + basicId + ", tableId=" + ds.getTableId());
                }
                indicatorFields.add(info);
            }
        }
        ds.setIndicatorFields(indicatorFields);
    }

    /**
     * 填充模型 DataSourceInfo（含 fact 表信息和 join 关系）。
     */
    private void fillModelSource(
            DataSourceInfo ds,
            List<Long> dimensionIds,
            OlapDataModelDO model,
            List<OlapDataModelDimensionDO> dimMappings,
            Map<Long, OlapTableProDO> tableMap,
            Map<Long, Map<Long, OlapTableFieldMappingDO>> tableFieldMap,
            Map<Long, OlapBasicProDO> basicMap,
            Map<Long, MetaDataSourceDO> metaDataSourceMap
    ) {
        if (model == null || model.getFactTableId() == null) {
            throw new BizException("模型未配置事实表, modelId=" + ds.getModelId());
        }
        Long factTableId = model.getFactTableId();
        OlapTableProDO factTable = tableMap.get(factTableId);
        if (factTable == null) {
            throw new BizException("未找到模型事实表, modelId=" + ds.getModelId() + ", factTableId=" + factTableId);
        }
        ds.setFactTableId(factTableId);
        ds.setFactDbName(factTable.getDbName());
        ds.setFactTableName(factTable.getTbName());

        String dbDialect = resolveEngine(metaDataSourceMap, factTable.getSourceId());
        ds.setDbDialect(dbDialect);
        ds.setMetaDataSourceId(factTable.getSourceId());

        // 5.2 按 (factTbId, dimTbId) 聚合，合并复合 join key
        Map<String, ModelJoinInfo> joinByPair = new LinkedHashMap<String, ModelJoinInfo>();
        if (!CollectionUtils.isEmpty(dimMappings)) {
            for (OlapDataModelDimensionDO dmd : dimMappings) {
                if (dmd.getDimTableId() == null || dmd.getDimTableId().equals(factTableId)) {
                    continue;
                }
                String key = factTableId + "_" + dmd.getDimTableId();
                ModelJoinInfo join = joinByPair.get(key);
                if (join == null) {
                    join = new ModelJoinInfo();
                    join.setFactTableId(factTableId);
                    join.setFactDbName(factTable.getDbName());
                    join.setFactTableName(factTable.getTbName());
                    join.setDimTableId(dmd.getDimTableId());
                    OlapTableProDO dimTable = tableMap.get(dmd.getDimTableId());
                    if (dimTable == null) {
                        throw new BizException("未找到模型维度表, modelId=" + ds.getModelId() + ", dimTableId=" + dmd.getDimTableId());
                    }
                    join.setDimDbName(dimTable.getDbName());
                    join.setDimTableName(dimTable.getTbName());
                    join.setJoinType(dmd.getJoinType() != null ? dmd.getJoinType() : "LEFT JOIN");
                    joinByPair.put(key, join);
                }
                if (!isBlank(dmd.getFactFkColumn()) && !isBlank(dmd.getDimPkColumn())) {
                    ModelJoinInfo.JoinKeyPair pair = new ModelJoinInfo.JoinKeyPair();
                    pair.setFactFieldKey(dmd.getFactFkColumn());
                    pair.setDimFieldKey(dmd.getDimPkColumn());
                    join.getJoinKeys().add(pair);
                }
            }
        }
        for (ModelJoinInfo join : joinByPair.values()) {
            if (join.getJoinKeys().isEmpty()) {
                throw new BizException("模型 join 缺少关联字段, modelId=" + ds.getModelId()
                        + ", factTableId=" + join.getFactTableId() + ", dimTableId=" + join.getDimTableId());
            }
        }
        // 5.3 模型涉及的 tableId 顺序：fact 在前，dim 按 join 顺序（用于维度字段解析）
        //     注意：这里用全部 joinByPair，保证字段解析时能搜索所有维表
        List<ModelJoinInfo> allJoins = new ArrayList<ModelJoinInfo>(joinByPair.values());
        List<Long> modelTableOrder = new ArrayList<Long>();
        modelTableOrder.add(factTableId);
        for (ModelJoinInfo join : allJoins) {
            if (!modelTableOrder.contains(join.getDimTableId())) {
                modelTableOrder.add(join.getDimTableId());
            }
        }

        // 5.4 维度字段：优先 fact，找不到再按 join 顺序在 dim 里找
        List<FieldMappingInfo> dimensionFields = new ArrayList<FieldMappingInfo>();
        for (Long dimId : dimensionIds == null ? Collections.<Long>emptyList() : dimensionIds) {
            OlapBasicProDO basic = basicMap.get(dimId);
            String granularity = getTimeGranularity(basic);
            if (granularity != null && ds.getPtdateField() != null) {
                dimensionFields.add(buildTimeDerivedField(dimId, ds.getPtdateField(), granularity, basic));
            } else {
                FieldMappingInfo info = pickFieldFromTables(dimId, modelTableOrder, tableFieldMap, basicMap, false, dbDialect);
                if (info == null) {
                    throw new BizException("维度字段映射缺失, dimensionId=" + dimId + ", modelId=" + ds.getModelId());
                }
                dimensionFields.add(info);
            }
        }
        ds.setDimensionFields(dimensionFields);

        // 5.4.1 只保留维度字段实际引用了的维表 JOIN，减少无用的 LEFT JOIN
        Set<Long> referencedTableIds = new LinkedHashSet<Long>();
        referencedTableIds.add(factTableId);
        for (FieldMappingInfo f : dimensionFields) {
            if (f.getTableId() != null) {
                referencedTableIds.add(f.getTableId());
            }
        }
        List<ModelJoinInfo> neededJoins = new ArrayList<ModelJoinInfo>();
        for (ModelJoinInfo join : allJoins) {
            if (referencedTableIds.contains(join.getDimTableId())) {
                neededJoins.add(join);
            }
        }
        ds.setJoinRelations(neededJoins);

        // 5.5 指标字段：当前阶段强制走 fact 表
        List<FieldMappingInfo> indicatorFields = new ArrayList<FieldMappingInfo>();
        if (ds.getIndicatorFields() != null) {
            for (FieldMappingInfo placeholder : ds.getIndicatorFields()) {
                Long basicId = placeholder.getBasicId();
                FieldMappingInfo info = buildSingleFieldMapping(basicId, factTableId,
                        tableFieldMap.get(factTableId), basicMap, true, dbDialect);
                if (info == null) {
                    throw new BizException("指标字段在 fact 表中未找到, indicatorId=" + basicId
                            + ", modelId=" + ds.getModelId() + ", factTableId=" + factTableId);
                }
                indicatorFields.add(info);
            }
        }
        ds.setIndicatorFields(indicatorFields);
    }

    /**
     * 按指定 tableId 顺序在表字段映射缓存中查找首个命中的字段。
     */
    private FieldMappingInfo pickFieldFromTables(
            Long basicId,
            List<Long> tableIdOrder,
            Map<Long, Map<Long, OlapTableFieldMappingDO>> tableFieldMap,
            Map<Long, OlapBasicProDO> basicMap,
            boolean isIndicator,
            String dbDialect
    ) {
        for (Long tableId : tableIdOrder) {
            FieldMappingInfo info = buildSingleFieldMapping(basicId, tableId, tableFieldMap.get(tableId), basicMap, isIndicator, dbDialect);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    /**
     * 按 dimensionIds 顺序构建单表场景下的字段映射列表。
     */
    private List<FieldMappingInfo> buildDimensionFieldsWithTime(
            List<Long> dimensionIds,
            Long tableId,
            Map<Long, OlapTableFieldMappingDO> fields,
            Map<Long, OlapBasicProDO> basicMap,
            String dbDialect,
            FieldMappingInfo ptdateField
    ) {
        List<FieldMappingInfo> list = new ArrayList<FieldMappingInfo>();
        if (CollectionUtils.isEmpty(dimensionIds)) {
            return list;
        }
        for (Long id : dimensionIds) {
            OlapBasicProDO basic = basicMap.get(id);
            String granularity = getTimeGranularity(basic);
            if (granularity != null && ptdateField != null) {
                list.add(buildTimeDerivedField(id, ptdateField, granularity, basic));
            } else {
                FieldMappingInfo info = buildSingleFieldMapping(id, tableId, fields, basicMap, false, dbDialect);
                if (info == null) {
                    throw new BizException("维度字段映射缺失, basicId=" + id + ", tableId=" + tableId);
                }
                list.add(info);
            }
        }
        return list;
    }

    private String getTimeGranularity(OlapBasicProDO basic) {
        if (basic == null) return null;
        return getTimeGranularityByName(basic.getEnglishName());
    }

    private String getTimeGranularityByName(String englishName) {
        if (isBlank(englishName)) return null;
        String name = englishName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("ptweek"))   return TimeGranularity.WEEK.getValue();
        if (name.startsWith("ptmonth"))  return TimeGranularity.MONTH.getValue();
        if (name.startsWith("ptquarter")) return TimeGranularity.QUARTER.getValue();
        if (name.startsWith("ptyear"))   return TimeGranularity.YEAR.getValue();
        return null;
    }

    private FieldMappingInfo buildTimeDerivedField(Long basicId, FieldMappingInfo ptdateField,
                                                   String granularity, OlapBasicProDO basic) {
        FieldMappingInfo info = new FieldMappingInfo();
        info.setBasicId(basicId);
        info.setTableId(ptdateField.getTableId());
        info.setBasicKey(basic != null && !isBlank(basic.getEnglishName())
                ? basic.getEnglishName() : granularity);
        info.setSrcField(ptdateField.getSrcField());
        info.setSrcFieldType(ptdateField.getSrcFieldType());
        info.setBasicKeyStr(basic != null ? basic.getEnglishName() : null);
        info.setDbDialect(ptdateField.getDbDialect());
        info.setDateFormat(ptdateField.getDateFormat());
        info.setTimeGranularity(granularity);
        return info;
    }

    private List<FieldMappingInfo> buildFieldMappings(
            List<Long> basicIds,
            Long tableId,
            Map<Long, OlapTableFieldMappingDO> fields,
            Map<Long, OlapBasicProDO> basicMap,
            boolean isIndicator,
            String dbDialect
    ) {
        List<FieldMappingInfo> list = new ArrayList<FieldMappingInfo>();
        if (CollectionUtils.isEmpty(basicIds)) {
            return list;
        }
        for (Long id : basicIds) {
            FieldMappingInfo info = buildSingleFieldMapping(id, tableId, fields, basicMap, isIndicator, dbDialect);
            if (info == null) {
                throw new BizException((isIndicator ? "指标" : "维度") + "字段映射缺失, basicId=" + id + ", tableId=" + tableId);
            }
            list.add(info);
        }
        return list;
    }

    /**
     * 构建单个 FieldMappingInfo，找不到返回 null。
     */
    private FieldMappingInfo buildSingleFieldMapping(
            Long basicId,
            Long tableId,
            Map<Long, OlapTableFieldMappingDO> fields,
            Map<Long, OlapBasicProDO> basicMap,
            boolean isIndicator,
            String dbDialect
    ) {
        if (fields == null) {
            return null;
        }
        OlapTableFieldMappingDO mapping = fields.get(basicId);
        if (mapping == null || isBlank(mapping.getFieldKey())) {
            return null;
        }
        OlapBasicProDO basic = basicMap.get(basicId);
        FieldMappingInfo info = new FieldMappingInfo();
        info.setBasicId(basicId);
        info.setTableId(tableId);
        info.setBasicKey(mapping.getBasicKey() );
        info.setSrcField(mapping.getFieldKey());
        info.setSrcFieldType(mapping.getFieldType());
        info.setExpression(mapping.getExpression());
        info.setDateFormat(mapping.getDateFormat());
        info.setBasicType(mapping.getBasicType());
        info.setUnit(mapping.getUnit());
        info.setBasicKeyStr(basic != null ? basic.getEnglishName() : null);
        info.setDbDialect(dbDialect);
        if (isIndicator) {
            info.setAggFunc(pickAggFunction(mapping.getSummary()));
            info.setAlias("alias_" + pickLogicalName(basic, mapping.getFieldKey(), basicId));
        }
        return info;
    }

    /**
     * 解析聚合函数（从 etl_summary 字符串中识别）。
     */
    private String pickAggFunction(String etlSummary) {
        if (isBlank(etlSummary)) {
            return "sum";
        }
        String lower = etlSummary.trim().toLowerCase(Locale.ROOT);
        if ("countdistinct".equals(lower) || "count_distinct".equals(lower)) {
            return "countDistinct";
        }
        if ("count".equals(lower)) return "count";
        if ("avg".equals(lower))   return "avg";
        if ("max".equals(lower))   return "max";
        if ("min".equals(lower))   return "min";
        if ("snapshot".equals(lower) || "last_value".equals(lower)) return "snapshot";
        return "sum";
    }

    /**
     * 选择业务可识别的字段名（用于生成输出列别名）。
     */
    private String pickLogicalName(OlapBasicProDO basic, String fallback, Long id) {
        String candidate = null;
        if (basic != null) {
            if (!isBlank(basic.getEnglishName())) {
                candidate = basic.getEnglishName();
            } else if (!isBlank(basic.getAlias())) {
                candidate = basic.getAlias();
            } else if (!isBlank(basic.getKeyStr())) {
                candidate = basic.getKeyStr();
            }
        }
        if (isBlank(candidate)) {
            candidate = fallback;
        }
        return sanitizeIdentifier(candidate, id);
    }

    private String sanitizeIdentifier(String raw, Long id) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        if (value.isEmpty()) {
            value = "f_" + id;
        }
        return value;
    }

    private String resolveEngine(Map<Long, MetaDataSourceDO> metaDataSourceMap, Long sourceId) {
        if (sourceId == null) {
            return "mysql";
        }
        MetaDataSourceDO ds = metaDataSourceMap.get(sourceId);
        if (ds == null || isBlank(ds.getDbType())) {
            return "mysql";
        }
        return ds.getDbType().trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 第三步：贪心算法 - 选最少数量的 表/模型 覆盖所有指标
     * @param candidates 第一步的候选列表（表/模型）
     * @param indicatorMap 第二步返回的：key=table_1 / model_2，value=指标集合
     * @param requestIndicatorIds 用户要的指标列表
     * @return 最终选中的最少表/模型（带指标信息）
     */
    private List<DataSourceInfo> greedySelectMinTableModel(
            List<CandidateTable> candidates,
            Map<String, Set<Long>> indicatorMap,
            List<Long> requestIndicatorIds
    ) {

        // 1. 初始化
        Set<Long> targetIndicators = new HashSet<>(requestIndicatorIds);
        Set<Long> coveredIndicators = new HashSet<>();
        List<CandidateTable> selectedList = new ArrayList<>();

        // 2. 循环：每次选 覆盖最多未覆盖指标 的表/模型
        while (coveredIndicators.size() < targetIndicators.size()) {
            CandidateTable bestOne = null;
            int maxNewCover = 0;
            Set<Long> bestNewIndicators = new HashSet<>();

            // 遍历所有候选，找出本轮最优
            for (CandidateTable candidate : candidates) {
                String key = candidate.getType() + "_" + candidate.getId();
                Set<Long> candidateIndicators = indicatorMap.get(key);

                if (CollectionUtils.isEmpty(candidateIndicators)) {
                    continue;
                }

                // 计算：这个候选能新增覆盖多少个指标
                Set<Long> newCover = new HashSet<>(candidateIndicators);
                newCover.removeAll(coveredIndicators);
                newCover.retainAll(targetIndicators);
                int newCount = newCover.size();

                // 优先选表：在覆盖数量相同的情况下，优先选择 table
                boolean preferTable = "table".equals(candidate.getType());
                boolean currentIsTable = bestOne != null && "table".equals(bestOne.getType());

                if (newCount > maxNewCover || (newCount == maxNewCover && preferTable && !currentIsTable)) {
                    maxNewCover = newCount;
                    bestOne = candidate;
                    bestNewIndicators = newCover;
                }
            }

            // 没有可选的了，退出
            if (bestOne == null || maxNewCover == 0) {
                break;
            }

            // 选中最优
            selectedList.add(bestOne);
            coveredIndicators.addAll(bestNewIndicators);
        }

        log.info("[贪心算法] 最终选择的表/模型, count={}", selectedList.size());

        // 转换为 DataSourceInfo 并填充指标
        List<DataSourceInfo> result = new ArrayList<>();
        for (CandidateTable ct : selectedList) {
            DataSourceInfo ds = "table".equals(ct.getType())
                    ? DataSourceInfo.fromTable(ct.getId())
                    : DataSourceInfo.fromModel(ct.getId());
            ds.setTableName(ct.getName());

            String key = ct.getType() + "_" + ct.getId();
            Set<Long> indicatorIds = indicatorMap.get(key);

            // 只保留用户请求的指标
            Set<Long> targetSet = new HashSet<>(requestIndicatorIds);
            List<FieldMappingInfo> indicatorFields = new ArrayList<>();
            if (indicatorIds != null) {
                for (Long indId : indicatorIds) {
                    if (targetSet.contains(indId)) {
                        FieldMappingInfo f = new FieldMappingInfo();
                        f.setBasicId(indId);
                        indicatorFields.add(f);
                    }
                }
            }
            ds.setIndicatorFields(indicatorFields);

            log.info("[贪心算法] 最终选中: type={}, id={}, name={}, indicatorIds={}",
                    ct.getType(), ct.getId(), ct.getName(), indicatorIds);

            result.add(ds);
        }

        return result;
    }

    /**
     * 无维度时获取所有可用表/模型作为候选。
     */
    private List<CandidateTable> findAllCandidates(List<Long> preferredTableIds, List<Long> preferredModelIds) {
        long startTime = System.currentTimeMillis();
        List<CandidateTable> candidates = new ArrayList<>();

        boolean hasTablePreferred = !CollectionUtils.isEmpty(preferredTableIds);
        boolean hasModelPreferred = !CollectionUtils.isEmpty(preferredModelIds);

        if (hasTablePreferred) {
            List<OlapTableProDO> tables = tableProMapper.selectBatchIds(preferredTableIds);
            for (OlapTableProDO t : tables) {
                if (t.getStatus() != null && t.getStatus() == 3) {
                    CandidateTable ct = new CandidateTable();
                    ct.setId(t.getId());
                    ct.setName(t.getTbName());
                    ct.setType("table");
                    candidates.add(ct);
                }
            }
        } else if (!hasModelPreferred) {
            List<OlapTableProDO> tables = tableProMapper.selectList(
                    new LambdaQueryWrapper<OlapTableProDO>()
                            .eq(OlapTableProDO::getStatus, 3));
            for (OlapTableProDO t : tables) {
                CandidateTable ct = new CandidateTable();
                ct.setId(t.getId());
                ct.setName(t.getTbName());
                ct.setType("table");
                candidates.add(ct);
            }
        }

        if (hasModelPreferred) {
            List<OlapDataModelDO> models = dataModelMapper.selectBatchIds(preferredModelIds);
            for (OlapDataModelDO m : models) {
                if (m.getStatus() != null && m.getStatus() == 1) {
                    CandidateTable ct = new CandidateTable();
                    ct.setId(m.getId());
                    ct.setName(m.getName());
                    ct.setType("model");
                    candidates.add(ct);
                }
            }
        } else if (!hasTablePreferred) {
            List<OlapDataModelDO> models = dataModelMapper.selectList(
                    new LambdaQueryWrapper<OlapDataModelDO>()
                            .eq(OlapDataModelDO::getStatus, 1));
            for (OlapDataModelDO m : models) {
                CandidateTable ct = new CandidateTable();
                ct.setId(m.getId());
                ct.setName(m.getName());
                ct.setType("model");
                candidates.add(ct);
            }
        }

        log.info("[候选筛选] findAllCandidates 耗时: {}ms, count={}", System.currentTimeMillis() - startTime, candidates.size());
        return candidates;
    }

    /**
     * 查找维度候选数据源（使用SQL UNION一次性查询）
     * 只返回候选列表，不填充字段信息
     */
    private List<CandidateTable> findDimCandidates(List<Long> dimensionIds, List<Long> preferredTableIds, List<Long> preferredModelIds) {
        long startTime = System.currentTimeMillis();

        List<CandidateTable> candidateList = tableProMapper.findDimCandidates(
                dimensionIds,
                dimensionIds.size(),
                preferredTableIds,
                preferredModelIds
        );

        log.info("[维度筛选] findDimCandidates SQL执行耗时: {}ms", System.currentTimeMillis() - startTime);
        return candidateList;
    }

    /**
     * 第二步：批量查询指标 + 过滤【完全不包含任何目标指标】的表
     */
    private IndicatorCheckResult checkTotalIndicatorCoverage(
            List<CandidateTable> firstStepCandidates,
            List<Long> requestIndicatorIds,
            List<CandidateTable> filteredValidCandidates) {

        long startTime = System.currentTimeMillis();

        Set<Long> tableIds = new HashSet<>();
        Set<Long> modelIds = new HashSet<>();

        for (CandidateTable ct : firstStepCandidates) {
            if ("table".equals(ct.getType())) {
                tableIds.add(ct.getId());
            } else if ("model".equals(ct.getType())) {
                modelIds.add(ct.getId());
            }
        }

        List<TableIndicatorDto> result = tableProMapper.batchFindIndicatorsByTablesAndModels(tableIds, modelIds);

        Set<Long> allIndicators = new HashSet<>();
        Map<String, Set<Long>> indicatorMap = new HashMap<>();

        for (TableIndicatorDto dto : result) {
            allIndicators.add(dto.getIndicatorId());
            String key = dto.getObjType() + "_" + dto.getObjId();
            indicatorMap.computeIfAbsent(key, k -> new HashSet<>()).add(dto.getIndicatorId());
        }

        // ===================== 【最终正确过滤】 =====================
        filteredValidCandidates.clear();
        Set<Long> targetSet = new HashSet<>(requestIndicatorIds);

        for (CandidateTable candidate : firstStepCandidates) {
            String key = candidate.getType() + "_" + candidate.getId();
            Set<Long> indicators = indicatorMap.get(key);

            // 无指标 → 跳过
            if (indicators == null || indicators.isEmpty()) {
                continue;
            }

            // 必须包含至少一个用户目标指标
            boolean match = indicators.stream().anyMatch(targetSet::contains);
            if (match) {
                filteredValidCandidates.add(candidate);
                // 打印有效表/模型 + 包含的目标指标
                Set<Long> containTarget = indicators.stream()
                        .filter(targetSet::contains)
                        .collect(Collectors.toSet());

                log.info("[有效表/模型] type={}, id={}, name={}, 包含目标指标={}",
                        candidate.getType(),
                        candidate.getId(),
                        candidate.getName(),
                        containTarget);
            }
        }

        // 判断总覆盖
        boolean covered = allIndicators.containsAll(targetSet);

        log.info("[指标校验] 原候选数:{} → 有效表/模型数:{} | 总指标数:{} | 完全覆盖:{}",
                firstStepCandidates.size(), filteredValidCandidates.size(), allIndicators.size(), covered);

        IndicatorCheckResult checkResult = new IndicatorCheckResult();
        checkResult.setCovered(covered);
        checkResult.setAllIndicators(allIndicators);
        checkResult.setIndicatorMap(indicatorMap);
        return checkResult;
    }

}
