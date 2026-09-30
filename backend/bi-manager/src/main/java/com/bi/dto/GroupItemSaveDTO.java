package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class GroupItemSaveDTO {

    @NotBlank(message = "组合项类型不能为空")
    private String itemType;

    @NotNull(message = "对象 ID 不能为空")
    private Long objectId;

    private String displayName;

    @NotNull(message = "展示顺序不能为空")
    private Integer displayOrder;

    private Integer isRequired;

    private Integer isDefaultVisible;

    private String formatType;

    private String unit;

    private String defaultSort;
}
