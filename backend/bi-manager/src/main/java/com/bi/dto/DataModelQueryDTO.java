package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;

/**
 * 数据模型列表查询
 */
@Data
public class DataModelQueryDTO {

    private String keyword;

    private Integer status;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    private Integer pageSize = 10;
}
