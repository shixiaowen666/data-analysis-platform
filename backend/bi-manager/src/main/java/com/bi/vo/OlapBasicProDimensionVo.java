package com.bi.vo;

import com.bi.dto.ValueEntry;
import lombok.Data;

import java.util.List;

@Data
public class OlapBasicProDimensionVo {
    /**
     * 1-标准维 2-杂项维
     */
    private Integer dimensionType;

    /**
     * 取值来源表
     */
    private String valueSourceTable;

    /**
     * 取值来源字段
     */
    private String valueSourceField;

    /**
     * 取值筛选条件
     */
    private String valueFilter;

    /**
     * 杂项维-手工维值映射
     */
    private List<ValueEntry> valueEntries;

    /**
     * 0-未采集 1-全量采集 2-部分采集
     */
    private Integer collectStatus;


    /**
     * 来源库表 ID
     */
    private Long databaseTableId;

    /**
     * 值字段 ID
     */
    private Long valueFieldId;

    /**
     * 值字段名
     */
    private String valueFieldName;

    private String caliberDescription;
}
