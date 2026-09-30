package com.bi.vo;

import lombok.Data;

/**
 * 指标映射项 VO
 */
@Data
public class MetricMappingItem {

    private String symbol;

    private Long metricId;

    private String code;

    private String name;
}
