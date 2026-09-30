package com.bi.vo;

import lombok.Data;

/**
 * SQL 解析字段结果
 */
@Data
public class ParseColumnVO {

    /**
     * 字段英文名
     */
    private String fieldKey;

    /**
     * 字段中文名
     */
    private String fieldName;

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 对比状态：0-保留 1-新增 2-废弃
     */
    private String compareStatus;
}
