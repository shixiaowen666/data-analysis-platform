package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发送聊天消息
 */
@Data
public class ChatMessageDTO {

    @NotNull(message = "智能体编码不能为空")
    private String aiBodyCode;

    @NotBlank(message = "问题不能为空")
    private String question;

    /**
     * 会话ID（新对话可为空）
     */
    private String chatSessionId;

    /**
     * 是否开启上下文引用
     */
    private Boolean enableContext;
}
