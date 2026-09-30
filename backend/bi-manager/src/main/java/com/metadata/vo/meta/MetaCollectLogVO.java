package com.metadata.vo.meta;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集日志 VO
 */
@Data
public class MetaCollectLogVO {

    private Long id;

    private Long sourceId;

    private String collectType;

    private String collectTypeLabel;

    private String status;

    private Integer tableCount;

    private Integer columnCount;

    private Integer updatedTableCount;

    private Integer updatedColumnCount;

    private Integer deletedTableCount;

    private Integer deletedColumnCount;

    /**
     * 变更摘要：新增表/修改表/新增字段/修改字段/删除表/删除字段
     */
    private String diffSummary;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationSeconds;

    /**
     * 采集过程步骤日志
     */
    private List<CollectLogEntryVO> logLines;

    /**
     * 失败原因（仅 status=FAIL 时有值）
     */
    private String errorMsg;
}
