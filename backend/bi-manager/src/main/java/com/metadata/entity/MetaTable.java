package com.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 元数据-表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meta_table")
public class MetaTable extends MetaBaseEntity {

    private Long sourceId;

    private Long tenantId;

    private String schemaName;

    private String tableName;

    private String tableComment;

    private Long rowCountEstimate;

    private LocalDateTime lastCollectedAt;
}
