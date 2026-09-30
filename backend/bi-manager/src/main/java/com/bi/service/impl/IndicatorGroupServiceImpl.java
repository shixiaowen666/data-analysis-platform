package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.GroupItemSaveDTO;
import com.bi.dto.IndicatorGroupQueryDTO;
import com.bi.dto.IndicatorGroupSaveDTO;
import com.bi.entity.OlapBasicPro;
import com.bi.entity.OlapBasicProDimension;
import com.bi.entity.OlapBasicProIndicator;
import com.bi.entity.OlapGroupItem;
import com.bi.entity.OlapIndicatorGroup;
import com.bi.enums.DimensionType;
import com.bi.enums.GroupItemType;
import com.bi.enums.GroupSaveAction;
import com.bi.enums.MetricStatus;
import com.bi.enums.MetricType;
import com.bi.mapper.OlapBasicProDimensionMapper;
import com.bi.mapper.OlapBasicProMapper;
import com.bi.mapper.OlapGroupItemMapper;
import com.bi.mapper.OlapIndicatorGroupMapper;
import com.bi.service.IIndicatorGroupService;
import com.bi.service.IOlapBasicProIndicatorService;
import com.bi.util.CandidateStructureSupport;
import com.bi.vo.*;
import com.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndicatorGroupServiceImpl implements IIndicatorGroupService {

    private static final int CATEGORY_DIMENSION = 1;
    private static final int CATEGORY_METRIC = 2;
    private static final Long DEFAULT_OPERATOR_ID = 0L;

    private final OlapIndicatorGroupMapper groupMapper;
    private final OlapGroupItemMapper itemMapper;
    private final OlapBasicProMapper basicProMapper;
    private final OlapBasicProDimensionMapper dimensionMapper;
    private final IOlapBasicProIndicatorService indicatorService;

    @Override
    public ApiPageResult<IndicatorGroupVO> listGroups(IndicatorGroupQueryDTO query, Long tenantId) {
        LambdaQueryWrapper<OlapIndicatorGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapIndicatorGroup::getTenantId, tenantId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(OlapIndicatorGroup::getGroupName, query.getKeyword())
                    .or().like(OlapIndicatorGroup::getGroupCode, query.getKeyword()));
        }
        wrapper.eq(query.getStatus() != null, OlapIndicatorGroup::getStatus, query.getStatus())
                .orderByDesc(OlapIndicatorGroup::getUpdatedAt);

        Page<OlapIndicatorGroup> page = groupMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        Map<Long, Long> fieldCountMap = countItemsByGroupIds(
                page.getRecords().stream().map(OlapIndicatorGroup::getId).collect(Collectors.toList()));

        List<IndicatorGroupVO> list = page.getRecords().stream()
                .map(g -> toListVO(g, fieldCountMap))
                .collect(Collectors.toList());

        return new ApiPageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public IndicatorGroupDetailVO getDetail(Long id, Long tenantId) {
        OlapIndicatorGroup group = requireGroup(id, tenantId);
        List<OlapGroupItem> items = listItems(id);
        IndicatorGroupDetailVO vo = toDetailVO(group, items);
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupSaveResultVO saveGroup(IndicatorGroupSaveDTO dto, Long tenantId) {
        if (dto.getId() == null) {
            return createGroup(dto, tenantId);
        }
        return updateGroup(dto.getId(), dto, tenantId);
    }

    private GroupSaveResultVO createGroup(IndicatorGroupSaveDTO dto, Long tenantId) {
        validateGroupCodeUnique(dto.getGroupCode(), tenantId, null);
        normalizeItems(dto.getItems(), tenantId);
        validateItems(dto.getItems(), tenantId, null, GroupSaveAction.PUBLISH);

        OlapIndicatorGroup group = new OlapIndicatorGroup();
        group.setGroupCode(dto.getGroupCode().trim());
        group.setGroupName(dto.getGroupName().trim());
        group.setSubjectDomain(dto.getSubjectDomain());
        group.setDescription(dto.getDescription());
        group.setStatus(MetricStatus.ONLINE.getCode());
        group.setVersion(1);
        group.setTenantId(tenantId);
        fillOperatorOnInsert(group);
        groupMapper.insert(group);

        saveItems(group.getId(), dto.getItems(), tenantId);
        return new GroupSaveResultVO(group.getId(), group.getStatus());
    }

    private GroupSaveResultVO updateGroup(Long id, IndicatorGroupSaveDTO dto, Long tenantId) {
        OlapIndicatorGroup group = requireGroup(id, tenantId);
        validateGroupCodeUnique(dto.getGroupCode(), tenantId, id);
        normalizeItems(dto.getItems(), tenantId);
        validateItems(dto.getItems(), tenantId, id, GroupSaveAction.PUBLISH);

        group.setGroupCode(dto.getGroupCode().trim());
        group.setGroupName(dto.getGroupName().trim());
        group.setSubjectDomain(dto.getSubjectDomain());
        group.setDescription(dto.getDescription());
        group.setStatus(MetricStatus.ONLINE.getCode());
        if (group.getVersion() == null) {
            group.setVersion(1);
        } else {
            group.setVersion(group.getVersion() + 1);
        }
        fillOperatorOnUpdate(group);
        groupMapper.updateById(group);

        itemMapper.delete(new LambdaQueryWrapper<OlapGroupItem>().eq(OlapGroupItem::getGroupId, id));
        saveItems(id, dto.getItems(), tenantId);
        return new GroupSaveResultVO(group.getId(), group.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupSaveResultVO onlineGroup(Long id, Long tenantId) {
        OlapIndicatorGroup group = requireGroup(id, tenantId);
        if (group.getStatus() != null && group.getStatus() == MetricStatus.ONLINE.getCode()) {
            return new GroupSaveResultVO(group.getId(), group.getStatus());
        }
        /*if (group.getStatus() == null || group.getStatus() != MetricStatus.APPROVING.getCode()) {
            throw new BizException(400, "仅审批中状态可上线");
        }*/
        List<OlapGroupItem> items = listItems(id);
        if (items.isEmpty()) {
            throw new BizException(400, "组合未配置字段，无法上线");
        }
        validateExistingItems(items, tenantId, id);

        group.setStatus(MetricStatus.ONLINE.getCode());
        fillOperatorOnUpdate(group);
        groupMapper.updateById(group);
        return new GroupSaveResultVO(group.getId(), group.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineGroup(Long id, Long tenantId) {
        OlapIndicatorGroup group = requireGroup(id, tenantId);
        if (group.getStatus() != null && group.getStatus() == MetricStatus.OFFLINE.getCode()) {
            return;
        }
        List<String> refNames = findNestedReferenceNames(id, tenantId);
        if (!refNames.isEmpty()) {
            throw new BizException(403, "组合正在被其他组合嵌套引用，无法下线: " + String.join("、", refNames));
        }
        group.setStatus(MetricStatus.OFFLINE.getCode());
        groupMapper.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id, Long tenantId) {
        OlapIndicatorGroup group = requireGroup(id, tenantId);
        if (group.getStatus() != null && group.getStatus() == MetricStatus.ONLINE.getCode()) {
            throw new BizException(403, "已上线状态不可删除，请先将组合下线");
        }
        List<String> refNames = findNestedReferenceNames(id, tenantId);
        if (!refNames.isEmpty()) {
            throw new BizException(403, "组合正在被其他组合嵌套引用，无法删除: " + String.join("、", refNames));
        }
        itemMapper.delete(new LambdaQueryWrapper<OlapGroupItem>().eq(OlapGroupItem::getGroupId, id));
        groupMapper.deleteById(id);
    }

    @Override
    public GroupCandidatesVO listCandidates(String keyword, String candidateType, Long excludeGroupId, Long tenantId) {
        keyword = StrUtil.trimToNull(keyword);
        candidateType = StrUtil.trimToNull(candidateType);

        GroupCandidatesVO vo = new GroupCandidatesVO();
        if (candidateType == null || "dimension".equals(candidateType)) {
            vo.setDimensions(listDimensionCandidates(keyword, tenantId));
        } else {
            vo.setDimensions(Collections.emptyList());
        }
        if (candidateType == null || "metric".equals(candidateType)) {
            vo.setMetrics(listMetricCandidates(keyword, tenantId));
        } else {
            vo.setMetrics(Collections.emptyList());
        }
        if (candidateType == null || "group".equals(candidateType)) {
            vo.setGroups(listGroupCandidates(keyword, excludeGroupId, tenantId));
        } else {
            vo.setGroups(Collections.emptyList());
        }
        return vo;
    }

    private void normalizeItems(List<GroupItemSaveDTO> items, Long tenantId) {
        if (items == null) {
            return;
        }
        for (GroupItemSaveDTO item : items) {
            normalizeItemType(item, tenantId);
        }
    }

    private void normalizeItemType(GroupItemSaveDTO item, Long tenantId) {
        if (item.getObjectId() == null || StrUtil.isBlank(item.getItemType())) {
            return;
        }
        String code = item.getItemType().trim();
        if ("metric".equals(code) || isMetricItemType(code)) {
            item.setItemType(resolveMetricItemType(item.getObjectId(), tenantId).getCode());
        }
    }

    private boolean isMetricItemType(String code) {
        return GroupItemType.ATOMIC_METRIC.getCode().equals(code)
                || GroupItemType.CALCULATED_METRIC.getCode().equals(code)
                || GroupItemType.DERIVED_METRIC.getCode().equals(code);
    }

    private GroupItemType resolveMetricItemType(Long objectId, Long tenantId) {
        requireBasicPro(objectId, tenantId, CATEGORY_METRIC);
        OlapBasicProIndicator indicator = indicatorService.getOne(
                new LambdaQueryWrapper<OlapBasicProIndicator>()
                        .eq(OlapBasicProIndicator::getOlapBasicProId, objectId));
        if (indicator == null) {
            throw new BizException(404, "指标扩展信息不存在: " + objectId);
        }
        return toGroupItemType(parseMetricType(indicator));
    }

    private void validateGroupCodeUnique(String groupCode, Long tenantId, Long excludeId) {
        LambdaQueryWrapper<OlapIndicatorGroup> wrapper = new LambdaQueryWrapper<OlapIndicatorGroup>()
                .eq(OlapIndicatorGroup::getTenantId, tenantId)
                .eq(OlapIndicatorGroup::getGroupCode, groupCode.trim());
        if (excludeId != null) {
            wrapper.ne(OlapIndicatorGroup::getId, excludeId);
        }
        if (groupMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "组合编码已存在");
        }
    }

    private void validateItems(List<GroupItemSaveDTO> items, Long tenantId, Long currentGroupId, GroupSaveAction action) {
        Set<String> objectKeys = new HashSet<>();
        for (GroupItemSaveDTO item : items) {
            GroupItemType type = GroupItemType.fromCode(item.getItemType());
            String key = type.getCode() + ":" + item.getObjectId();
            if (!objectKeys.add(key)) {
                throw new BizException(400, "组合项不能重复: " + type.getDesc());
            }
            if (type == GroupItemType.INDICATOR_GROUP && Objects.equals(item.getObjectId(), currentGroupId)) {
                throw new BizException(400, "不能嵌套引用自身组合");
            }
            resolveObjectName(type, item.getObjectId(), tenantId, action);
        }
    }

    private void validateExistingItems(List<OlapGroupItem> items, Long tenantId, Long groupId) {
        Set<String> objectKeys = new HashSet<>();
        for (OlapGroupItem item : items) {
            GroupItemType type = GroupItemType.fromCode(item.getItemType());
            String key = type.getCode() + ":" + item.getObjectId();
            if (!objectKeys.add(key)) {
                throw new BizException(400, "组合项不能重复: " + type.getDesc());
            }
            if (type == GroupItemType.INDICATOR_GROUP && Objects.equals(item.getObjectId(), groupId)) {
                throw new BizException(400, "不能嵌套引用自身组合");
            }
            resolveObjectName(type, item.getObjectId(), tenantId, GroupSaveAction.PUBLISH);
        }
    }

    private String resolveObjectName(GroupItemType type, Long objectId, Long tenantId, GroupSaveAction action) {
        switch (type) {
            case DIMENSION:
                OlapBasicPro dim = requireBasicPro(objectId, tenantId, CATEGORY_DIMENSION);
                requireOnline(dim, action);
                OlapBasicProDimension dimExt = dimensionMapper.selectOne(
                        new LambdaQueryWrapper<OlapBasicProDimension>()
                                .eq(OlapBasicProDimension::getOlapBasicProId, objectId));
                if (dimExt == null) {
                    throw new BizException(404, "维度扩展信息不存在: " + objectId);
                }
                return dim.getChineseName();
            case ATOMIC_METRIC:
            case CALCULATED_METRIC:
            case DERIVED_METRIC:
                OlapBasicPro metric = requireBasicPro(objectId, tenantId, CATEGORY_METRIC);
                requireOnline(metric, action);
                OlapBasicProIndicator indicator = indicatorService.getOne(
                        new LambdaQueryWrapper<OlapBasicProIndicator>()
                                .eq(OlapBasicProIndicator::getOlapBasicProId, objectId));
                if (indicator == null) {
                    throw new BizException(404, "指标扩展信息不存在: " + objectId);
                }
                return metric.getChineseName();
            case INDICATOR_GROUP:
                OlapIndicatorGroup nested = requireGroup(objectId, tenantId);
                if (nested.getStatus() == null || nested.getStatus() != MetricStatus.ONLINE.getCode()) {
                    throw new BizException(400, "仅可引用已上线的指标组合: " + nested.getGroupName());
                }
                return nested.getGroupName();
            default:
                throw new BizException(400, "不支持的组合项类型");
        }
    }

    private GroupItemType toGroupItemType(MetricType metricType) {
        switch (metricType) {
            case CALC:
                return GroupItemType.CALCULATED_METRIC;
            case DERIVE:
                return GroupItemType.DERIVED_METRIC;
            default:
                return GroupItemType.ATOMIC_METRIC;
        }
    }

    private MetricType parseMetricType(OlapBasicProIndicator indicator) {
        if (StrUtil.isNotBlank(indicator.getCalculatedProduction())) {
            return MetricType.CALC;
        }
        if (StrUtil.isNotBlank(indicator.getDerivativeProduction())
                || StrUtil.isNotBlank(indicator.getDecoratedWord())
                || StrUtil.isNotBlank(indicator.getTimePeriodName())) {
            return MetricType.DERIVE;
        }
        return MetricType.ATOM;
    }

    private void requireOnline(OlapBasicPro basic, GroupSaveAction action) {
        if (action == GroupSaveAction.PUBLISH
                && (basic.getStatus() == null || basic.getStatus() != MetricStatus.ONLINE.getCode())) {
            throw new BizException(400, "提交审批时引用的维度/指标必须已上线: " + basic.getChineseName());
        }
    }

    private OlapBasicPro requireBasicPro(Long id, Long tenantId, int category) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null || !Objects.equals(basic.getTenantId(), tenantId)) {
            throw new BizException(404, "对象不存在: " + id);
        }
        if (!Objects.equals(basic.getCategory(), category)) {
            throw new BizException(400, "对象类型不匹配: " + id);
        }
        return basic;
    }

    private OlapIndicatorGroup requireGroup(Long id, Long tenantId) {
        OlapIndicatorGroup group = groupMapper.selectById(id);
        if (group == null || !Objects.equals(group.getTenantId(), tenantId)) {
            throw new BizException(404, "指标组合不存在");
        }
        return group;
    }

    private void saveItems(Long groupId, List<GroupItemSaveDTO> items, Long tenantId) {
        for (GroupItemSaveDTO dto : items) {
            OlapGroupItem item = new OlapGroupItem();
            item.setGroupId(groupId);
            item.setItemType(dto.getItemType());
            item.setObjectId(dto.getObjectId());
            item.setDisplayName(dto.getDisplayName());
            item.setDisplayOrder(dto.getDisplayOrder());
            item.setIsRequired(dto.getIsRequired() != null ? dto.getIsRequired() : 0);
            item.setIsDefaultVisible(dto.getIsDefaultVisible() != null ? dto.getIsDefaultVisible() : 1);
            item.setFormatType(dto.getFormatType());
            item.setUnit(dto.getUnit());
            item.setDefaultSort(dto.getDefaultSort());
            item.setTenantId(tenantId);
            fillOperatorOnInsert(item);
            itemMapper.insert(item);
        }
    }

    private void fillOperatorOnInsert(OlapIndicatorGroup entity) {
        if (entity.getCreatedBy() == null) {
            entity.setCreatedBy(DEFAULT_OPERATOR_ID);
        }
        if (entity.getUpdatedBy() == null) {
            entity.setUpdatedBy(DEFAULT_OPERATOR_ID);
        }
    }

    private void fillOperatorOnUpdate(OlapIndicatorGroup entity) {
        entity.setUpdatedBy(DEFAULT_OPERATOR_ID);
    }

    private void fillOperatorOnInsert(OlapGroupItem entity) {
        if (entity.getCreatedBy() == null) {
            entity.setCreatedBy(DEFAULT_OPERATOR_ID);
        }
        if (entity.getUpdatedBy() == null) {
            entity.setUpdatedBy(DEFAULT_OPERATOR_ID);
        }
    }

    private List<OlapGroupItem> listItems(Long groupId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OlapGroupItem>()
                .eq(OlapGroupItem::getGroupId, groupId)
                .orderByAsc(OlapGroupItem::getDisplayOrder)
                .orderByAsc(OlapGroupItem::getId));
    }

    private Map<Long, Long> countItemsByGroupIds(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OlapGroupItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OlapGroupItem>().in(OlapGroupItem::getGroupId, groupIds));
        return items.stream().collect(Collectors.groupingBy(OlapGroupItem::getGroupId, Collectors.counting()));
    }

    private List<String> findNestedReferenceNames(Long groupId, Long tenantId) {
        List<OlapGroupItem> refs = itemMapper.selectList(new LambdaQueryWrapper<OlapGroupItem>()
                .eq(OlapGroupItem::getItemType, GroupItemType.INDICATOR_GROUP.getCode())
                .eq(OlapGroupItem::getObjectId, groupId)
                .eq(OlapGroupItem::getTenantId, tenantId));
        if (refs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> parentIds = refs.stream().map(OlapGroupItem::getGroupId).collect(Collectors.toSet());
        return groupMapper.selectBatchIds(parentIds).stream()
                .map(OlapIndicatorGroup::getGroupName)
                .collect(Collectors.toList());
    }

    private List<GroupCandidateVO> listDimensionCandidates(String keyword, Long tenantId) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<OlapBasicPro>()
                .eq(OlapBasicPro::getTenantId, tenantId)
                .eq(OlapBasicPro::getCategory, CATEGORY_DIMENSION)
                .eq(OlapBasicPro::getStatus, MetricStatus.ONLINE.getCode());
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(OlapBasicPro::getChineseName, keyword);
        }
        wrapper.orderByAsc(OlapBasicPro::getChineseName);
        return basicProMapper.selectList(wrapper).stream()
                .map(this::toDimensionCandidate)
                .collect(Collectors.toList());
    }

    private List<GroupCandidateVO> listMetricCandidates(String keyword, Long tenantId) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<OlapBasicPro>()
                .eq(OlapBasicPro::getTenantId, tenantId)
                .eq(OlapBasicPro::getCategory, CATEGORY_METRIC)
                .eq(OlapBasicPro::getStatus, MetricStatus.ONLINE.getCode());
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(OlapBasicPro::getChineseName, keyword);
        }
        wrapper.orderByAsc(OlapBasicPro::getChineseName);
        List<OlapBasicPro> metrics = basicProMapper.selectList(wrapper);
        if (metrics.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, OlapBasicProIndicator> indicatorMap = indicatorService.list(
                        new LambdaQueryWrapper<OlapBasicProIndicator>()
                                .in(OlapBasicProIndicator::getOlapBasicProId,
                                        metrics.stream().map(OlapBasicPro::getId).collect(Collectors.toList())))
                .stream()
                .collect(Collectors.toMap(OlapBasicProIndicator::getOlapBasicProId, i -> i, (a, b) -> a));

        return metrics.stream().map(m -> {
            GroupCandidateVO vo = new GroupCandidateVO();
            vo.setId(m.getId());
            vo.setName(m.getChineseName());
            OlapBasicProIndicator indicator = indicatorMap.get(m.getId());
            GroupItemType itemType = GroupItemType.ATOMIC_METRIC;
            if (indicator != null) {
                MetricType type = parseMetricType(indicator);
                itemType = toGroupItemType(type);
                if (type == MetricType.CALC) {
                    vo.setTypeLabel("计算");
                } else if (type == MetricType.DERIVE) {
                    vo.setTypeLabel("派生");
                } else {
                    vo.setTypeLabel("原子");
                }
            } else {
                vo.setTypeLabel("原子");
            }
            vo.setItemType(itemType.getCode());
            CandidateStructureSupport.applyMetricDefaults(vo, indicator, itemType);
            return vo;
        }).collect(Collectors.toList());
    }

    private List<GroupCandidateVO> listGroupCandidates(String keyword, Long excludeGroupId, Long tenantId) {
        LambdaQueryWrapper<OlapIndicatorGroup> wrapper = new LambdaQueryWrapper<OlapIndicatorGroup>()
                .eq(OlapIndicatorGroup::getTenantId, tenantId)
                .eq(OlapIndicatorGroup::getStatus, MetricStatus.ONLINE.getCode());
        if (excludeGroupId != null) {
            wrapper.ne(OlapIndicatorGroup::getId, excludeGroupId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(OlapIndicatorGroup::getGroupName, keyword);
        }
        wrapper.orderByAsc(OlapIndicatorGroup::getGroupName);
        return groupMapper.selectList(wrapper).stream().map(g -> {
            GroupCandidateVO vo = new GroupCandidateVO();
            vo.setId(g.getId());
            vo.setName(g.getGroupName());
            vo.setTypeLabel("指标组合");
            vo.setItemType(GroupItemType.INDICATOR_GROUP.getCode());
            CandidateStructureSupport.applyGroupDefaults(vo);
            return vo;
        }).collect(Collectors.toList());
    }

    private GroupCandidateVO toDimensionCandidate(OlapBasicPro basic) {
        GroupCandidateVO vo = new GroupCandidateVO();
        vo.setId(basic.getId());
        vo.setName(basic.getChineseName());
        vo.setItemType(GroupItemType.DIMENSION.getCode());
        OlapBasicProDimension dim = dimensionMapper.selectOne(
                new LambdaQueryWrapper<OlapBasicProDimension>()
                        .eq(OlapBasicProDimension::getOlapBasicProId, basic.getId()));
        if (dim == null) {
            vo.setTypeLabel("标准维");
            CandidateStructureSupport.applyDimensionDefaults(vo, null);
            return vo;
        }
        if (dim.getDimensionType() != null && dim.getDimensionType() == DimensionType.MISC.getCode()) {
            vo.setTypeLabel("杂项维");
        } else if (StrUtil.isNotBlank(dim.getTimeDynamic()) || StrUtil.isNotBlank(dim.getPartitionField())) {
            vo.setTypeLabel("标准维·时间");
        } else {
            vo.setTypeLabel("标准维");
        }
        CandidateStructureSupport.applyDimensionDefaults(vo, dim);
        return vo;
    }

    private IndicatorGroupVO toListVO(OlapIndicatorGroup group, Map<Long, Long> fieldCountMap) {
        IndicatorGroupVO vo = new IndicatorGroupVO();
        vo.setId(group.getId());
        vo.setGroupCode(group.getGroupCode());
        vo.setGroupName(group.getGroupName());
        vo.setSubjectDomain(group.getSubjectDomain());
        vo.setStatus(group.getStatus());
        vo.setStatusName(resolveStatusName(group.getStatus()));
        vo.setUpdatedAt(group.getUpdatedAt());
        Long count = fieldCountMap.get(group.getId());
        vo.setFieldCount(count != null ? count.intValue() : 0);
        return vo;
    }

    private IndicatorGroupDetailVO toDetailVO(OlapIndicatorGroup group, List<OlapGroupItem> items) {
        IndicatorGroupDetailVO vo = new IndicatorGroupDetailVO();
        vo.setId(group.getId());
        vo.setGroupCode(group.getGroupCode());
        vo.setGroupName(group.getGroupName());
        vo.setSubjectDomain(group.getSubjectDomain());
        vo.setDescription(group.getDescription());
        vo.setStatus(group.getStatus());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }

    private IndicatorGroupItemVO toItemVO(OlapGroupItem item) {
        IndicatorGroupItemVO vo = new IndicatorGroupItemVO();
        vo.setId(item.getId());
        vo.setItemType(item.getItemType());
        GroupItemType type = GroupItemType.fromCode(item.getItemType());
        vo.setItemTypeName(type.getDesc());
        vo.setObjectId(item.getObjectId());
        vo.setObjectName(resolveItemObjectName(type, item.getObjectId()));
        vo.setDisplayName(StrUtil.isNotBlank(item.getDisplayName()) ? item.getDisplayName() : vo.getObjectName());
        vo.setDisplayOrder(item.getDisplayOrder());
        vo.setIsRequired(item.getIsRequired());
        vo.setIsDefaultVisible(item.getIsDefaultVisible());
        vo.setFormatType(item.getFormatType());
        vo.setUnit(item.getUnit());
        vo.setDefaultSort(item.getDefaultSort());
        return vo;
    }

    private String resolveItemObjectName(GroupItemType type, Long objectId) {
        if (type == GroupItemType.INDICATOR_GROUP) {
            OlapIndicatorGroup g = groupMapper.selectById(objectId);
            return g != null ? g.getGroupName() : null;
        }
        OlapBasicPro basic = basicProMapper.selectById(objectId);
        return basic != null ? basic.getChineseName() : null;
    }

    private String resolveStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        try {
            return MetricStatus.fromCode(status).getDesc();
        } catch (Exception ex) {
            return String.valueOf(status);
        }
    }
}
