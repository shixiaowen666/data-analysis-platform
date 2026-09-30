package com.chatbi.chat.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AIChatParam {

    @Schema(description = "智能体编码")
    private String aicode;
    /**
     * chatSessionId如果前端传入此参数，说明当前是一个已经存在的会话，后端不需要新建会话，记录当前对话即可
     */
    @Schema(description = "会话id")
    private String chatSessionId;

    @Schema(description = "问题")
    private String question;
}
