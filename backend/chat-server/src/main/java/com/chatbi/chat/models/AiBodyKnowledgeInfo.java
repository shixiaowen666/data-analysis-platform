package com.chatbi.chat.models;

import lombok.Data;

import java.util.List;

/**
 * <p>
 * 智能体单条知识库
 * </p>
 *
 * @author gj
 * @since 2025-01-17
 */
@Data
public class AiBodyKnowledgeInfo {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 智能体主键
     */

    private Integer aiBodyId;

    /**
     * 单条知识库内容
     */
    private String knowledgeElement;

    private List<String> knowledgeAlias;

    private Long tenantId;

}
