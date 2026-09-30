package com.bi.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 聊天消息 VO
 */
@Data
public class ChatMessageVO {

    private Long id;

    private String chatSessionId;

    private String chatId;

    /**
     * 消息类型 user/ai
     */
    private String type;

    private String question;

    private String answer;

    /**
     * 用户反馈 1-赞 -1-踩 0-默认
     */
    private Integer feedback;

    private String reason;

    /**
     * 状态 0-执行中 1-已完成 2-正在思考 3-正在查数 4-停止
     */
    private Integer status;

    private Date createTime;

    /**
     * 数据结果卡片（当类型为 ai 且包含查询结果时）
     */
    private DataCardVO dataCard;
}
