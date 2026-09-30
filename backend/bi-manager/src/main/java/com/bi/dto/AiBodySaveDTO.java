package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 智能体新建/编辑
 */
@Data
public class AiBodySaveDTO {

    @NotBlank(message = "智能体名称不能为空")
    private String name;

    private String code;

    /**
     * 智能体描述
     */
    private String description;
    /**
     * 关联数据表配置
     */
    private List<AiBodyTableRelDTO> tableRelations;

    /**
     * 知识库配置
     */
    private List<AiBodyKnowledgeDTO> knowledgeList;
}
