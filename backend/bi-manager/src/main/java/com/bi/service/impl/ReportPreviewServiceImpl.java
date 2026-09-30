package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.bi.dto.CompatibleFieldQueryDTO;
import com.bi.dto.DimMetricQueryVO;
import com.bi.enums.GroupItemType;
import com.bi.util.CandidateStructureSupport;
import com.bi.util.MetricFormulaParser;
import com.bi.mapper.OlapFieldRelationMapper;
import com.bi.service.IReportPreviewService;
import com.bi.vo.*;
import com.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportPreviewServiceImpl implements IReportPreviewService {

    private final OlapFieldRelationMapper fieldRelationMapper;

    @Override
    public CompatibleFieldsVO listCompatibleFields(CompatibleFieldQueryDTO query, Long tenantId) {
        List<Long> metricIds = normalizeIds(query.getMetricIds());
        List<Long> dimensionIds = normalizeIds(query.getDimensionIds());
        String keyword = StrUtil.trimToNull(query.getKeyword());

        Set<Long> factTableIds = resolveFactTableContext(metricIds, dimensionIds, tenantId);

        CompatibleFieldsVO vo = new CompatibleFieldsVO();
        vo.setFactTableIds(factTableIds == null ? null : new ArrayList<>(factTableIds));

        if (factTableIds == null) {
            List<CompatibleFieldRow> allMetrics = new ArrayList<>();
            Set<Long> seenIds = new HashSet<>();
            addRowsDedup(allMetrics, seenIds, fieldRelationMapper.selectAllOnlineMetrics(keyword, tenantId), Collections.emptySet());
            addRowsDedup(allMetrics, seenIds, fieldRelationMapper.selectCalcDeriveMetrics(keyword, tenantId), Collections.emptySet());
            vo.setDimensions(toDimensionVOs(fieldRelationMapper.selectAllOnlineDimensions(keyword, tenantId)));
            vo.setMetrics(toMetricVOs(allMetrics));
            return vo;
        }

        if (factTableIds.isEmpty()) {
            vo.setDimensions(mergeSelectedDimensions(Collections.emptyList(), dimensionIds, tenantId));
            vo.setMetrics(mergeSelectedMetrics(Collections.emptyList(), metricIds, tenantId));
            return vo;
        }

        List<CompatibleFieldRow> dimensions = fieldRelationMapper.selectCompatibleDimensions(factTableIds, keyword, tenantId);
        List<CompatibleFieldRow> metrics = fieldRelationMapper.selectCompatibleMetrics(factTableIds, keyword, tenantId);
        // 合并计算/派生指标（根据事实表上下文过滤）
        Set<Long> availableMetricIds = new HashSet<>(fieldRelationMapper.selectAvailableMetricIdsByFactTables(factTableIds, tenantId));
        List<CompatibleFieldRow> calcDeriveFiltered = filterCalcDeriveByContext(
                fieldRelationMapper.selectCalcDeriveMetrics(keyword, tenantId), availableMetricIds);
        // 去重合并
        Set<Long> metricSeenIds = new HashSet<>();
        for (CompatibleFieldRow row : metrics) {
            metricSeenIds.add(row.getId());
        }
        for (CompatibleFieldRow row : calcDeriveFiltered) {
            if (metricSeenIds.add(row.getId())) {
                metrics.add(row);
            }
        }

        vo.setDimensions(mergeSelectedDimensions(dimensions, dimensionIds, tenantId));
        vo.setMetrics(mergeSelectedMetrics(metrics, metricIds, tenantId));
        return vo;
    }

    @Override
    public List<DimMetricTreeDTO> dimAndMetric(DimMetricQueryVO query, Long tenantId) {
        String keyword = StrUtil.trimToNull(query.getKeyword());
        String type = query.getType();
        List<Long> metricIds = normalizeIds(query.getMetricIds());
        List<Long> dimensionIds = normalizeIds(query.getDimensionIds());

        if ("dim".equals(type)) {
            return queryDimTree(keyword, metricIds, dimensionIds, tenantId);
        }
        if ("metric".equals(type)) {
            return queryMetricTree(keyword, metricIds, dimensionIds, tenantId);
        }
        return Collections.emptyList();
    }

    private List<DimMetricTreeDTO> queryDimTree(String keyword, List<Long> metricIds,
                                                 List<Long> dimensionIds, Long tenantId) {
        Set<Long> factTableIds = resolveFactTableContext(metricIds, dimensionIds, tenantId);

        List<CompatibleFieldRow> rows;
        if (factTableIds == null) {
            rows = fieldRelationMapper.selectAllOnlineDimensions(keyword, tenantId);
        } else if (factTableIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            rows = fieldRelationMapper.selectCompatibleDimensions(factTableIds, keyword, tenantId);
        }

        if (!dimensionIds.isEmpty()) {
            Set<Long> selectedSet = new HashSet<>(dimensionIds);
            rows = rows.stream()
                    .filter(r -> !selectedSet.contains(r.getId()))
                    .collect(Collectors.toList());
        }

        return rows.stream().map(this::toDimMetricTreeDTO).collect(Collectors.toList());
    }

    private List<DimMetricTreeDTO> queryMetricTree(String keyword, List<Long> metricIds,
                                                    List<Long> dimensionIds, Long tenantId) {
        // 指标互滤只由已选维度驱动：已选指标不参与上下文解析（指标间不做互滤）
        Set<Long> factTableIds = resolveFactTableContext(Collections.emptyList(), dimensionIds, tenantId);

        Set<Long> selectedSet = metricIds.isEmpty() ? Collections.emptySet() : new HashSet<>(metricIds);
        List<CompatibleFieldRow> rows = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        if (factTableIds == null) {
            addRowsDedup(rows, seenIds, fieldRelationMapper.selectAllOnlineMetrics(keyword, tenantId), selectedSet);
            addRowsDedup(rows, seenIds, fieldRelationMapper.selectCalcDeriveMetrics(keyword, tenantId), selectedSet);
        } else if (factTableIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            addRowsDedup(rows, seenIds, fieldRelationMapper.selectCompatibleMetrics(factTableIds, keyword, tenantId), selectedSet);
            List<CompatibleFieldRow> calcDeriveRows = fieldRelationMapper.selectCalcDeriveMetrics(keyword, tenantId);
            Set<Long> availableMetricIds = new HashSet<>(fieldRelationMapper.selectAvailableMetricIdsByFactTables(factTableIds, tenantId));
            addRowsDedup(rows, seenIds, filterCalcDeriveByContext(calcDeriveRows, availableMetricIds), selectedSet);
        }

        return rows.stream().map(this::toDimMetricTreeDTO).collect(Collectors.toList());
    }

    private void addRowsDedup(List<CompatibleFieldRow> target, Set<Long> seenIds,
                               List<CompatibleFieldRow> rows, Set<Long> excludeIds) {
        for (CompatibleFieldRow row : rows) {
            if (excludeIds.contains(row.getId())) {
                continue;
            }
            if (seenIds.add(row.getId())) {
                target.add(row);
            }
        }
    }

    private DimMetricTreeDTO toDimMetricTreeDTO(CompatibleFieldRow row) {
        DimMetricTreeDTO dto = new DimMetricTreeDTO();
        dto.setId(row.getId());
        dto.setName(row.getChineseName());
        dto.setKey(row.getEnglishName());
        return dto;
    }
//
//    private List<DimTreeItemVO> toFieldTreeDimensions(List<FieldTreeRow> rows) {
//        List<DimTreeItemVO> list = rows.stream().map(row -> {
//            DimTreeItemVO vo = new DimTreeItemVO();
//            vo.setId(row.getId());
//            vo.setName(row.getChineseName());
//            vo.setKey(row.getEnglishName());
//            vo.setFixedFirst(isFixedFirstFieldTreeDimension(row));
//            return vo;
//        }).collect(Collectors.toList());
//        list.sort(Comparator
//                .comparing((DimTreeItemVO vo) -> !Boolean.TRUE.equals(vo.getFixedFirst()))
//                .thenComparing(DimTreeItemVO::getName, Comparator.nullsLast(String::compareTo)));
//        return list;
//    }
//
//    private List<FieldTreeMetricGroupVO> toFieldTreeMetricGroups(List<FieldTreeRow> rows) {
//        Map<String, List<FieldTreeRow>> grouped = rows.stream()
//                .collect(Collectors.groupingBy(
//                        row -> StrUtil.isNotBlank(row.getOlapLabelName()) ? row.getOlapLabelName() : "通用",
//                        LinkedHashMap::new,
//                        Collectors.toList()));
//        return grouped.entrySet().stream().map(entry -> {
//            FieldTreeMetricGroupVO group = new FieldTreeMetricGroupVO();
//            group.setName(entry.getKey());
//            group.setItems(entry.getValue().stream().map(row -> {
//                MetricTreeItemVO item = new MetricTreeItemVO();
//                item.setId(row.getId());
//                item.setName(row.getChineseName());
//                item.setKey(row.getEnglishName());
//                return item;
//            }).collect(Collectors.toList()));
//            return group;
//        }).collect(Collectors.toList());
//    }

    private boolean isFixedFirstFieldTreeDimension(FieldTreeRow row) {
        return StrUtil.isNotBlank(row.getPartitionField()) || StrUtil.isNotBlank(row.getTimeDynamic());
    }

    /**
     * 根据已选指标/维度解析共同事实表上下文；无选择时返回 null 表示不过滤。
     */
    private Set<Long> resolveFactTableContext(List<Long> metricIds, List<Long> dimensionIds, Long tenantId) {
        if (metricIds.isEmpty() && dimensionIds.isEmpty()) {
            return null;
        }

        Set<Long> factTableIds = null;

        if (!metricIds.isEmpty()) {
            factTableIds = intersectFactTablesByMetrics(metricIds, tenantId);
        }
        if (!dimensionIds.isEmpty()) {
            Set<Long> dimFactTables = intersectFactTablesByDimensions(dimensionIds, tenantId);
            if (factTableIds == null) {
                factTableIds = dimFactTables;
            } else {
                factTableIds.retainAll(dimFactTables);
            }
        }
        return factTableIds;
    }

    private Set<Long> intersectFactTablesByMetrics(List<Long> metricIds, Long tenantId) {
        Set<Long> result = null;
        for (Long metricId : metricIds) {
            Set<Long> tables = resolveFactTablesForMetric(metricId, tenantId);
            if (tables.isEmpty()) {
                throw new BizException(400, "指标未关联已上线决策表: " + metricId);
            }
            if (result == null) {
                result = tables;
            } else {
                result.retainAll(tables);
            }
        }
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * 解析单个指标对应的事实表集合。
     * 原子指标查 mapping 表；派生指标追溯 indicatorId；
     * 计算指标提取所有 indicatorList 引用的原子指标求交集。
     */
    private Set<Long> resolveFactTablesForMetric(Long metricId, Long tenantId) {
        // 原子指标：走 mapping 表
        Set<Long> tables = new HashSet<>(fieldRelationMapper.selectFactTableIdsByMetricId(metricId, tenantId));
        if (!tables.isEmpty()) {
            return tables;
        }
        // 可能在映射表中找不到，检查是否是计算/派生指标
        var info = fieldRelationMapper.selectIndicatorInfoById(metricId, tenantId);
        if (info == null) {
            return Collections.emptySet();
        }
        // 派生指标：追溯引用的原子指标
        if (MetricFormulaParser.isDeriveMetric(info.getDerivativeProduction())) {
            Long refId = MetricFormulaParser.extractDeriveReferencedId(info.getDerivativeProduction());
            if (refId != null) {
                return new HashSet<>(fieldRelationMapper.selectFactTableIdsByMetricId(refId, tenantId));
            }
            return Collections.emptySet();
        }
        // 计算指标：所有引用原子指标的交集
        if (MetricFormulaParser.isCalcMetric(info.getCalculatedProduction())) {
            List<Long> refIds = MetricFormulaParser.extractCalcReferencedIds(info.getCalculatedProduction());
            return intersectFactTablesByReferencedIds(refIds, tenantId);
        }
        return Collections.emptySet();
    }

    private Set<Long> intersectFactTablesByReferencedIds(List<Long> refIds, Long tenantId) {
        if (refIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> result = null;
        for (Long refId : refIds) {
            Set<Long> tables = new HashSet<>(fieldRelationMapper.selectFactTableIdsByMetricId(refId, tenantId));
            if (tables.isEmpty()) {
                return Collections.emptySet();
            }
            if (result == null) {
                result = tables;
            } else {
                result.retainAll(tables);
            }
        }
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * 根据事实表上下文过滤计算/派生指标：
     * 只保留所有引用原子指标都在当前可用集合中的指标。
     */
    private List<CompatibleFieldRow> filterCalcDeriveByContext(List<CompatibleFieldRow> rows, Set<Long> availableMetricIds) {
        return rows.stream()
                .filter(row -> {
                    if (StrUtil.isNotBlank(row.getCalculatedProduction())) {
                        List<Long> refIds = MetricFormulaParser.extractCalcReferencedIds(row.getCalculatedProduction());
                        return MetricFormulaParser.allReferencedInSet(refIds, availableMetricIds);
                    }
                    if (StrUtil.isNotBlank(row.getDerivativeProduction())) {
                        Long refId = MetricFormulaParser.extractDeriveReferencedId(row.getDerivativeProduction());
                        return refId != null && availableMetricIds.contains(refId);
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    private Set<Long> intersectFactTablesByDimensions(List<Long> dimensionIds, Long tenantId) {
        Set<Long> result = null;
        for (Long dimensionId : dimensionIds) {
            Set<Long> tables = new HashSet<>(fieldRelationMapper.selectFactTableIdsByDimensionId(dimensionId, tenantId));
            if (tables.isEmpty()) {
                throw new BizException(400, "维度未关联已上线决策表或数据模型: " + dimensionId);
            }
            if (result == null) {
                result = tables;
            } else {
                result.retainAll(tables);
            }
        }
        return result == null ? Collections.emptySet() : result;
    }

    private List<CompatibleFieldVO> mergeSelectedDimensions(List<CompatibleFieldRow> rows,
                                                            List<Long> selectedIds,
                                                            Long tenantId) {
        Map<Long, CompatibleFieldVO> map = rows.stream()
                .collect(Collectors.toMap(CompatibleFieldRow::getId, this::toDimensionVO,
                        (a, b) -> a, LinkedHashMap::new));
        if (!selectedIds.isEmpty()) {
            for (CompatibleFieldRow row : fieldRelationMapper.selectDimensionsByIds(selectedIds, tenantId)) {
                map.putIfAbsent(row.getId(), toDimensionVO(row));
            }
        }
        return sortDimensions(new ArrayList<>(map.values()));
    }

    private List<CompatibleFieldVO> mergeSelectedMetrics(List<CompatibleFieldRow> rows,
                                                           List<Long> selectedIds,
                                                           Long tenantId) {
        Map<Long, CompatibleFieldVO> map = rows.stream()
                .collect(Collectors.toMap(CompatibleFieldRow::getId, this::toMetricVO,
                        (a, b) -> a, LinkedHashMap::new));
        if (!selectedIds.isEmpty()) {
            for (CompatibleFieldRow row : fieldRelationMapper.selectMetricsByIds(selectedIds, tenantId)) {
                map.putIfAbsent(row.getId(), toMetricVO(row));
            }
            for (CompatibleFieldRow row : fieldRelationMapper.selectCalcDeriveMetricsByIds(selectedIds, tenantId)) {
                map.putIfAbsent(row.getId(), toMetricVO(row));
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<CompatibleFieldVO> toDimensionVOs(List<CompatibleFieldRow> rows) {
        return sortDimensions(rows.stream().map(this::toDimensionVO).collect(Collectors.toList()));
    }

    private List<CompatibleFieldVO> toMetricVOs(List<CompatibleFieldRow> rows) {
        return rows.stream().map(this::toMetricVO).collect(Collectors.toList());
    }

    private CompatibleFieldVO toDimensionVO(CompatibleFieldRow row) {
        CompatibleFieldVO vo = new CompatibleFieldVO();
        vo.setId(row.getId());
        vo.setCode(row.getCode());
        vo.setName(row.getChineseName());
        vo.setEnglishName(row.getEnglishName());
        vo.setItemType(GroupItemType.DIMENSION.getCode());
        vo.setTypeLabel(resolveDimensionTypeLabel(row.getDimensionType(), row.getPartitionField(), row.getTimeDynamic()));
        vo.setFixedFirst(isFixedFirstDimension(row));
        CandidateStructureSupport.applyDimensionDefaults(vo, row.getDimensionType(),
                row.getPartitionField(), row.getTimeDynamic());
        return vo;
    }

    private CompatibleFieldVO toMetricVO(CompatibleFieldRow row) {
        CompatibleFieldVO vo = new CompatibleFieldVO();
        vo.setId(row.getId());
        vo.setCode(row.getCode());
        vo.setName(row.getChineseName());
        vo.setEnglishName(row.getEnglishName());
        GroupItemType itemType = resolveMetricItemType(row);
        vo.setItemType(itemType.getCode());
        vo.setTypeLabel(resolveMetricTypeLabel(itemType));
        vo.setFixedFirst(false);
        CandidateStructureSupport.applyMetricDefaults(vo, row.getUnit(), itemType);
        return vo;
    }

    private GroupItemType resolveMetricItemType(CompatibleFieldRow row) {
        if (StrUtil.isNotBlank(row.getCalculatedProduction())) {
            return GroupItemType.CALCULATED_METRIC;
        }
        if (StrUtil.isNotBlank(row.getDerivativeProduction())) {
            return GroupItemType.DERIVED_METRIC;
        }
        return GroupItemType.ATOMIC_METRIC;
    }

    private String resolveMetricTypeLabel(GroupItemType itemType) {
        switch (itemType) {
            case CALCULATED_METRIC:
                return "计算";
            case DERIVED_METRIC:
                return "派生";
            default:
                return "原子";
        }
    }

    private String resolveDimensionTypeLabel(Integer dimensionType, String partitionField, String timeDynamic) {
        if (StrUtil.isNotBlank(partitionField) || StrUtil.isNotBlank(timeDynamic)) {
            return "标准维·时间";
        }
        if (Objects.equals(dimensionType, 1)) {
            return "标准维";
        }
        if (Objects.equals(dimensionType, 2)) {
            return "杂项维";
        }
        return "维度";
    }

    private boolean isFixedFirstDimension(CompatibleFieldRow row) {
        return StrUtil.isNotBlank(row.getPartitionField()) || StrUtil.isNotBlank(row.getTimeDynamic());
    }

    private List<CompatibleFieldVO> sortDimensions(List<CompatibleFieldVO> list) {
        list.sort(Comparator
                .comparing((CompatibleFieldVO vo) -> !Boolean.TRUE.equals(vo.getFixedFirst()))
                .thenComparing(CompatibleFieldVO::getName, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}
