package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · chat_feedback（JSON 列以 String 存取） */
@Data
@TableName("chat_feedback")
public class ChatFeedback implements Serializable {
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
    private String answerSnapshot;
    private Integer rating;
    private String errorTypes;
    private String description;
    private Integer status;
    private Long diagnosisId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
