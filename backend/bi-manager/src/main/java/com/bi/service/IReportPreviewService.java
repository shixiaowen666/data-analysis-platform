package com.bi.service;

import com.bi.dto.CompatibleFieldQueryDTO;
import com.bi.dto.DimMetricQueryVO;
import com.bi.vo.CompatibleFieldsVO;
import com.bi.vo.DimMetricTreeDTO;

import java.util.List;

public interface IReportPreviewService {

    /**
     * 根据已选指标/维度，返回当前可用的维度与指标列表。
     */
    CompatibleFieldsVO listCompatibleFields(CompatibleFieldQueryDTO query, Long tenantId);


    List<DimMetricTreeDTO> dimAndMetric(DimMetricQueryVO queryVo, Long tenantId);
}
