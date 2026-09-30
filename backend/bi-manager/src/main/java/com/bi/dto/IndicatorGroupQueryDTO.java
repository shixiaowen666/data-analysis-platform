package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;

@Data
public class IndicatorGroupQueryDTO {

    private String keyword;

    private Integer status;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    private Integer pageSize = 10;
}
