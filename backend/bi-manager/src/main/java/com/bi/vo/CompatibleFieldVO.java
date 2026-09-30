package com.bi.vo;

import lombok.Data;

@Data
public class CompatibleFieldVO {

    private Long id;

    private String code;

    private String name;

    private String englishName;

    private String typeLabel;

    /**
     * dimension / atomic_metric / calculated_metric / derived_metric
     */
    private String itemType;

    /**
     * 日期类维度固定排第一
     */
    private Boolean fixedFirst;

    // ---------- 结构配置区（B 区）默认值 ----------

    private String displayName;

    private Integer displayOrder;

    private String itemTypeName;

    private Integer isRequired;

    private Integer isDefaultVisible;

    private String formatType;

    private String unit;

    private String defaultSort;
}
