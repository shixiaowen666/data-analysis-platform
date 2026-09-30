package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体详情
 */
@Data
public class AiBodyDetailVO {

    private Integer id;

    private String code;

    private String name;

    /**
     * 交互模式 0-多维分析 1-即席分析
     */
    private Integer interactionMode;

    private String themeCode;

    /**
     * 授权策略 0-私有 1-公开 2-自定义
     */
    private Integer authorizeStrategy;

    /**
     * 语音热词（JSON字符串）
     */
    private String hotWords;

    /**
     * 智能体描述
     */
    private String description;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;

    /**
     * 关联数据表列表
     */
    private List<OlapModelRelationVO> tableRelations;

    /**
     * 知识库列表
     */
    private List<AiBodyKnowledgeVO> knowledgeList;
}
