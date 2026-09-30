package com.chatbi.chat.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AIChatVO {

    @Schema(description = "会话id")
    @JsonProperty("session_id")
    private String chatSessionId;

    @Schema(description = "本次对话的id")
    @JsonProperty("chat_id")
    private String chatId;

    @Schema(description = "问题")
    private String question;

    @Schema(description = "item")
    private Integer itemId;

    @Schema(description = "回答")
    private String answer;

    @Schema(description = "用户反馈 1.赞 -1踩 0默认")
    private Integer feedback;

    @Schema(description = "点踩原因")
    private String reason;

    private String sqlAnswer;
}
