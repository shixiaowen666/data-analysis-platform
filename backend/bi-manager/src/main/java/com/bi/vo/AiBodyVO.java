package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体列表项
 */
@Data
public class AiBodyVO {

    private Integer id;

    private String code;

    private String name;

    /**
     * 交互模式 0-多维分析 1-即席分析
     */
    private Integer interactionMode;

    /**
     * 授权策略 0-私有 1-公开 2-自定义
     */
    private Integer authorizeStrategy;

    private String themeCode;

    private Long tenantId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
