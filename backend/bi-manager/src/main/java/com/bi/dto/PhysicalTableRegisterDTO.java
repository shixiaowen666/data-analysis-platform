package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 注册物理表
 */
@Data
public class PhysicalTableRegisterDTO {

    @NotNull(message = "数据源 ID 不能为空")
    private Long sourceId;

    @NotEmpty(message = "请至少选择一张表")
    private List<Long> tableIds;
}
