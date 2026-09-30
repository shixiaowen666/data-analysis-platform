package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段映射详情
 */
@Data
public class FieldMappingVO {

    private Long id;

    private Long tableId;

    private String fieldKey;

    private String fieldName;

    private Long basicId;

    /**
     * 类型：维度/维度id/指标/分区字段/其他
     */
    private String basicType;

    /**
     * 类型 key：dim/dimid/index/other/pf
     */
    private String basicTypeKey;
    private String basicKey;
    /**
     * 注册名称
     */
    private String basicName;

    /**
     * 字段格式类型
     */
    private String fieldType;

    /**
     * 字段格式类型名称
     */
    private String fieldTypeName;

    /**
     * 字段注释
     */
    private String fieldNote;

    /**
     * 0-否 1-是
     */
    private Integer isCustomize;

    /**
     * 是否分区字段 0-否 1-是
     */
    private Integer pfStatus;

    /**
     * 聚合函数
     */
    private String summary;

    /**
     * 聚合函数 key
     */
    private String summaryKey;

    /**
     * 字段级转换表达式
     */
    private String expression;

    /**
     * 映射的 field_key
     */
    private String innerFieldKey;

    /**
     * 上溯关联 IDs
     */
    private String lookBackMappingIds;

    /**
     * 上溯关联显示信息
     */
    private String lookBackMappingNames;

    /**
     * 0-部分汇总 1-可汇总
     */
    private Integer lookBackFlag;

    /**
     * 排序字段
     */
    private Integer height;

    /**
     * 状态 0-删除 1-有效 2-待生效
     */
    private Integer status;

    private String registerStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    private String unit;
}
