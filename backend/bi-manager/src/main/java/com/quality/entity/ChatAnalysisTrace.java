package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · chat_analysis_trace（JSON 列以 String 存取） */
@Data
@TableName("chat_analysis_trace")
public class ChatAnalysisTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String chatSessionId;
    private String chatId;
    private String aiBodyCode;
    private Long tenantId;
    private Long userId;
    private String username;
    private String question;
    private Integer recallEnabled;
    private Integer recallElapsedMs;
    private String recallTables;
    private String recallMetricCodes;
    private String recallDimCodes;
    private Integer metaTableCount;
    private Integer metaMetricCount;
    private Integer metaDimCount;
    private String decompositionSteps;
    private String executionLog;
    private String usedTables;
    private String resolvedMetrics;
    private String resolvedDims;
    private String finalAnswer;
    private String status;
    private String systemBLogPath;
    private Integer totalElapsedMs;
    private String llmModel;
    private String promptVersion;
    private String fullResponseMinioPath;
    private String autoErrorHint;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
