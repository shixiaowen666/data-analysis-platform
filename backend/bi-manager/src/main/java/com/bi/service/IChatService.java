package com.bi.service;

import com.bi.dto.*;
import com.bi.entity.ChatRecord;
import com.bi.vo.*;

import java.util.List;
import java.util.Map;

/**
 * 智能问数 - 对话管理 Service
 */
public interface IChatService {

//    /**
//     * 创建或切换对话会话
//     */
//    ChatSessionVO createSession(ChatSessionDTO dto);

    /**
     * 获取智能体的会话列表
     */
    List<ChatSessionVO> listSessions(String aiBodyCode, Long tenantId);

//    /**
//     * 获取会话中的消息记录
//     */
//    List<ChatMessageVO> listMessages(String chatSessionId, Long tenantId);


    /**
     * 对话反馈（点赞/点踩）
     */
    void feedback(ChatFeedbackDTO dto);

//    /**
//     * 对话记录分页查询
//     */
//    ApiPageResult<ChatRecord> listRecords(ChatRecordQueryDTO query, Long tenantId);

    /**
     * 删除会话
     */
    void deleteSession(String chatSessionId, Long tenantId);

//    /**
//     * 新对话（清空当前会话的对话内容，仅返回欢迎态）
//     */
//    void newChat(String aiBodyCode, Long tenantId);

    Map<String, List<ChatRecord>> getRecordList(String keyword);

    ChatRecordDTO getSessionInfo(String chatSessionId);
    ChatDTO getChatInfo(String chatSessionId,String chatId);

    /**
     * 获取单步骤的执行结果
     */
    ChatItemInfo getChatStep(String chatSessionId, String chatId, Integer itemId);
}
