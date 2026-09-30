package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.*;
import com.bi.entity.OlapBasicPro;
import com.bi.entity.OlapBasicProIndicator;
import com.bi.entity.OlapReportGroup;
import com.bi.entity.OlapTableFieldMapping;
import com.bi.entity.OlapTablePro;
import com.bi.enums.MetricStatus;
import com.bi.enums.Category;
import com.bi.enums.FieldRegisterType;
import com.bi.enums.MetricType;
import com.bi.mapper.OlapBasicProMapper;
import com.bi.mapper.OlapReportGroupMapper;
import com.bi.mapper.OlapTableFieldMappingMapper;
import com.bi.mapper.OlapTableProMapper;
import com.bi.service.IOlapBasicProIndicatorService;
import com.bi.service.IMetricService;
import com.bi.util.JsonUtil;
import com.bi.util.MetricFormulaParser;
import com.bi.vo.MetricDetailVO;
import com.bi.vo.MetricVO;
import com.bi.vo.NameCheckVO;
import com.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 指标管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements IMetricService {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final OlapBasicProMapper basicProMapper;
    private final IOlapBasicProIndicatorService indicatorService;
    private final OlapTableFieldMappingMapper tableFieldMappingMapper;
    private final OlapReportGroupMapper reportGroupMapper;
    private final OlapTableProMapper tableProMapper;
    private final AutoVirtualTableBinder autoVirtualTableBinder;

    @Override
    public IPage<MetricVO> listMetrics(String keyword, String type, Integer status, Long tenantId, long page, long pageSize) {
        Page<MetricVO> mpPage = new Page<>(page, pageSize);
        return basicProMapper.selectMetricPage(mpPage, keyword, type, status, tenantId);
    }

    @Override
    public MetricDetailVO getMetricDetail(Long id) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "指标不存在");
        }

        MetricDetailVO vo = new MetricDetailVO();
        vo.setId(basic.getId());
        vo.setStandardName(basic.getStandardName());
        vo.setChineseName(basic.getChineseName());
        vo.setAlias(basic.getAlias());
        vo.setEnglishName(basic.getEnglishName());
        vo.setAbbreviation(basic.getAbbreviation());
        vo.setClassificationLabel(basic.getClassificationLabel());
        vo.setDataType(basic.getDataType());
        vo.setCategory(basic.getCategory());
        vo.setStatus(basic.getStatus());

        OlapBasicProIndicator indicator = indicatorService.selectOne(basic.getId());

        if (indicator != null) {
            vo.setCaliberDescription(indicator.getCaliberDescription());
            vo.setUnit(indicator.getUnit());
            vo.setDecimalPlaces(indicator.getDecimalPlaces());
            vo.setThresholdRule(indicator.getThresholdRule());
            vo.setNullHandling(indicator.getNullHandling());
            vo.setAuthorizeStrategy(indicator.getAuthorizeStrategy());

            MetricType metricType = parseMetricType(indicator);
            vo.setType(metricType.getCode());
            List<Map<String,Object>> mappingList = tableFieldMappingMapper.getMappingListByDimId(basic.getId());
            vo.setMappingList(mappingList);
            if (StrUtil.isNotBlank(indicator.getCalculatedProduction())) {
                try {
                    CalculatedProductionDTO calculatedProductionDTO = JSONObject.parseObject(indicator.getCalculatedProduction(), CalculatedProductionDTO.class);
                    vo.setCalculateFormula(calculatedProductionDTO);
                } catch (Exception e) {
                    log.warn("解析计算指标公式失败: {}", e.getMessage());
                }
            }

            if (StrUtil.isNotBlank(indicator.getDerivativeProduction())) {
                try {
                    DerivativeProductionDTO derivativeProductionDTO = JSONObject.parseObject(indicator.getDerivativeProduction(), DerivativeProductionDTO.class);
                    vo.setDerivativeFormula(derivativeProductionDTO);
                } catch (Exception e) {
                    log.warn("解析关联指标失败: {}", e.getMessage());
                }
            }
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMetric(MetricReq req, Long tenantId) {
        // 1. 校验名称唯一性
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getEnglishName, req.getEnglishName())
               .eq(OlapBasicPro::getTenantId, tenantId);
        if (basicProMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "指标名称已存在");
        }

        // 1.1 计算指标：校验引用原子指标同源
        List<Long> calcRefIds = extractCalcRefIds(req);
        validateCalcSameSource(calcRefIds, tenantId);

        // 2. 生成 key_str（编码）
        String code = generateMetricCode();

        // 3. 创建基本信息
        OlapBasicPro basic = new OlapBasicPro();
        basic.setTenantId(tenantId);
        basic.setCategory(Category.METRIC.getCode());
        basic.setKeyStr(code);
        basic.setStandardName(req.getStandardName());
        basic.setChineseName(StrUtil.isNotBlank(req.getChineseName()) ? req.getChineseName() : req.getStandardName());
        basic.setEnglishName(req.getEnglishName());
        basic.setAlias(req.getAlias());
        basic.setAbbreviation(req.getAbbreviation());
        basic.setClassificationLabel(req.getClassificationLabel());
        basic.setClassificationLabelName(req.getClassificationLabelName());
        basic.setOlapLabel(StrUtil.isNotBlank(req.getOlapLabel()) ? req.getOlapLabel() : "olapcommon");
        basic.setOlapLabelName(StrUtil.isNotBlank(req.getOlapLabelName()) ? req.getOlapLabelName() : "通用");
        basic.setDataType(req.getDataType());
        basic.setDataSourceType(1);
        basic.setStatus(MetricStatus.ONLINE.getCode());
        basic.setPrincipal(req.getPrincipalId());
        basic.setPrincipalName(req.getPrincipalName());
        basic.setPrincipalEmail(req.getPrincipalEmail());
        basic.setApprover(req.getApproverId());
        basic.setApproverName(req.getApproverName());
        basic.setApproverEmail(req.getApproverEmail());
        basic.setIsShow("1");
        basicProMapper.insert(basic);

        Long basicId = basic.getId();

        // 4. 创建扩展信息
        OlapBasicProIndicator indicator = buildIndicator(basicId, tenantId, req);
        indicatorService.save(indicator);

        // 5. 计算指标：自动绑定虚拟表
        if (!calcRefIds.isEmpty()) {
            bindCalcMetricToVirtualTable(basic, calcRefIds, tenantId);
        }

        log.info("新建指标成功: id={}, code={}", basicId, code);
        return basicId;
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public OlapBasicPro createMetricOrExit(MetricReq req, Long tenantId) {
        // 1. 校验名称唯一性
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getEnglishName, req.getEnglishName())
                .eq(OlapBasicPro::getTenantId, tenantId);
        List<OlapBasicPro> list = basicProMapper.selectList(wrapper);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }

        // 计算指标：校验引用原子指标同源
        List<Long> calcRefIds = extractCalcRefIds(req);
        validateCalcSameSource(calcRefIds, tenantId);

        // 2. 生成 key_str（编码）
        String code = generateMetricCode();

        // 3. 创建基本信息
        OlapBasicPro basic = new OlapBasicPro();
        basic.setTenantId(tenantId);
        basic.setCategory(Category.METRIC.getCode());
        basic.setKeyStr(code);
        basic.setStandardName(req.getStandardName());
        basic.setChineseName(StrUtil.isNotBlank(req.getChineseName()) ? req.getChineseName() : req.getStandardName());
        basic.setEnglishName(req.getEnglishName());
        basic.setAlias(req.getAlias());
        basic.setAbbreviation(req.getAbbreviation());
        basic.setClassificationLabel(req.getClassificationLabel());
        basic.setClassificationLabelName(req.getClassificationLabelName());
        basic.setOlapLabel(StrUtil.isNotBlank(req.getOlapLabel()) ? req.getOlapLabel() : "olapcommon");
        basic.setOlapLabelName(StrUtil.isNotBlank(req.getOlapLabelName()) ? req.getOlapLabelName() : "通用");
        basic.setDataType(req.getDataType());
        basic.setDataSourceType(1);
        basic.setStatus(MetricStatus.ONLINE.getCode());
        basic.setPrincipal(req.getPrincipalId());
        basic.setPrincipalName(req.getPrincipalName());
        basic.setPrincipalEmail(req.getPrincipalEmail());
        basic.setApprover(req.getApproverId());
        basic.setApproverName(req.getApproverName());
        basic.setApproverEmail(req.getApproverEmail());
        basic.setIsShow("1");
        basicProMapper.insert(basic);

        Long basicId = basic.getId();

        // 4. 创建扩展信息
        OlapBasicProIndicator indicator = buildIndicator(basicId, tenantId, req);
        indicatorService.save(indicator);

        // 5. 计算指标：自动绑定虚拟表
        if (!calcRefIds.isEmpty()) {
            bindCalcMetricToVirtualTable(basic, calcRefIds, tenantId);
        }

        log.info("新建指标成功: id={}, code={}", basicId, code);
        return basicProMapper.selectById(basicId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMetric(Long id, MetricReq req, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "指标不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该指标");
        }

        // 校验名称唯一性（排除自身）
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getKeyStr, req.getStandardName())
               .eq(OlapBasicPro::getTenantId, tenantId)
               .ne(OlapBasicPro::getId, id);
        if (basicProMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "指标名称已存在");
        }

        // 更新基本信息
        basic.setStandardName(req.getStandardName());
        basic.setChineseName(StrUtil.isNotBlank(req.getChineseName()) ? req.getChineseName() : req.getStandardName());
        basic.setEnglishName(req.getEnglishName());
        basic.setAlias(req.getAlias());
        basic.setAbbreviation(req.getAbbreviation());
        basic.setClassificationLabel(req.getClassificationLabel());
        basic.setClassificationLabelName(req.getClassificationLabelName());
        basic.setOlapLabel(StrUtil.isNotBlank(req.getOlapLabel()) ? req.getOlapLabel() : "olapcommon");
        basic.setOlapLabelName(StrUtil.isNotBlank(req.getOlapLabelName()) ? req.getOlapLabelName() : "通用");
        basic.setDataType(req.getDataType());
        basic.setPrincipal(req.getPrincipalId());
        basic.setPrincipalName(req.getPrincipalName());
        basic.setPrincipalEmail(req.getPrincipalEmail());
        basic.setApprover(req.getApproverId());
        basic.setApproverName(req.getApproverName());
        basic.setApproverEmail(req.getApproverEmail());
        basicProMapper.updateById(basic);

        // 更新扩展信息
        OlapBasicProIndicator indicator = indicatorService.getOne(
                new LambdaQueryWrapper<OlapBasicProIndicator>()
                        .eq(OlapBasicProIndicator::getOlapBasicProId, id)
        );
        if (indicator == null) {
            indicator = new OlapBasicProIndicator();
            indicator.setOlapBasicProId(id);
            indicator.setTenantId(tenantId);
        }
        List<Long> oldCalcRefIds = MetricFormulaParser.extractCalcReferencedIds(indicator.getCalculatedProduction());

        OlapBasicProIndicator updated = buildIndicator(id, tenantId, req);
        indicator.setBusinessTheme(updated.getBusinessTheme());
        indicator.setBusinessThemeName(updated.getBusinessThemeName());
        indicator.setBusinessLine(updated.getBusinessLine());
        indicator.setBusinessLineName(updated.getBusinessLineName());
        indicator.setBusinessProcessId(updated.getBusinessProcessId());
        indicator.setBusinessRoot(updated.getBusinessRoot());
        indicator.setBusinessRootName(updated.getBusinessRootName());
        indicator.setCaliberDescription(updated.getCaliberDescription());
        indicator.setUnit(updated.getUnit());
        indicator.setDecimalPlaces(updated.getDecimalPlaces());
        indicator.setThresholdRule(updated.getThresholdRule());
        indicator.setNullHandling(updated.getNullHandling());
        indicator.setAuthorizeStrategy(updated.getAuthorizeStrategy());
        indicator.setCalculatedProduction(updated.getCalculatedProduction());
        indicator.setDecoratedWord(updated.getDecoratedWord());
        indicator.setTimePeriodName(updated.getTimePeriodName());
        indicator.setRelationIndicators(updated.getRelationIndicators());

        indicatorService.saveOrUpdate(indicator);

        // 计算指标虚拟表绑定联动：引用变化才触发；公式清空则删旧绑定
        List<Long> newCalcRefIds = MetricFormulaParser.extractCalcReferencedIds(indicator.getCalculatedProduction());
        if (newCalcRefIds.isEmpty()) {
            if (!oldCalcRefIds.isEmpty()) {
                deleteCalcMetricBinding(id, tenantId);
            }
        } else {
            validateCalcSameSource(newCalcRefIds, tenantId);
            if (!new HashSet<>(oldCalcRefIds).equals(new HashSet<>(newCalcRefIds))) {
                bindCalcMetricToVirtualTable(basic, newCalcRefIds, tenantId);
            }
        }

        log.info("更新指标成功: id={}", id);
    }

    /**
     * 根据请求构建扩展信息实体（只设置类型相关字段）
     */
    private OlapBasicProIndicator buildIndicator(Long basicId, Long tenantId, MetricReq req) {
        OlapBasicProIndicator indicator = new OlapBasicProIndicator();
        indicator.setOlapBasicProId(basicId);
        indicator.setTenantId(tenantId);
        indicator.setBusinessThemeName(req.getBusinessThemeName());
        indicator.setBusinessLineName(req.getBusinessLineName());
        indicator.setBusinessProcessId(req.getBusinessProcessId());
        indicator.setBusinessRootName(req.getBusinessRootName());
        indicator.setCaliberDescription(req.getCaliberDescription());
        indicator.setUnit(req.getUnit());
        indicator.setDecimalPlaces(req.getDecimalPlaces() != null ? req.getDecimalPlaces() : 2);
        indicator.setThresholdRule(req.getThresholdRule());
        indicator.setNullHandling(req.getNullHandling());
        indicator.setAuthorizeStrategy(req.getAuthorizeStrategy() != null ? req.getAuthorizeStrategy() : 2);

        MetricType metricType = MetricType.fromCode(req.getType());
        switch (metricType) {
            case ATOM:
                indicator.setDecoratedWord(null);
                indicator.setCalculatedProduction(null);
                indicator.setTimePeriodName(null);
                indicator.setRelationIndicators(null);
                break;
            case CALC:
                CalculatedProductionDTO calculateFormula = req.getCalculateFormula();
                if(Objects.nonNull(calculateFormula)){
                    indicator.setCalculatedProduction(JSONObject.toJSONString(calculateFormula));
                }
                break;
            case DERIVE:
                DerivativeProductionDTO derivativeFormula = req.getDerivativeFormula();
                if(Objects.nonNull(derivativeFormula)){
                    indicator.setDerivativeProduction(JSONObject.toJSONString(derivativeFormula));
                }
                break;
        }
        return indicator;
    }

    private static Integer parseUnitType(String unit) {
        if (StrUtil.isBlank(unit)) {
            return 0;
        }
        String value = unit.trim();
        if ("1".equals(value) || "元".equals(value)) {
            return 1;
        }
        if ("2".equals(value) || "%".equals(value)) {
            return 2;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeUnitStorage(String unit) {
        Integer type = parseUnitType(unit);
        if (type == null || type == 0) {
            return unit != null ? unit.trim() : "0";
        }
        return String.valueOf(type);
    }

    private static String formatUnitDisplay(String unit) {
        if (StrUtil.isBlank(unit)) {
            return null;
        }
        Integer type = parseUnitType(unit);
        if (type != null) {
            return formatUnitDisplay(type);
        }
        return unit.trim();
    }

    private static String formatUnitDisplay(Integer unitType) {
        if (unitType == null) {
            return null;
        }
        switch (unitType) {
            case 1:
                return "元";
            case 2:
                return "%";
            default:
                return null;
        }
    }

    @Override
    public String generateMetricCode() {
        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = SEQ.incrementAndGet();
        return "idx_" + timestamp + String.format("%04d", seq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineMetric(Long id, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "指标不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该指标");
        }
        if (basic.getStatus() != MetricStatus.ONLINE.getCode()) {
            throw new BizException(403, "仅已上线状态可下线");
        }

        List<MetricVO> referencedBy = checkReferencedByCalculation(Collections.singletonList(id));
        if (!referencedBy.isEmpty()) {
            throw new BizException(403, "指标被以下计算指标引用，无法下线: " +
                    referencedBy.stream().map(MetricVO::getChineseName).collect(Collectors.joining("、")));
        }

        basic.setStatus(MetricStatus.OFFLINE.getCode());
        basicProMapper.updateById(basic);
        log.info("指标下线成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onlineMetric(Long id, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "指标不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该指标");
        }
        if (basic.getStatus() == MetricStatus.ONLINE.getCode()) {
            throw new BizException(400, "指标已是已上线状态");
        }

        basic.setStatus(MetricStatus.ONLINE.getCode());
        basicProMapper.updateById(basic);
        log.info("指标上线成功: id={}", id);
    }

    @Override
    public List<MetricVO> listAllMetrics(String keyword, Long tenantId) {
        return basicProMapper.selectMetricList(keyword, tenantId);
    }

    @Override
    public List<MetricVO> listAllMetricsWithGroups(String keyword, Long tenantId) {
        List<MetricVO> result = new ArrayList<>(basicProMapper.selectMetricList(keyword, tenantId));
        LambdaQueryWrapper<OlapReportGroup> groupWrapper = new LambdaQueryWrapper<OlapReportGroup>()
                .eq(OlapReportGroup::getTenantId, tenantId)
                .eq(OlapReportGroup::getStatus, MetricStatus.ONLINE.getCode())
                .orderByDesc(OlapReportGroup::getUpdatedAt);
        if (StrUtil.isNotBlank(keyword)) {
            groupWrapper.and(w -> w.like(OlapReportGroup::getGroupName, keyword)
                    .or().like(OlapReportGroup::getGroupCode, keyword));
        }
        for (OlapReportGroup group : reportGroupMapper.selectList(groupWrapper)) {
            MetricVO vo = new MetricVO();
            vo.setId(group.getId());
            vo.setCode(group.getGroupCode());
            vo.setEnglishName(group.getGroupCode());
            vo.setChineseName(group.getGroupName());
            vo.setType("group");
            vo.setStatus(group.getStatus());
            vo.setUpdatedAt(group.getUpdatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetric(Long id, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "指标不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该指标");
        }
        if (basic.getStatus() != null && basic.getStatus() == MetricStatus.ONLINE.getCode()) {
            throw new BizException(403, "已上线状态不可删除，请先将指标下线");
        }
        List<MetricVO> referencedBy = checkReferencedByCalculation(List.of(id));
        if (!referencedBy.isEmpty()) {
            throw new BizException(403, "指标被以下计算指标引用，无法删除: " +
                    referencedBy.stream().map(MetricVO::getChineseName).collect(Collectors.joining("、")));
        }

        deleteCalcMetricBinding(id, tenantId);

        indicatorService.remove(
                new LambdaQueryWrapper<OlapBasicProIndicator>()
                        .eq(OlapBasicProIndicator::getOlapBasicProId, id)
        );
        basicProMapper.deleteById(id);
        log.info("指标删除成功: id={}", id);
    }

    @Override
    public List<MetricVO> checkReferencedByCalculation(List<Long> metricIds) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getCategory, 2)
               .eq(OlapBasicPro::getStatus, MetricStatus.ONLINE.getCode());
        List<OlapBasicPro> onlineMetrics = basicProMapper.selectList(wrapper);

        List<MetricVO> result = new ArrayList<>();
        for (OlapBasicPro metric : onlineMetrics) {
            OlapBasicProIndicator indicator = indicatorService.getOne(
                    new LambdaQueryWrapper<OlapBasicProIndicator>()
                            .eq(OlapBasicProIndicator::getOlapBasicProId, metric.getId())
            );
            if (indicator != null && StrUtil.isNotBlank(indicator.getCalculatedProduction())) {
                try {
                    MetricFormula formula = JsonUtil.fromJson(indicator.getCalculatedProduction(), MetricFormula.class);
                    if (formula != null && formula.getMapping() != null) {
                        for (MetricMappingItem item : formula.getMapping()) {
                            if (metricIds.contains(item.getMetricId())) {
                                MetricVO vo = new MetricVO();
                                vo.setId(metric.getId());
                                vo.setCode(metric.getKeyStr());
                                vo.setChineseName(metric.getChineseName());
                                result.add(vo);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析计算指标公式失败: id={}, error={}", metric.getId(), e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public NameCheckVO checkEnglishName(String name, Long excludeId, Long tenantId) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getEnglishName, name)
               .eq(OlapBasicPro::getTenantId, tenantId);
        if (excludeId != null) {
            wrapper.ne(OlapBasicPro::getId, excludeId);
        }
        if (basicProMapper.selectCount(wrapper) > 0) {
            return NameCheckVO.conflict("英文名称 " + name + " 已存在");
        }
        return NameCheckVO.ok();
    }

    @Override
    public NameCheckVO checkChineseName(String name, Long excludeId, Long tenantId) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getChineseName, name)
               .eq(OlapBasicPro::getTenantId, tenantId);
        if (excludeId != null) {
            wrapper.ne(OlapBasicPro::getId, excludeId);
        }
        if (basicProMapper.selectCount(wrapper) > 0) {
            return NameCheckVO.conflict("中文名称 " + name + " 已存在");
        }
        return NameCheckVO.ok();
    }

    // ---- 私有辅助方法 ----

    private MetricType parseMetricType(OlapBasicProIndicator indicator) {
        if (StrUtil.isNotBlank(indicator.getCalculatedProduction())) {
            return MetricType.CALC;
        }
        if (StrUtil.isNotBlank(indicator.getDerivativeProduction())) {
            return MetricType.DERIVE;
        }
        return MetricType.ATOM;
    }

    private List<String> parseDecoratedWord(OlapBasicProIndicator indicator) {
        if (StrUtil.isNotBlank(indicator.getDecoratedWord())) {
            try {
                return JsonUtil.fromJsonArray(indicator.getDecoratedWord(), String.class);
            } catch (Exception e) {
                log.warn("解析修饰词失败: {}", e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    // ==================== 计算指标虚拟表绑定 ====================

    /**
     * 从请求提取计算指标引用的原子指标 id 列表；非计算指标返回空。
     */
    private List<Long> extractCalcRefIds(MetricReq req) {
        if (!"calc".equals(req.getType()) || req.getCalculateFormula() == null) {
            return Collections.emptyList();
        }
        return MetricFormulaParser.extractCalcReferencedIds(JSONObject.toJSONString(req.getCalculateFormula()));
    }

    /**
     * 硬规则：计算指标引用的原子指标必须属于同一数据源，跨源直接阻断保存。
     */
    private void validateCalcSameSource(List<Long> refIds, Long tenantId) {
        if (refIds.isEmpty()) {
            return;
        }
        Set<Long> commonSources = null;
        for (Long refId : refIds) {
            Set<Long> refSources = resolveMetricSources(refId, tenantId);
            if (refSources.isEmpty()) {
                throw new BizException(400, "计算指标引用的原子指标未关联已上线决策表: id=" + refId);
            }
            commonSources = commonSources == null ? new HashSet<>(refSources) : autoVirtualTableBinder.intersect(commonSources, refSources);
        }
        if (commonSources.isEmpty()) {
            throw new BizException(400, "计算指标引用的原子指标必须属于同一数据源");
        }
    }

    /**
     * 原子指标（mapping index 行）所在表的 source_id 集合。
     */
    private Set<Long> resolveMetricSources(Long metricId, Long tenantId) {
        List<OlapTableFieldMapping> indexRows = tableFieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getBasicId, metricId)
                        .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.INDEX.getKey())
                        .eq(OlapTableFieldMapping::getStatus, 1)
                        .eq(OlapTableFieldMapping::getTenantId, tenantId));
        Set<Long> tableIds = indexRows.stream().map(OlapTableFieldMapping::getTableId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (tableIds.isEmpty()) {
            return Collections.emptySet();
        }
        return tableProMapper.selectList(new LambdaQueryWrapper<OlapTablePro>().in(OlapTablePro::getId, tableIds))
                .stream().map(OlapTablePro::getSourceId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 绑定入口：失败仅记日志，不影响指标保存结果。
     */
    private void bindCalcMetricToVirtualTable(OlapBasicPro metric, List<Long> refIds, Long tenantId) {
        try {
            doBindCalcMetric(metric, refIds, tenantId);
        } catch (Exception e) {
            log.error("计算指标绑定虚拟表失败，不影响指标保存 | metricId={}, refIds={}, error={}",
                    metric.getId(), refIds, e.getMessage(), e);
        }
    }

    /**
     * 核心绑定流程（复用公共组件）：
     * 1) 因子原子指标求共同数据源与维度交集
     * 2) 交集空 → 日志 + 清旧绑定后返回
     * 3) 交集非空 → 已绑定则校验交集是否仍匹配（匹配只刷新自己；不匹配解绑）
     * 4) 复用查找同交集自动表，命中则挂行；否则新建 view_auto_ 表
     */
    private void doBindCalcMetric(OlapBasicPro metric, List<Long> refIds, Long tenantId) {
        if (refIds == null || refIds.isEmpty()) {
            return;
        }
        AutoVirtualTableBinder.DimIntersectionResult ctx =
                autoVirtualTableBinder.resolveCommonSourceAndDims(refIds, tenantId, metric.getId(), "计算指标");
        if (ctx == null) {
            return;
        }
        if (ctx.getOrderedDims().isEmpty()) {
            deleteCalcMetricBinding(metric.getId(), tenantId);
            return;
        }
        Set<Long> commonDims = new HashSet<>(ctx.getOrderedDims());

        // 6. 定位已有绑定（该指标自己的 index 行）
        List<OlapTableFieldMapping> metricRows = tableFieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getBasicId, metric.getId())
                        .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.INDEX.getKey())
                        .eq(OlapTableFieldMapping::getStatus, 1)
                        .eq(OlapTableFieldMapping::getTenantId, tenantId));
        Long boundTableId = metricRows.stream().map(OlapTableFieldMapping::getTableId)
                .filter(Objects::nonNull).findFirst().orElse(null);

        if (boundTableId != null) {
            if (tableProMapper.selectById(boundTableId) == null) {
                // 绑定表已被删除：清残留行，视为未绑定
                tableFieldMappingMapper.delete(new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getBasicId, metric.getId())
                        .eq(OlapTableFieldMapping::getTenantId, tenantId));
                metricRows = Collections.emptyList();
                boundTableId = null;
            } else {
                Set<Long> tableDimIds = tableFieldMappingMapper.selectList(
                                new LambdaQueryWrapper<OlapTableFieldMapping>()
                                        .eq(OlapTableFieldMapping::getTableId, boundTableId)
                                        .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.DIMENSION.getKey())
                                        .eq(OlapTableFieldMapping::getStatus, 1)
                                        .eq(OlapTableFieldMapping::getTenantId, tenantId))
                        .stream().map(OlapTableFieldMapping::getBasicId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
                if (tableDimIds.equals(commonDims)) {
                    // 交集仍匹配：只刷新自己的 index 行（英文名/中文名可能变）+ 重拼 view_sql
                    for (OlapTableFieldMapping row : metricRows) {
                        row.setFieldKey(metric.getEnglishName());
                        row.setFieldName(metric.getChineseName());
                        row.setBasicName(metric.getChineseName());
                        tableFieldMappingMapper.updateById(row);
                    }
                    autoVirtualTableBinder.refreshAutoTable(boundTableId, tenantId);
                    log.info("计算指标绑定已更新（交集匹配） | metricId={}, tableId={}", metric.getId(), boundTableId);
                    return;
                }
                // 交集不再匹配：解绑自己的 index 行（表空则删表），之后重新走复用/新建
                for (OlapTableFieldMapping row : metricRows) {
                    tableFieldMappingMapper.deleteById(row.getId());
                }
                autoVirtualTableBinder.refreshAutoTable(boundTableId, tenantId);
                metricRows = Collections.emptyList();
                boundTableId = null;
            }
        }

        // 7. 复用查找：同租户同源、维度集合完全相同的自动表（取 id 最小）
        Long reusableTableId = autoVirtualTableBinder.findReusableTable(commonDims, ctx.getCommonSourceId(), tenantId);
        if (reusableTableId != null) {
            tableFieldMappingMapper.insert(autoVirtualTableBinder.buildIndexMapping(reusableTableId, metric, tenantId));
            autoVirtualTableBinder.refreshAutoTable(reusableTableId, tenantId);
            log.info("计算指标复用共享虚拟表 | metricId={}, tableId={}", metric.getId(), reusableTableId);
            return;
        }

        // 8. 新建虚拟表 + mapping（表名创建后不再变更）
        OlapTablePro sample = ctx.getSampleTable();
        OlapTablePro virtualTable = new OlapTablePro();
        virtualTable.setSourceId(ctx.getCommonSourceId());
        virtualTable.setDbName(sample != null ? sample.getDbName() : null);
        virtualTable.setTbName(autoVirtualTableBinder.genUniqueViewName(metric.getEnglishName(), tenantId));
        virtualTable.setCnName(metric.getChineseName() + "自动视图");
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
            tableFieldMappingMapper.insert(autoVirtualTableBinder.buildDimMapping(newTableId, ctx.getDimBasicMap().get(dimId),
                    autoVirtualTableBinder.findSourceRow(ctx.getTableRows(), dimId), tenantId));
        }
        tableFieldMappingMapper.insert(autoVirtualTableBinder.buildIndexMapping(newTableId, metric, tenantId));
        autoVirtualTableBinder.refreshAutoTable(newTableId, tenantId);
        log.info("计算指标绑定虚拟表成功 | metricId={}, tableId={}, tbName={}, dims={}",
                metric.getId(), newTableId, virtualTable.getTbName(), ctx.getOrderedDims());
    }

    /**
     * 删除计算指标的绑定：删自己的 index 行；共享表上有其他指标则保留表（重拼 view_sql），
     * 空了才删表。只处理自动生成的 tb_type=1 + view_auto_ 前缀表，不碰手工视图。
     */
    private void deleteCalcMetricBinding(Long metricId, Long tenantId) {
        try {
            List<OlapTableFieldMapping> metricRows = tableFieldMappingMapper.selectList(
                    new LambdaQueryWrapper<OlapTableFieldMapping>()
                            .eq(OlapTableFieldMapping::getBasicId, metricId)
                            .eq(OlapTableFieldMapping::getBasicTypeKey, FieldRegisterType.INDEX.getKey())
                            .eq(OlapTableFieldMapping::getTenantId, tenantId));
            if (metricRows.isEmpty()) {
                return;
            }
            Set<Long> tableIds = metricRows.stream().map(OlapTableFieldMapping::getTableId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            List<OlapTablePro> autoTables = tableProMapper.selectList(new LambdaQueryWrapper<OlapTablePro>()
                    .in(OlapTablePro::getId, tableIds)
                    .eq(OlapTablePro::getTbType, "1")
                    .likeRight(OlapTablePro::getTbName, "view_auto_"));
            if (autoTables.isEmpty()) {
                return;
            }
            Set<Long> autoTableIds = autoTables.stream().map(OlapTablePro::getId).collect(Collectors.toSet());
            for (OlapTableFieldMapping row : metricRows) {
                if (autoTableIds.contains(row.getTableId())) {
                    tableFieldMappingMapper.deleteById(row.getId());
                }
            }
            for (OlapTablePro table : autoTables) {
                autoVirtualTableBinder.refreshAutoTable(table.getId(), tenantId);
            }
            log.info("计算指标绑定已删除 | metricId={}, autoTables={}", metricId, autoTableIds);
        } catch (Exception e) {
            log.error("删除计算指标绑定失败 | metricId={}, error={}", metricId, e.getMessage(), e);
        }
    }
}
