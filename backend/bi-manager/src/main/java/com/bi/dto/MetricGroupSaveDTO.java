package com.bi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 指标组合保存（数据预览页保存）
 */
@Data
public class MetricGroupSaveDTO {

    /**
     * 组合 ID；为空新建，非空编辑
     */
    private Long id;

    @NotBlank(message = "组合编码不能为空")
    private String groupCode;

    @NotBlank(message = "组合名称不能为空")
    private String groupName;

    private String description;

    @NotBlank(message = "组合配置不能为空")
    private String groupConfig;
}
