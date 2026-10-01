package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_task（JSON 列以 String 存取） */
@Data
@TableName("tuning_task")
public class TuningTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long diagnosisId;
    private String chatId;
    private String aiBodyCode;
    private Long tenantId;
    private String primaryErrorType;
    private String question;
    private String status;
    private Integer round;
    private Long verifyReportId;
    private String verifySummary;
    private String verifyConfig;
    private String confirmNote;
    private Long approvalId;
    private LocalDateTime submittedAt;
    private String submittedBy;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private String approvalComment;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private LocalDateTime rolledBackAt;
    private String rolledBackBy;
    private String rollbackReason;
    private String onlineRecheck;
    private LocalDateTime observeUntil;
    private String observeAlert;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
