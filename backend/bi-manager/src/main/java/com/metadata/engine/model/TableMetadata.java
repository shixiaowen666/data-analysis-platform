package com.metadata.engine.model;

import lombok.Data;

/**
 * 远程表元数据
 */
@Data
public class TableMetadata {

    private String schemaName;

    private String tableName;

    private String tableComment;

    private Long rowCountEstimate;
}
