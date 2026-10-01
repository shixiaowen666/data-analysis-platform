package com.quality.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveDTO {
    @NotNull private Boolean approved;
    private String comment;
}
