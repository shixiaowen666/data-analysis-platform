package com.bi.dto;

import lombok.Data;

/**
 * 计算指标公式映射项
 */
@Data
public class MetricMappingItem {

    private String symbol;

    private Long metricId;

    private String code;

    private String name;
}
