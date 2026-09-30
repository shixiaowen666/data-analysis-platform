package com.wm.semantic.dto;

import lombok.Data;

import java.util.List;

/**
 * 衍生指标的维度过滤条件。
 */
@Data
public class DerivativeFilter {

    /** 维度 ID */
    private Long dimensionId;

    /** 操作符：= != in notin */
    private String operator;

    /** 过滤值列表 */
    private List<String> values;

    /** 运行时填充：该维度对应的 fieldKey */
    private String fieldKey;
}
