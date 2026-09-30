package com.wm.semantic.dto;

import lombok.Data;

/**
 * 请求指标元数据。
 */
@Data
public class IndicatorMeta {
    /** 指标ID，对应 basic_id。 */
    private Long id;
    /** 物理字段名或基础宽表中的字段名。 */
    private String fieldName;
    /** 聚合函数：sum/count/avg/max/min/countDistinct。 */
    private String aggFunc;
    /** 指标输出别名，格式通常为 alias_xxx。 */
    private String alias;
    /** 是否为计算指标（公式指标） */
    private boolean calculated;
    /** 计算指标的公式元数据，仅 calculated=true 时有值 */
    private CalculatedIndicatorMeta formulaMeta;
    /** 是否为衍生指标 */
    private boolean derivative;
    /** 衍生指标的元数据，仅 derivative=true 时有值 */
    private DerivativeIndicatorMeta derivativeMeta;
    /** 纯内部子指标：单源时不输出 SQL 列，仅公式内联使用 */
    private boolean internal;
}
