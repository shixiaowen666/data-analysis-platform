package com.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 用户提交/更新反馈 */
@Data
public class FeedbackSubmitDTO {
    @NotBlank private String chatSessionId;
    @NotBlank private String chatId;
    private String aiBodyCode;
    private String question;
    private String answerSnapshot;
    /** 1 赞 / -1 踩 */
    @NotNull private Integer rating;
    /** 错误类型 code 列表（踩时） */
    private List<String> errorTypes;
    @Size(max = 1000) private String description;
}
