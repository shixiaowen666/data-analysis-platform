package com.chatbi.chat.service;

import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.entity.ChatStepTrace;

/**
 * 问答质量管理：链路证据落库。所有方法 try/catch 非致命，不影响主流程。
 */
public interface ChatTraceService {

    /** biChat 收到 analyze 响应后写一轮 trace（不存在则插入，存在则更新） */
    void saveAnalysisTrace(String chatSessionId, String chatId, String aiBodyCode,
                           Long tenantId, Long userId, String username,
                           String question, JSONObject databaseMeta,
                           JSONObject resolverResult, long elapsedMs);

    /** /getdata/ai 每步写 step trace */
    void saveStepTrace(ChatStepTrace trace);
}
