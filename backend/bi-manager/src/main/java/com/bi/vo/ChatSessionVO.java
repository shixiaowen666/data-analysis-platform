package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话信息
 */
@Data
public class ChatSessionVO {

    private Long id;

    private String chatSessionId;

    private String aiBodyCode;

    private String aiBodyName;

    private String chatName;

    /**
     * 交互模式 0-多维分析 1-即席分析
     */
    private Integer interactionMode;

    /**
     * 是否汇总计算
     */
    private Integer isCalculate;

    /**
     * 是否开启上下文引用
     */
    private Boolean enableContext;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
