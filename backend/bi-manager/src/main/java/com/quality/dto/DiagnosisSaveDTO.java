package com.quality.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 管理员保存定位结论 */
@Data
public class DiagnosisSaveDTO {
    @NotBlank private String chatId;
    private String chatSessionId;
    private String aiBodyCode;
    @NotBlank private String primaryErrorType;
    private List<String> secondaryErrorTypes;
    private String errorStepId;
    private String expectedTable;
    private String actualTable;
    /** [{type: metric|dim, code, name, actualCode}] */
    private List<Map<String, Object>> expectedEntities;
    private List<Map<String, Object>> actualEntities;
    private String rootCause;
    private String fixActionType;
    private String fixActionDetail;
    private Boolean addRegression;
    private List<String> expectedAnswerKeywords;
    /** 保存后是否立即生成调优建议 */
    private Boolean generateSuggestions;
}
