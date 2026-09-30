package com.bi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能数据助手对话记录
 */
@Data
@TableName("dcar_chat_record")
public class ChatRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对话ID
     */
    private String chatSessionId;

    /**
     * 智能体编码
     */
    private String aiBodyCode;

    /**
     * 对话名称
     */
    private String chatName;

    /**
     * 对话JSON存放的minIO地址
     */
    private String minioFilePath;

    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Long updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 状态 0-未生效 1-已生效
     */
    private Integer status;

    /**
     * 交互模式 0-多维分析 1-即席分析
     */
    private Integer interactionMode;

    /**
     * 是否汇总计算 0-否 1-是
     */
    private Integer isCalculate;

    private String creatorChineseName;

    private String modifierChineseName;

    /**
     * 是否开启上下文引用
     */
    private Boolean enableContext;

    private Long tenantId;
}
