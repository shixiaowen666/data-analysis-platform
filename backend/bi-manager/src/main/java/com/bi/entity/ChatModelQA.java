package com.bi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bi.vo.DataCardVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 智能数据助手-模型问答记录
 */
@Data
@TableName("dcar_chat_model_qa")
public class ChatModelQA implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private String chatSessionId;

    /**
     * 对话ID
     */
    private String chatId;

    /**
     * 问题
     */
    private String question;

    /**
     * 模型返回
     */
    private String answer;

    /**
     * 用户反馈 1-赞 -1-踩 0-默认
     */
    private Integer feedback;

    private Long userId;


    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;

    private Long updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;


    /**
     * 点踩原因
     */
    private String reason;

    /**
     * 对话JSON存放的minIO地址
     */
    private String minioFilePath;

    /**
     * 主表ID
     */
    private Long recordId;


    /**
     * 修改人ID
     */
    private Long updateBy;

    /**
     * 历史数据存储地址
     */
    private String historyDataMinioPath;

    /**
     * 子项ID
     */
    private Integer itemId;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 类型 user/ai
     */
    private String type;

    /**
     * 状态 0-执行中 1-已完成 2-正在思考 3-正在查数 4-停止
     */
    private Integer status;

    /**
     * 数据结果卡片（非数据库字段，仅用于接口传输）
     */
    @JsonIgnore
    private transient DataCardVO dataCard;
}
