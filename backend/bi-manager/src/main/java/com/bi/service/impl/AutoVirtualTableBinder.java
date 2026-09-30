package com.bi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bi.entity.OlapBasicPro;
import com.bi.entity.OlapTableFieldMapping;
import com.bi.entity.OlapTablePro;
import com.bi.enums.Category;
import com.bi.enums.FieldRegisterType;
import com.bi.enums.MetricStatus;
import com.bi.mapper.OlapBasicProMapper;
import com.bi.mapper.OlapTableFieldMappingMapper;
import com.bi.mapper.OlapTableProMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自动虚拟表公共绑定组件：计算指标绑定、指标组合绑定共用。
 * 自动表判定：tb_type=1 且 tb_name 以 view_auto_ 开头。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoVirtualTableBinder {

    public static final String AUTO_TABLE_PREFIX = "view_auto_";

    private final OlapBasicProMapper basicProMapper;
    private final OlapTableFieldMappingMapper tableFieldMappingMapper;
    private final OlapTableProMapper tableProMapper;

    /**
     * 因子原子指标求交集的计算结果
     */
    @Data
    public static class DimIntersectionResult {
        private Long commonSourceId;
        /** 交集维度（按 height/id 排序） */
        private List<Long> orderedDims;
        private Map<Long, OlapBasicPro> dimBasicMap;
        /** 共同源表上的全部映射行（拷贝源行字段用） */
        private List<OlapTableFieldMapping> tableRows;
        /** 共同源下的一张表（新建表拷贝 dbName/type 用） */
        private OlapTablePro sampleTable;
    }

    /**
     * 求因子原子指标集合的共同数据源与维度交集（与计算指标绑定同一算法）。
     * 返回 null = 跳过（不删旧绑定）；orderedDims 为空 = 交集为空（调用方决定是否删旧绑定）。
     */
    public DimIntersectionResult resolveCommonSourceAndDims(List<Long> factorAtomicMetricIds,
                                                            Long tenantId, Long bizId, String bizLabel) {
        // 1. 因子指标的 index mapping 行
        List<OlapTableFieldMapping> refIndexRows = tableFieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .in(OlapTableFieldMapping::getBasicId, factorAtomicMetricIds)
                        .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.INDEX.getKey())
                        .eq(OlapTableFieldMapping::getStatus, 1)
                        .eq(OlapTableFieldMapping::getTenantId, tenantId));
        if (refIndexRows.isEmpty()) {
            log.warn("{}绑定虚拟表跳过：因子原子指标均无有效映射 | {}Id={}, factorIds={}",
                    bizLabel, bizLabel, bizId, factorAtomicMetricIds);
            return null;
        }
        Set<Long> allTableIds = refIndexRows.stream().map(OlapTableFieldMapping::getTableId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (allTableIds.isEmpty()) {
            log.warn("{}绑定虚拟表跳过：因子原子指标映射无有效表 | {}Id={}", bizLabel, bizLabel, bizId);
            return null;
        }
        Map<Long, OlapTablePro> tableMap = tableProMapper.selectList(
                        new LambdaQueryWrapper<OlapTablePro>().in(OlapTablePro::getId, allTableIds))
                .stream().collect(Collectors.toMap(OlapTablePro::getId, t -> t));

        // 2. 共同数据源
        Set<Long> commonSources = null;
        for (Long factorId : factorAtomicMetricIds) {
            Set<Long> sources = refIndexRows.stream()
                    .filter(r -> factorId.equals(r.getBasicId()))
                    .map(OlapTableFieldMapping::getTableId)
                    .map(tableMap::get).filter(Objects::nonNull)
                    .map(OlapTablePro::getSourceId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (sources.isEmpty()) {
                log.warn("{}绑定虚拟表跳过：因子原子指标无有效表 | {}Id={}, factorId={}", bizLabel, bizLabel, bizId, factorId);
                return null;
            }
            commonSources = commonSources == null ? new HashSet<>(sources) : intersect(commonSources, sources);
        }
        if (commonSources.isEmpty()) {
            log.warn("{}绑定虚拟表跳过：因子原子指标无共同数据源 | {}Id={}, factorIds={}",
                    bizLabel, bizLabel, bizId, factorAtomicMetricIds);
            return null;
        }
        Long commonSourceId = commonSources.iterator().next();
        Set<Long> commonTables = refIndexRows.stream()
                .filter(r -> {
                    OlapTablePro t = tableMap.get(r.getTableId());
                    return t != null && commonSourceId.equals(t.getSourceId());
                })
                .map(OlapTableFieldMapping::getTableId).collect(Collectors.toSet());

        // 3. 共同源表上的映射行 + 已上线维度
        List<OlapTableFieldMapping> tableRows = tableFieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .in(OlapTableFieldMapping::getTableId, commonTables)
                        .eq(OlapTableFieldMapping::getStatus, 1)
                        .eq(OlapTableFieldMapping::getTenantId, tenantId));
        Set<Long> candidateBasicIds = tableRows.stream().map(OlapTableFieldMapping::getBasicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> onlineDimIds = basicProMapper.selectList(new LambdaQueryWrapper<OlapBasicPro>()
                        .in(OlapBasicPro::getId, candidateBasicIds)
                        .eq(OlapBasicPro::getCategory, Category.DIMENSION.getCode())
                        .eq(OlapBasicPro::getStatus, MetricStatus.ONLINE.getCode())
                        .eq(OlapBasicPro::getTenantId, tenantId))
                .stream().map(OlapBasicPro::getId).collect(Collectors.toSet());

        Map<Long, Set<Long>> dimsByTable = new HashMap<>();
        for (OlapTableFieldMapping row : tableRows) {
            if (row.getTableId() == null || row.getBasicId() == null || !onlineDimIds.contains(row.getBasicId())) {
                continue;
            }
            dimsByTable.computeIfAbsent(row.getTableId(), k -> new HashSet<>()).add(row.getBasicId());
        }

        // 4. 每因子指标同表维度求交集
        Set<Long> commonDims = null;
        Map<Long, Set<Long>> factorDimsForLog = new LinkedHashMap<>();
        for (Long factorId : factorAtomicMetricIds) {
            Set<Long> factorTables = refIndexRows.stream()
                    .filter(r -> factorId.equals(r.getBasicId()) && commonTables.contains(r.getTableId()))
                    .map(OlapTableFieldMapping::getTableId).collect(Collectors.toSet());
            Set<Long> dims = new HashSet<>();
            for (Long t : factorTables) {
                Set<Long> td = dimsByTable.get(t);
                if (td != null) {
                    dims.addAll(td);
                }
            }
            factorDimsForLog.put(factorId, dims);
            commonDims = commonDims == null ? new HashSet<>(dims) : intersect(commonDims, dims);
        }
        DimIntersectionResult result = new DimIntersectionResult();
        result.setCommonSourceId(commonSourceId);
        result.setTableRows(tableRows);
        result.setSampleTable(tableMap.values().stream()
                .filter(t -> commonSourceId.equals(t.getSourceId())).findFirst().orElse(null));
        if (commonDims == null || commonDims.isEmpty()) {
            log.warn("{}绑定虚拟表跳过：因子原子指标维度交集为空 | {}Id={}, 各因子维度={}",
                    bizLabel, bizLabel, bizId, factorDimsForLog);
            result.setOrderedDims(Collections.emptyList());
            result.setDimBasicMap(Collections.emptyMap());
            return result;
        }

        // 5. 排序（height 升序，空排后，id 兜底）
        Map<Long, Integer> heightByDim = tableRows.stream()
                .filter(r -> r.getBasicId() != null && r.getHeight() != null)
                .collect(Collectors.toMap(OlapTableFieldMapping::getBasicId, OlapTableFieldMapping::getHeight, (a, b) -> a));
        List<Long> orderedDims = new ArrayList<>(commonDims);
        orderedDims.sort(Comparator
                .comparing((Long d) -> heightByDim.getOrDefault(d, Integer.MAX_VALUE))
                .thenComparing(d -> d));
        Map<Long, OlapBasicPro> dimBasicMap = basicProMapper.selectList(
                        new LambdaQueryWrapper<OlapBasicPro>().in(OlapBasicPro::getId, orderedDims))
                .stream().collect(Collectors.toMap(OlapBasicPro::getId, d -> d));
        result.setOrderedDims(orderedDims);
        result.setDimBasicMap(dimBasicMap);
        return result;
    }

    /**
     * 查找可复用的自动表：同租户同源、维度集合完全一致，取 id 最小。
     */
    public Long findReusableTable(Set<Long> dims, Long sourceId, Long tenantId) {
        List<OlapTablePro> autoTables = tableProMapper.selectList(new LambdaQueryWrapper<OlapTablePro>()
                .eq(OlapTablePro::getTbType, "1")
                .likeRight(OlapTablePro::getTbName, AUTO_TABLE_PREFIX)
                .eq(OlapTablePro::getSourceId, sourceId)
                .eq(OlapTablePro::getTenantId, tenantId));
        if (autoTables.isEmpty()) {
            return null;
        }
        Set<Long> autoTableIds = autoTables.stream().map(OlapTablePro::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> dimsByAutoTable = tableFieldMappingMapper.selectList(
                        new LambdaQueryWrapper<OlapTableFieldMapping>()
                                .in(OlapTableFieldMapping::getTableId, autoTableIds)
                                .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.DIMENSION.getKey())
                                .eq(OlapTableFieldMapping::getStatus, 1)
                                .eq(OlapTableFieldMapping::getTenantId, tenantId))
                .stream()
                .filter(r -> r.getTableId() != null && r.getBasicId() != null)
                .collect(Collectors.groupingBy(OlapTableFieldMapping::getTableId,
                        Collectors.mapping(OlapTableFieldMapping::getBasicId, Collectors.toSet())));
        return autoTables.stream()
                .filter(t -> dims.equals(dimsByAutoTable.getOrDefault(t.getId(), Collections.emptySet())))
                .map(OlapTablePro::getId)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * 重拼自动表 view_sql（dims 按 height/id 排序 + 指标按加入顺序）；表上无指标行则删表。
     */
    public void refreshAutoTable(Long tableId, Long tenantId) {
        OlapTablePro table = tableProMapper.selectById(tableId);
        if (table == null) {
            return;
        }
        List<OlapTableFieldMapping> rows = tableFieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getTableId, tableId)
                        .eq(OlapTableFieldMapping::getStatus, 1)
                        .eq(OlapTableFieldMapping::getTenantId, tenantId));
        List<OlapTableFieldMapping> dimRows = rows.stream()
                .filter(r -> FieldRegisterType.DIMENSION.getKey().equals(r.getBasicTypeKey()))
                .sorted(Comparator
                        .comparing((OlapTableFieldMapping r) -> r.getHeight() == null ? Integer.MAX_VALUE : r.getHeight())
                        .thenComparing(OlapTableFieldMapping::getId))
                .collect(Collectors.toList());
        List<OlapTableFieldMapping> indexRows = rows.stream()
                .filter(r -> FieldRegisterType.INDEX.getKey().equals(r.getBasicTypeKey()))
                .sorted(Comparator.comparing(OlapTableFieldMapping::getId))
                .collect(Collectors.toList());
        if (indexRows.isEmpty()) {
            tableFieldMappingMapper.delete(new LambdaQueryWrapper<OlapTableFieldMapping>()
                    .eq(OlapTableFieldMapping::getTableId, tableId)
                    .eq(OlapTableFieldMapping::getTenantId, tenantId));
            tableProMapper.deleteById(tableId);
            log.info("自动虚拟表已清空并删除 | tableId={}, tbName={}", tableId, table.getTbName());
            return;
        }
        Set<Long> basicIds = rows.stream().map(OlapTableFieldMapping::getBasicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OlapBasicPro> basicMap = basicProMapper.selectList(
                        new LambdaQueryWrapper<OlapBasicPro>().in(OlapBasicPro::getId, basicIds))
                .stream().collect(Collectors.toMap(OlapBasicPro::getId, b -> b));
        StringBuilder sql = new StringBuilder("SELECT ");
        int idx = 1;
        for (OlapTableFieldMapping row : dimRows) {
            OlapBasicPro dim = basicMap.get(row.getBasicId());
            if (dim == null) {
                continue;
            }
            if (idx > 1) {
                sql.append(", ");
            }
            sql.append(idx++).append(" AS ").append(dim.getEnglishName());
        }
        for (OlapTableFieldMapping row : indexRows) {
            OlapBasicPro m = basicMap.get(row.getBasicId());
            if (m == null) {
                continue;
            }
            if (idx > 1) {
                sql.append(", ");
            }
            sql.append(idx++).append(" AS ").append(m.getEnglishName());
        }
        table.setViewSql(sql.toString());
        tableProMapper.updateById(table);
    }

    public OlapTableFieldMapping buildDimMapping(Long tableId, OlapBasicPro dim,
                                                 OlapTableFieldMapping src, Long tenantId) {
        OlapTableFieldMapping m = new OlapTableFieldMapping();
        m.setTableId(tableId);
        m.setFieldKey(dim.getEnglishName());
        m.setFieldName(dim.getChineseName());
        m.setBasicId(dim.getId());
        m.setBasicType(FieldRegisterType.DIMENSION.getLabel());
        m.setBasicTypeKey(FieldRegisterType.DIMENSION.getKey());
        m.setBasicKey(dim.getKeyStr());
        m.setBasicName(dim.getChineseName());
        if (src != null) {
            m.setFieldType(src.getFieldType());
            m.setFieldTypeName(src.getFieldTypeName());
            m.setFieldNote(src.getFieldNote());
            m.setSummary(src.getSummary());
            m.setSummaryKey(src.getSummaryKey());
            m.setHeight(src.getHeight());
            m.setUnit(src.getUnit());
            m.setExpression(src.getExpression());
            m.setLookBackFlag(src.getLookBackFlag());
        }
        m.setStatus(1);
        m.setRegisterStatus("已注册");
        m.setTenantId(tenantId);
        return m;
    }

    public OlapTableFieldMapping buildIndexMapping(Long tableId, OlapBasicPro basic, Long tenantId) {
        OlapTableFieldMapping m = new OlapTableFieldMapping();
        m.setTableId(tableId);
        m.setFieldKey(basic.getEnglishName());
        m.setFieldName(basic.getChineseName());
        m.setBasicId(basic.getId());
        m.setBasicType(FieldRegisterType.INDEX.getLabel());
        m.setBasicTypeKey(FieldRegisterType.INDEX.getKey());
        m.setBasicKey(basic.getKeyStr());
        m.setBasicName(basic.getChineseName());
        m.setStatus(1);
        m.setRegisterStatus("已注册");
        m.setTenantId(tenantId);
        return m;
    }

    public String genUniqueViewName(String englishName, Long tenantId) {
        String base = AUTO_TABLE_PREFIX + englishName;
        String candidate = base;
        int seq = 2;
        while (true) {
            LambdaQueryWrapper<OlapTablePro> wrapper = new LambdaQueryWrapper<OlapTablePro>()
                    .eq(OlapTablePro::getTbName, candidate)
                    .eq(OlapTablePro::getTenantId, tenantId);
            if (tableProMapper.selectCount(wrapper) == 0) {
                return candidate;
            }
            candidate = base + "_" + seq++;
        }
    }

    /**
     * 在 rows 中找该 basic_id 的源映射行（拷贝字段信息用）
     */
    public OlapTableFieldMapping findSourceRow(List<OlapTableFieldMapping> rows, Long basicId) {
        return rows.stream()
                .filter(r -> basicId.equals(r.getBasicId()))
                .sorted(Comparator.comparing((OlapTableFieldMapping r) ->
                        r.getHeight() == null ? Integer.MAX_VALUE : r.getHeight()))
                .findFirst().orElse(null);
    }

    public Set<Long> intersect(Set<Long> a, Set<Long> b) {
        Set<Long> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
