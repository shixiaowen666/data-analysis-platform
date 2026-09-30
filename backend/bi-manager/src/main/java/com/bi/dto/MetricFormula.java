package com.bi.dto;

import lombok.Data;
import java.util.List;

/**
 * 计算指标公式结构
 */
@Data
public class MetricFormula {

    private String formula;

    private List<MetricMappingItem> mapping;
}
