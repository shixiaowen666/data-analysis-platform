package com.bi.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bi.dto.MetricReq;
import com.bi.dto.NameCheckReq;
import com.bi.entity.OlapBasicPro;
import com.bi.vo.MetricDetailVO;
import com.bi.vo.MetricVO;
import com.bi.vo.NameCheckVO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 指标管理 Service
 */
public interface IMetricService {

    /**
     * 分页查询指标列表
     */
    IPage<MetricVO> listMetrics(String keyword, String type, Integer status, Long tenantId, long page, long pageSize);

    /**
     * 获取指标详情
     */
    MetricDetailVO getMetricDetail(Long id);

    /**
     * 新建指标
     */
    Long createMetric(MetricReq req, Long tenantId);

    @Transactional(rollbackFor = Exception.class)
    OlapBasicPro createMetricOrExit(MetricReq req, Long tenantId);

    /**
     * 编辑指标
     */
    void updateMetric(Long id, MetricReq req, Long tenantId);

    /**
     * 获取指标代码（自动生成唯一编码）
     */
    String generateMetricCode();

    /**
     * 下线指标
     */
    void offlineMetric(Long id, Long tenantId);

    /**
     * 删除指标
     */
    void deleteMetric(Long id, Long tenantId);

    /**
     * 上线指标
     */
    void onlineMetric(Long id, Long tenantId);

    /**
     * 查询所有已上线的指标列表（不分页）
     */
    List<MetricVO> listAllMetrics(String keyword, Long tenantId);

    /**
     * 查询所有已上线指标 + 已上线指标组合（不分页，组合映射为 type=group）
     */
    List<MetricVO> listAllMetricsWithGroups(String keyword, Long tenantId);

    /**
     * 检查指标是否被计算指标引用
     */
    List<MetricVO> checkReferencedByCalculation(List<Long> metricIds);

    /**
     * 校验英文名是否已存在
     */
    NameCheckVO checkEnglishName(String name, Long excludeId, Long tenantId);

    /**
     * 校验中文名是否已存在
     */
    NameCheckVO checkChineseName(String name, Long excludeId, Long tenantId);
}
