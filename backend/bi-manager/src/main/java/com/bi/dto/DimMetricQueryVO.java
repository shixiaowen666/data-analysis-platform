package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 数据预览指标维度list
 */
@Data
public class DimMetricQueryVO {

    /**
     * 已选指标 ID 列表，传入后过滤可用维度
     */
    private List<Long> metricIds;

    /**
     * 已选维度 ID 列表，传入后过滤可用指标
     */
    private List<Long> dimensionIds;

    /**
     * 中文名称模糊搜索
     */
    private String keyword;

    /**
     * 查维度列表传入:dim，查指标列表传入:metric
     */
    @NotBlank(message = "type 不能为空，维度传 dim，指标传 metric")
    private String type;
}
