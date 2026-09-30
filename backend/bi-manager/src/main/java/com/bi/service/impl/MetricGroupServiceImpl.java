package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.MetricGroupSaveDTO;
import com.bi.entity.OlapBasicProIndicator;
import com.bi.entity.OlapReportGroup;
import com.bi.entity.OlapTableFieldMapping;
import com.bi.entity.OlapTablePro;
import com.bi.enums.FieldRegisterType;
import com.bi.enums.MetricStatus;
import com.bi.mapper.OlapBasicProIndicatorMapper;
import com.bi.mapper.OlapReportGroupMapper;
import com.bi.mapper.OlapTableFieldMappingMapper;
import com.bi.mapper.OlapTableProMapper;
import com.bi.service.IMetricGroupService;
import com.bi.util.MetricFormulaParser;
import com.bi.vo.ApiPageResult;
import com.bi.vo.MetricGroupVO;
import com.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricGroupServiceImpl implements IMetricGroupService {

    private final OlapReportGroupMapper groupMapper;
    private final AutoVirtualTableBinder autoVirtualTableBinder;
    private final OlapBasicProIndicatorMapper indicatorMapper;
    private final OlapTableFieldMappingMapper tableFieldMappingMapper;
    private final OlapTableProMapper tableProMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(MetricGroupSaveDTO dto, Long tenantId) {
        String code = dto.getGroupCode().trim();
        if (dto.getId() == null) {
            validateCodeUnique(code, tenantId, null);
            OlapReportGroup group = new OlapReportGroup();
            group.setGroupCode(code);
            group.setGroupName(dto.getGroupName().trim());
            group.setDescription(dto.getDescription());
            group.setGroupConfig(dto.getGroupConfig());
            group.setStatus(MetricStatus.ONLINE.getCode());
            group.setTenantId(tenantId);
            groupMapper.insert(group);
            bindGroupToVirtualTable(group, tenantId);
            return group.getId();
        }
        OlapReportGroup group = requireGroup(dto.getId(), tenantId);
        validateCodeUnique(code, tenantId, dto.getId());
        group.setGroupCode(code);
        group.setGroupName(dto.getGroupName().trim());
        group.setDescription(dto.getDescription());
        group.setGroupConfig(dto.getGroupConfig());
        group.setStatus(MetricStatus.ONLINE.getCode());
        groupMapper.updateById(group);
        bindGroupToVirtualTable(group, tenantId);
        return group.getId();
    }

    @Override
    public ApiPageResult<MetricGroupVO> listGroups(String keyword, Integer status, Integer page, Integer pageSize, Long tenantId) {
        LambdaQueryWrapper<OlapReportGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapReportGroup::getTenantId, tenantId);
        wrapper.eq(status != null, OlapReportGroup::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(OlapReportGroup::getGroupName, keyword)
                    .or().like(OlapReportGroup::getGroupCode, keyword));
        }
        wrapper.orderByDesc(OlapReportGroup::getUpdatedAt);

        int pageNo = page != null && page > 0 ? page : 1;
        int size = pageSize != null && pageSize > 0 ? pageSize : 10;
        Page<OlapReportGroup> result = groupMapper.selectPage(new Page<>(pageNo, size), wrapper);

        return new ApiPageResult<>(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public MetricGroupVO getDetail(Long id, Long tenantId) {
        return toVO(requireGroup(id, tenantId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void online(Long id, Long tenantId) {
        OlapReportGroup group = requireGroup(id, tenantId);
        if (MetricStatus.ONLINE.getCode() == group.getStatus()) {
            return;
        }
        group.setStatus(MetricStatus.ONLINE.getCode());
        groupMapper.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, Long tenantId) {
        OlapReportGroup group = requireGroup(id, tenantId);
        if (MetricStatus.OFFLINE.getCode() == group.getStatus()) {
            return;
        }
        group.setStatus(MetricStatus.OFFLINE.getCode());
        groupMapper.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long tenantId) {
        OlapReportGroup group = requireGroup(id, tenantId);
        if (group.getStatus() != null && group.getStatus() == MetricStatus.ONLINE.getCode()) {
            throw new BizException(403, "已上线状态不可删除，请先下线");
        }
        unbindGroup(group, tenantId);
        groupMapper.deleteById(group.getId());
    }

    // ==================== 指标组合虚拟表绑定 ====================

    /**
     * 组合绑定入口：失败仅记日志，不影响组合保存结果。
     */
    private void bindGroupToVirtualTable(OlapReportGroup group, Long tenantId) {
        try {
            doBindGroup(group, tenantId);
        } catch (Exception e) {
            log.error("指标组合绑定虚拟表失败，不影响组合保存 | groupId={}, error={}", group.getId(), e.getMessage(), e);
        }
    }

    /**
     * 组合绑定核心（沿用计算指标注册流程）：
     * 1) 组内指标 → 因子原子指标集合（原子指标本身 + 计算/派生指标引用的原子指标）
     * 2) 因子集合求共同源与维度交集（与计算指标绑定同一算法）
     * 3) 交集空 → 解绑组合行；非空 → 复用/新建 view_auto_ 表
     * 4) 写"组合自身"映射行：basic_id=组合id、basic_key=groupCode、basic_name=groupName
     *    （查数侧按 code 找到组合后不再往下走，转去解析组合的因子指标）
     */
    private void doBindGroup(OlapReportGroup group, Long tenantId) {
        List<Long> indicatorIds = parseIndicatorIds(group.getGroupConfig());
        if (indicatorIds.isEmpty()) {
            log.warn("指标组合绑定虚拟表跳过：无组内指标 | groupId={}", group.getId());
            return;
        }
        Set<Long> factorAtomicMetricIds = resolveFactorAtomicMetricIds(indicatorIds, tenantId);
        if (factorAtomicMetricIds.isEmpty()) {
            log.warn("指标组合绑定虚拟表跳过：因子原子指标集合为空 | groupId={}", group.getId());
            return;
        }

        AutoVirtualTableBinder.DimIntersectionResult ctx = autoVirtualTableBinder.resolveCommonSourceAndDims(
                new ArrayList<>(factorAtomicMetricIds), tenantId, group.getId(), "指标组合");
        if (ctx == null) {
            return;
        }
        if (ctx.getOrderedDims().isEmpty()) {
            unbindGroup(group, tenantId);
            return;
        }
        Set<Long> commonDims = new HashSet<>(ctx.getOrderedDims());

        // 定位已有绑定：组合自身的映射行（basic_id=组合id）所在表
        OlapTableFieldMapping groupRow = findGroupRow(group.getId(), tenantId);
        Long boundTableId = groupRow == null ? null : groupRow.getTableId();
        if (boundTableId != null && tableProMapper.selectById(boundTableId) == null) {
            tableFieldMappingMapper.deleteById(groupRow.getId());
            boundTableId = null;
            groupRow = null;
        }

        if (boundTableId != null) {
            Set<Long> tableDimIds = tableFieldMappingMapper.selectList(
                            new LambdaQueryWrapper<OlapTableFieldMapping>()
                                    .eq(OlapTableFieldMapping::getTableId, boundTableId)
                                    .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.DIMENSION.getKey())
                                    .eq(OlapTableFieldMapping::getStatus, 1)
                                    .eq(OlapTableFieldMapping::getTenantId, tenantId))
                    .stream().map(OlapTableFieldMapping::getBasicId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (tableDimIds.equals(commonDims)) {
                // 维度集合未变：刷新组合行（code/name 可能变）+ 重拼 view_sql
                groupRow.setFieldKey(group.getGroupCode());
                groupRow.setFieldName(group.getGroupName());
                groupRow.setBasicKey(group.getGroupCode());
                groupRow.setBasicName(group.getGroupName());
                tableFieldMappingMapper.updateById(groupRow);
                autoVirtualTableBinder.refreshAutoTable(boundTableId, tenantId);
                log.info("指标组合绑定已更新（维度交集匹配） | groupId={}, tableId={}", group.getId(), boundTableId);
                return;
            }
            // 维度集合变了：解绑后重新走复用/新建
            tableFieldMappingMapper.deleteById(groupRow.getId());
            autoVirtualTableBinder.refreshAutoTable(boundTableId, tenantId);
            boundTableId = null;
        }

        // 复用查找
        Long reusableTableId = autoVirtualTableBinder.findReusableTable(commonDims, ctx.getCommonSourceId(), tenantId);
        if (reusableTableId != null) {
            tableFieldMappingMapper.insert(buildGroupIndexMapping(reusableTableId, group, tenantId));
            autoVirtualTableBinder.refreshAutoTable(reusableTableId, tenantId);
            log.info("指标组合复用共享虚拟表 | groupId={}, tableId={}", group.getId(), reusableTableId);
            return;
        }

        // 新建
        OlapTablePro sample = ctx.getSampleTable();
        OlapTablePro virtualTable = new OlapTablePro();
        virtualTable.setSourceId(ctx.getCommonSourceId());
        virtualTable.setDbName(sample != null ? sample.getDbName() : null);
        virtualTable.setTbName(autoVirtualTableBinder.genUniqueViewName(group.getGroupCode(), tenantId));
        virtualTable.setCnName(group.getGroupName() + "自动视图");
        virtualTable.setTbCnName(virtualTable.getCnName());
        virtualTable.setType(sample != null ? sample.getType() : "fact");
        virtualTable.setTypeKey(sample != null ? sample.getTypeKey() : "fact");
        virtualTable.setModelType(0);
        virtualTable.setTbType("1");
        virtualTable.setTbTypeKey("1");
        virtualTable.setStatus(3);
        virtualTable.setTenantId(tenantId);
        tableProMapper.insert(virtualTable);
        Long newTableId = virtualTable.getId();

        for (Long dimId : ctx.getOrderedDims()) {
            tableFieldMappingMapper.insert(autoVirtualTableBinder.buildDimMapping(newTableId,
                    ctx.getDimBasicMap().get(dimId),
                    autoVirtualTableBinder.findSourceRow(ctx.getTableRows(), dimId), tenantId));
        }
        tableFieldMappingMapper.insert(buildGroupIndexMapping(newTableId, group, tenantId));
        autoVirtualTableBinder.refreshAutoTable(newTableId, tenantId);
        log.info("指标组合绑定虚拟表成功 | groupId={}, tableId={}, tbName={}, dims={}",
                group.getId(), newTableId, virtualTable.getTbName(), ctx.getOrderedDims());
    }

    /**
     * 解绑组合：删组合自身的映射行 + refresh（表空删表）。
     */
    private void unbindGroup(OlapReportGroup group, Long tenantId) {
        OlapTableFieldMapping groupRow = findGroupRow(group.getId(), tenantId);
        if (groupRow == null) {
            return;
        }
        Long tableId = groupRow.getTableId();
        tableFieldMappingMapper.deleteById(groupRow.getId());
        if (tableId != null) {
            autoVirtualTableBinder.refreshAutoTable(tableId, tenantId);
        }
        log.info("指标组合绑定已解绑 | groupId={}, tableId={}", group.getId(), tableId);
    }

    /**
     * 组合自身的映射行：basic_id=组合id，basic_key=groupCode，basic_name=groupName。
     */
    private OlapTableFieldMapping buildGroupIndexMapping(Long tableId, OlapReportGroup group, Long tenantId) {
        OlapTableFieldMapping m = new OlapTableFieldMapping();
        m.setTableId(tableId);
        m.setFieldKey(group.getGroupCode());
        m.setFieldName(group.getGroupName());
        m.setBasicId(group.getId());
        m.setBasicType(FieldRegisterType.INDEX.getLabel());
        m.setBasicTypeKey(FieldRegisterType.INDEX.getKey());
        m.setBasicKey(group.getGroupCode());
        m.setBasicName(group.getGroupName());
        m.setStatus(1);
        m.setRegisterStatus("已注册");
        m.setTenantId(tenantId);
        return m;
    }

    private OlapTableFieldMapping findGroupRow(Long groupId, Long tenantId) {
        return tableFieldMappingMapper.selectList(
                        new LambdaQueryWrapper<OlapTableFieldMapping>()
                                .eq(OlapTableFieldMapping::getBasicId, groupId)
                                .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.INDEX.getKey())
                                .eq(OlapTableFieldMapping::getStatus, 1)
                                .eq(OlapTableFieldMapping::getTenantId, tenantId))
                .stream().findFirst().orElse(null);
    }

    /**
     * 因子原子指标集合：组内原子指标本身 + 组内计算/派生指标引用的原子指标。
     */
    private Set<Long> resolveFactorAtomicMetricIds(List<Long> indicatorIds, Long tenantId) {
        Set<Long> factorIds = new LinkedHashSet<>();
        Map<Long, OlapBasicProIndicator> indMap = indicatorMapper.selectList(
                        new LambdaQueryWrapper<OlapBasicProIndicator>()
                                .in(OlapBasicProIndicator::getOlapBasicProId, indicatorIds)
                                .eq(OlapBasicProIndicator::getTenantId, tenantId))
                .stream().collect(Collectors.toMap(OlapBasicProIndicator::getOlapBasicProId, i -> i));
        for (Long id : indicatorIds) {
            OlapBasicProIndicator ind = indMap.get(id);
            if (ind == null) {
                continue;
            }
            if (StrUtil.isNotBlank(ind.getCalculatedProduction())) {
                factorIds.addAll(MetricFormulaParser.extractCalcReferencedIds(ind.getCalculatedProduction()));
            } else if (StrUtil.isNotBlank(ind.getDerivativeProduction())) {
                Long refId = MetricFormulaParser.extractDeriveReferencedId(ind.getDerivativeProduction());
                if (refId != null) {
                    factorIds.add(refId);
                }
            } else {
                factorIds.add(id);
            }
        }
        return factorIds;
    }

    private List<Long> parseIndicatorIds(String groupConfig) {
        if (StrUtil.isBlank(groupConfig)) {
            return Collections.emptyList();
        }
        try {
            JSONObject json = JSONObject.parseObject(groupConfig);
            JSONArray indicators = json.getJSONArray("indicators");
            List<Long> ids = new ArrayList<>();
            if (indicators != null) {
                for (int i = 0; i < indicators.size(); i++) {
                    Long id = indicators.getJSONObject(i).getLong("id");
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("解析组合 groupConfig 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 私有辅助 ====================

    private OlapReportGroup requireGroup(Long id, Long tenantId) {
        OlapReportGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException(404, "指标组合不存在");
        }
        if (!tenantId.equals(group.getTenantId())) {
            throw new BizException(403, "无权限操作该组合");
        }
        return group;
    }

    private void validateCodeUnique(String code, Long tenantId, Long excludeId) {
        LambdaQueryWrapper<OlapReportGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapReportGroup::getGroupCode, code)
                .eq(OlapReportGroup::getTenantId, tenantId);
        if (excludeId != null) {
            wrapper.ne(OlapReportGroup::getId, excludeId);
        }
        if (groupMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "组合编码已存在: " + code);
        }
    }

    private String resolveStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case 0:
                return "草稿";
            case 1:
                return "审批中";
            case 2:
                return "已上线";
            case 3:
                return "已下线";
            default:
                return "";
        }
    }

    private MetricGroupVO toVO(OlapReportGroup group) {
        MetricGroupVO vo = new MetricGroupVO();
        vo.setId(group.getId());
        vo.setGroupCode(group.getGroupCode());
        vo.setGroupName(group.getGroupName());
        vo.setDescription(group.getDescription());
        vo.setGroupConfig(group.getGroupConfig());
        vo.setStatus(group.getStatus());
        vo.setStatusName(resolveStatusName(group.getStatus()));
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }
}
