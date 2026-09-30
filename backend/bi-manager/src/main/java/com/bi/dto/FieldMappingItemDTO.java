package com.bi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 字段映射项（语义映射编辑用）
 */
@Data
public class FieldMappingItemDTO {

    @NotNull(message = "字段 ID 不能为空")
    private Long id;

    /**
     * OLAP 字段 key
     */
    @NotBlank(message = "字段 key 不能为空")
    private String fieldKey;

    /**
     * OLAP 字段 key
     */
    private String fieldName;
    /**
     * 字段格式类型
     */
    private String fieldType;

    /**
     * 字段格式类型名称
     */
    private String fieldTypeName;
    /**
     * 注册类型 key：dim/dimid/index/other/pf
 
    */
    private String basicTypeKey;

    /**
     * 注册名称
     */
    private String basicKey;
    private String basicName;

    private Long basicId;

    /**
     * 聚合函数 key
     */
    private String summaryKey;

    /**
     * 字段级转换表达式
     */
    private String expression;

    /**
     * 是否可汇总 0-否 1-是
     */
    private Integer lookBackFlag;

    private String unit;

    @JsonProperty("isMetricNew")
    private boolean isMetricNew;

    private String registerStatus;
}
