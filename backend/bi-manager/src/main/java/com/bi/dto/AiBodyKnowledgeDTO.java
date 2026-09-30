package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 智能体知识库条目
 */
@Data
public class AiBodyKnowledgeDTO {

    @NotBlank(message = "知识内容不能为空")
    private String knowledgeElement;

    /**
     * 别名列表
     */
    private String knowledgeAlias;
}
