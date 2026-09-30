package com.bi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 维度扩展信息表
 */
@Data
@TableName("olap_basic_pro_dimension")
public class OlapBasicProDimension extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("olap_basic_pro_id")
    private Long olapBasicProId;

    /**
     * 1-标准维 2-杂项维
     */
    @TableField("dimension_type")
    private Integer dimensionType;

    /**
     * 1-高基维 2-普通维
     */
    @TableField("high_level_flag")
    private Integer highLevelFlag;

    /**
     * 标准维-取值来源表
     */
    @TableField("value_source_table")
    private String valueSourceTable;

    /**
     * 标准维-取值来源字段
     */
    @TableField("value_source_field")
    private String valueSourceField;

    /**
     * 取值筛选条件（SQL WHERE 片段）
     */
    @TableField("value_filter")
    private String valueFilter;

    /**
     * 杂项维-手工维值映射 JSON [{rawValue,displayValue}]
     */
    @TableField("value_entries")
    private String valueEntries;


    /**
     * 采集状态：0-未采集 1-全量采集 2-部分采集
     */
    @TableField("collect_status")
    private Integer collectStatus;

    /**
     * 部分采集时保存的上限值
     */
    @TableField("collect_limit")
    private Integer collectLimit;

    /**
     * 来源库表 ID（冗余）
     */
    @TableField("database_table_id")
    private Long databaseTableId;

    /**
     * 来源库表名（冗余）
     */
    @TableField("database_table_name")
    private String databaseTableName;

    /**
     * 来源字段 ID（冗余）
     */
    @TableField("column_id")
    private Long columnId;

    /**
     * 来源字段 key（冗余）
     */
    @TableField("column_key")
    private String columnKey;

    /**
     * 来源字段名（冗余）
     */
    @TableField("column_name")
    private String columnName;

    /**
     * 值字段 ID（冗余）
     */
    @TableField("value_field_id")
    private Long valueFieldId;

    /**
     * 值字段 key（冗余）
     */
    @TableField("value_field_key")
    private String valueFieldKey;

    /**
     * 值字段名（冗余）
     */
    @TableField("value_field_name")
    private String valueFieldName;

    /**
     * 维度说明 口径描述
     */
    @TableField("caliber_description")
    private String caliberDescription;

    /**
     * 维度可选值(JSON数组)
     */
    @TableField("dimension_values")
    private String dimensionValues;

    /**
     * 时间动态标记
     */
    @TableField("time_dynamic")
    private String timeDynamic;

    /**
     * 分区字段
     */
    @TableField("partition_field")
    private String partitionField;

    /**
     * 分区格式
     */
    @TableField("partition_format")
    private String partitionFormat;

    /**
     * 是否参与归因分析 0-否 1-是
     */
    @TableField("is_attributing")
    private Integer isAttributing;

    /**
     * 是否监控 Y/N
     */
    private String monitor;

    private Long tenantId;
}
