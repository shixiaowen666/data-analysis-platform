package com.quality.dto;

import lombok.Data;

@Data
public class RollbackDTO {
    private String reason;
    /** PARTIAL | FORCE */
    private String mode = "FORCE";
}
