package com.metadata.engine.model;

import lombok.Data;

/**
 * 远程字段元数据
 */
@Data
public class ColumnMetadata {

    private String columnName;

    private String dataType;

    private Integer isNullable;

    private Integer isPk;

    private String defaultValue;

    private String columnComment;

    private Integer ordinal;
}
