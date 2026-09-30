package com.bi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体
 */
@Data
@TableName("dcar_ai_body")
public class AiBody implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
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

    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Long updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 语音热词（JSON）
     */
    @JsonProperty("hotWords")
    private String hotWords;

    /**
     * 智能体描述
     */
    private String description;

    private Long tenantId;
}
