package com.bi.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompatibleFieldQueryDTO {

    /**
     * 已选指标 ID 列表，传入后过滤可用维度
     */
    private List<Long> metricIds;

    /**
     * 已选维度 ID 列表，传入后过滤可用指标
     */
    private List<Long> dimensionIds;

    /**
     * 名称模糊搜索
     */
    private String keyword;
}
