package com.bi.dto;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 新建/编辑数据模型
 */
@Data
public class DataModelSaveDTO {

    @NotBlank(message = "模型名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "数据源 ID 不能为空")
    private Long sourceId;

    @NotNull(message = "主表 ID 不能为空")
    private Long factTableId;

    @NotEmpty(message = "关联表配置至少 1 条")
    @Valid
    private List<JoinConfigDTO> joins;
}
