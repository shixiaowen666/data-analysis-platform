package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 对话反馈
 */
@Data
public class ChatFeedbackDTO {

    @NotNull(message = "问答记录ID不能为空")
    private Long id;

    /**
     * 反馈 1-赞 -1-踩
     */
    @NotNull(message = "反馈值不能为空")
    private Integer feedback;

    /**
     * 点踩原因
     */
    private String reason;
}
