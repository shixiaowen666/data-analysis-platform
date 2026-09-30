package com.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meta_collect_log")
public class MetaCollectLog extends MetaBaseEntity {

    private Long sourceId;

    private Long tenantId;

    private String collectType;

    private String status;

    private String tableNames;

    private Integer tableCount;

    private Integer columnCount;

    private Integer updatedTableCount;

    private Integer updatedColumnCount;

    private Integer deletedTableCount;

    private Integer deletedColumnCount;

    private String collectLog;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
