package com.bi.vo;

import lombok.Data;

/**
 * 结构配置区（B 区）默认值，供前端勾选后直接渲染表格
 */
@Data
public class StructureConfigDefaults {

    /**
     * 展示名称，默认取中文名
     */
    private String displayName;

    /**
     * 展示顺序；候选阶段为空，拖入 B 区后由前端赋值
     */
    private Integer displayOrder;

    /**
     * 类型中文名：维度 / 原子指标 / 计算指标 / 派生指标 / 指标组合
     */
    private String itemTypeName;

    /**
     * 是否必选 0-否 1-是
     */
    private Integer isRequired;

    /**
     * 是否默认展示 0-否 1-是
     */
    private Integer isDefaultVisible;

    /**
     * 格式类型：amount-金额 / percent-百分比，维度一般为 null
     */
    private String formatType;

    /**
     * 单位，如 元 / %
     */
    private String unit;

    /**
     * 默认排序：default-默认 / DESC-降序 / ASC-升序
     */
    private String defaultSort;
}
