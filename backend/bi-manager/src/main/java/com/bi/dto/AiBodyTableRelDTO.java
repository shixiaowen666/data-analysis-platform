package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 智能体关联数据表
 */
@Data
public class AiBodyTableRelDTO {

    @NotNull(message = "数据源ID不能为空")
    private Long relationId;
    /**
     * 数据源ID
     */
    @NotNull(message = "数据源ID不能为空")
    private Long sourceId;

    /**
     * 表名
     */
    @NotBlank(message = "表名不能为空")
    private String tableName;

    /**
     * 表注释
     */
    private String tableComment;

    /**
     * 关联类型 0-关联事实表 1-关联分析模型
     */
    private Integer relationType;
}
