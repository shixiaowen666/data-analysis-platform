package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AIChatSessionVO {

    private Long id;

    @Schema(description = "会话id")
    private String chatSessionId;

    @Schema(description = "会话名称")
    private String chatName;

    @Schema(description = "智能体名称")
    private String name;

    @Schema(description = "智能体编码")
    private String aiBodyCode;

    @Schema(description = "minio路径")
    private String minioFilePath;
}
