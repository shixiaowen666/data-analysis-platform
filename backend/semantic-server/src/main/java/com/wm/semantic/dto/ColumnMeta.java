package com.wm.semantic.dto;

import lombok.Data;

/**
 * 列元数据（维度或指标）。
 */
@Data
public class ColumnMeta {
    /** 字段标识 key，对应 basic_key。 */
    private String key;
    /** 中文名，对应 olap_basic_pro.chinese_name。 */
    private String name;
    /** 维度/指标，对应 "dimension" / "indicator"。 */
    private String type;
    /** 单位，指标有值，维度为 null。 */
    private String unit;
    /** 小数位数，指标有值，维度为 null。 */
    private Integer precision;
}
