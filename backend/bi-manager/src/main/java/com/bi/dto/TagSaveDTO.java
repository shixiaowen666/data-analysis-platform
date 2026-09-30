package com.bi.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class TagSaveDTO {

    private Long id;

    @NotEmpty(message = "标签名称不能为空")
    private String name;
}
