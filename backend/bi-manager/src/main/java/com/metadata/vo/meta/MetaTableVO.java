package com.metadata.vo.meta;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 元数据表 VO
 */
@Data
public class MetaTableVO {

    private Long id;

    private Long sourceId;

    private String schemaName;

    private String tableName;

    private String tableComment;

    private Integer columnCount;

    private Long rowCountEstimate;

    private LocalDateTime lastCollectedAt;
}
