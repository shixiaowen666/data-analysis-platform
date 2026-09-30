package com.bi.vo;

import lombok.Data;

/**
 * 指标扩展信息查询结果行，用于获取 calculated/derivative JSON 判断指标类型。
 */
@Data
public class IndicatorInfoRow {

    private Long olapBasicProId;

    private String calculatedProduction;

    private String derivativeProduction;
}
