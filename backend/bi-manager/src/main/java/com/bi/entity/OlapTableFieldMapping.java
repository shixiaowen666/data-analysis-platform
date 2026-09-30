package com.bi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 元数据字段映射表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("olap_table_field_mapping")
public class OlapTableFieldMapping extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联 olap_table_pro.id
     */
    private Long tableId;

    /**
     * OLAP 字段 key
     */
    private String fieldKey;

    /**
     * OLAP 字段名称
     */
    private String fieldName;

    /**
     * 关联 olap_basic_pro.id
     */
    private Long basicId;

    /**
     * 类型：维度dim/指标index/其他other表示被忽略
     */
    private String basicType;

    /**
     * 类型 key：dim/index/
     */
    private String basicTypeKey;

    private String basicKey;

    /**
     * 指标或维度名称
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
     * 聚合函数 sum/count/count distinct/avg/time_format
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
     * 0-删除 1-有效 2-待生效
     */
    private Integer status=1;

    /**
     * 租户 ID
     */
    private Long tenantId;

    private String unit;
    private String registerStatus;

    /**
     * 源字段日期格式，如 yyyyMMdd、yyyy-MM-dd；NULL 表示标准日期类型
     */
    private String dateFormat;
}
