package com.bi.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体知识库条目
 */
@Data
public class AiBodyKnowledgeVO {

    private Long id;

    private Long aiBodyId;

    /**
     * 知识内容
     */
    private String knowledgeElement;

    /**
     * 知识库别名（JSON字符串）
     */
    @JsonProperty("knowledgeAlias")
    private String knowledgeAlias;

    private LocalDateTime createdAt;
}
