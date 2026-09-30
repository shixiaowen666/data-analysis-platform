package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 编辑视图
 */
@Data
public class ViewEditDTO {

    @NotNull(message = "表 ID 不能为空")
    private Long tableId;

    private String viewCnName;

    private String sql;
}
