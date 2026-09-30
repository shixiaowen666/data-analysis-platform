package com.bi.dto;

import lombok.Data;

/**
 * 指标列表查询参数
 */
@Data
public class MetricQueryDTO {

    private String keyword;

    private String type;

    private Integer status;

    private Long page = 1l;

    private Long pageSize = 10l;
}
