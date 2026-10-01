package com.quality.dto;

import lombok.Data;

/** 调优变更（建议 / 手动） */
@Data
public class ChangeDTO {
    private Long id;
    private Integer seq;
    private String source = "SUGGEST";
    private String ruleCode;
    private String confidence = "HIGH";
    private Boolean accepted = true;
    private String assetType;
    private String targetModule;
    private String targetId;
    private String targetLabel;
    private String field;
    private String beforeValue;
    private String afterValue;
    private String diffSummary;
    private String reason;
}
