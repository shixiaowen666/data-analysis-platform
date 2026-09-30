package com.bi.service;

import com.bi.dto.MetricGroupSaveDTO;
import com.bi.vo.ApiPageResult;
import com.bi.vo.MetricGroupVO;

/**
 * 指标组合（数据预览保存）
 */
public interface IMetricGroupService {

    Long save(MetricGroupSaveDTO dto, Long tenantId);

    ApiPageResult<MetricGroupVO> listGroups(String keyword, Integer status, Integer page, Integer pageSize, Long tenantId);

    MetricGroupVO getDetail(Long id, Long tenantId);

    void online(Long id, Long tenantId);

    void offline(Long id, Long tenantId);

    void delete(Long id, Long tenantId);
}
