package com.chatbi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chatbi.chat.entity.DcarChatModelQA;
import com.chatbi.chat.response.ResultData;

import java.util.List;

public interface ChatModelQAService extends IService<DcarChatModelQA> {

    ResultData saveOrUpdateChat(DcarChatModelQA chatModelQA);

    void updateStatusByChatId(String chatSessionId, String chatId, Integer status);

    DcarChatModelQA getByChatKey(String chatSessionId, String chatId, Integer itemId);

    List<DcarChatModelQA> listByChatKey(String chatSessionId, String chatId, Long createdBy);

    int removeStepsByChatKey(String chatSessionId, String chatId, Long createdBy);
}
