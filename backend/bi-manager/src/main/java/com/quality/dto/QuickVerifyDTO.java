package com.quality.dto;

import lombok.Data;

@Data
public class QuickVerifyDTO {
    private Long taskId;
    /** prompt-main / prompt-summary */
    private String groupName;
    private String content;
    private String question;
    private String aiBodyCode;
}
