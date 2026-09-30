package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量注册维度/指标
 */
@Data
public class BatchRegisterDTO {

    @NotNull(message = "表 ID 不能为空")
    private Long tableId;

    /**
     * 注册类型 key：dim/dimid（维度）或 index（指标）
     */
    @NotEmpty(message = "注册类型不能为空")
    private String typeKey;

    @NotEmpty(message = "请勾选至少一个字段")
    private List<Long> fieldIds;
}
