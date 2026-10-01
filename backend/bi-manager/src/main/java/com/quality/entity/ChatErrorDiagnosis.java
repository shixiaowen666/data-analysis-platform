package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · chat_error_diagnosis（JSON 列以 String 存取） */
@Data
@TableName("chat_error_diagnosis")
public class ChatErrorDiagnosis implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String chatId;
    private String chatSessionId;
    private String aiBodyCode;
    private Long tenantId;
    private String primaryErrorType;
    private String secondaryErrorTypes;
    private String errorStepId;
    private String expectedTable;
    private String actualTable;
    private String expectedEntities;
    private String actualEntities;
    private String rootCause;
    private String fixActionType;
    private String fixActionDetail;
    private Integer fixStatus;
    private Integer addRegression;
    private String expectedAnswerKeywords;
    private String diagnosedBy;
    private LocalDateTime diagnosedAt;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
