package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 模型关联表 JOIN 配置
 */
@Data
public class JoinConfigDTO {

    @NotNull(message = "关联表 ID 不能为空")
    private Long dimTableId;

    @NotBlank(message = "关联关系不能为空")
    private String joinType;

    @NotBlank(message = "主表关联字段不能为空")
    private String factFkColumn;

    @NotBlank(message = "关联表关联字段不能为空")
    private String dimPkColumn;
}
