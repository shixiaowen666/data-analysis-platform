package com.quality.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RegressionCaseDTO {
    private Long id;
    private String aiBodyCode;
    @NotBlank private String question;
    private Map<String, Object> expected;
    private String source = "MANUAL";
    private String sourceChatId;
    private List<String> tags;
    private Boolean enabled = true;
}
