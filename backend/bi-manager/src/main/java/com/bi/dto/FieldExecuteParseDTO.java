package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 执行 SQL 解析字段
 */
@Data
public class FieldExecuteParseDTO {
    /**
     * 数据源 ID
     */
    private Long sourceId;

    /**
     * 表 ID
     */
    private Long tableId;

    /**
     * SQL 语句
     */
    @NotBlank(message = "SQL 不能为空")
    private String sql;
}
