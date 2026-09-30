package com.chatbi.chat.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RegenerateParam {

    @Schema(description = "点踩的回答")
    @JsonProperty("answer")
    private String answer;

    @Schema(description = "反馈意见")
    @JsonProperty("feedbackInfo")
    private String feedbackInfo;
}
