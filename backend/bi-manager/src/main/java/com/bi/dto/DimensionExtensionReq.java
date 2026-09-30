package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 维度扩展信息请求
 */
@Data
public class DimensionExtensionReq {

    /**
     * 维度类型：1-标准维 2-杂项维
     */
    @NotNull(message = "维度类型不能为空")
    private Integer dimensionType;

    /**
     * 标准维-取值来源表（表名.字段名格式）
     */
    private String valueSourceTable;

    /**
     * 标准维-取值来源字段
     */
    private String valueSourceField;

    /**
     * 取值筛选条件（SQL WHERE 片段）
     */
    private String valueFilter;

    /**
     * 杂项维-手工维值映射 [{rawValue,displayValue}]
     */
    private List<ValueEntry> valueEntries;

    /**
     * 来源库表 ID（冗余）
     */
    private Long databaseTableId;

    /**
     * 维度说明/口径描述
     */
    private String caliberDescription;
}
