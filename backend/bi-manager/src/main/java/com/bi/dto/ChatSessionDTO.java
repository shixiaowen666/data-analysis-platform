package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建/切换对话会话
 */
@Data
public class ChatSessionDTO {

    @NotNull(message = "智能体编码不能为空")
    private String aiBodyCode;

    /**
     * 对话名称
     */
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
}
