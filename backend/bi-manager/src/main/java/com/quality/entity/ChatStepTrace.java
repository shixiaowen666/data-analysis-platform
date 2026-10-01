package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · chat_step_trace（JSON 列以 String 存取） */
@Data
@TableName("chat_step_trace")
public class ChatStepTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String chatId;
    private String stepId;
    private String queryDescription;
    private String expectedTable;
    private String expectedColumns;
    private Long resolvedTableId;
    private String resolvedTableName;
    private String resolvedIndicatorIds;
    private String resolvedIndicatorNames;
    private String resolvedDimensionIds;
    private String resolvedDimensionNames;
    private String unmatchedColumns;
    private String filters;
    private String timeRange;
    private Integer groupExpanded;
    private String sqlText;
    private Integer rowCount;
    private Integer elapsedMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
