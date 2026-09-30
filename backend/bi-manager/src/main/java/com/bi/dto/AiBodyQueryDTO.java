package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 智能体列表查询
 */
@Data
public class AiBodyQueryDTO {

    private String keyword;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer page;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为 1")
    private Integer pageSize;
}
