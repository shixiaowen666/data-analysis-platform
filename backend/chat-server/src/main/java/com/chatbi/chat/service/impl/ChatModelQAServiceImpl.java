package com.chatbi.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatbi.chat.entity.DcarChatModelQA;
import com.chatbi.chat.mapper.ChatModelQAMapper;
import com.chatbi.chat.response.ResultData;
import com.chatbi.chat.service.ChatModelQAService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatModelQAServiceImpl extends ServiceImpl<ChatModelQAMapper, DcarChatModelQA> implements ChatModelQAService {

    @Override
    public ResultData saveOrUpdateChat(DcarChatModelQA chatModelQA) {
        QueryWrapper<DcarChatModelQA> qw = new QueryWrapper<>();
        qw.eq(DcarChatModelQA.CHAT_ID, chatModelQA.getChatId())
                .eq(DcarChatModelQA.ITEM_ID, chatModelQA.getItemId());
        DcarChatModelQA existing = baseMapper.selectOne(qw);
        if (existing != null) {
            existing.setUpdatedBy(chatModelQA.getUpdatedBy());
            existing.setMinioFilePath(chatModelQA.getMinioFilePath());
            existing.setStatus(chatModelQA.getStatus());
        } else {
            existing = chatModelQA;
        }
        saveOrUpdate(existing);
        return ResultData.success();
    }

    @Override
    public void updateStatusByChatId(String chatSessionId, String chatId, Integer status) {
        LambdaUpdateWrapper<DcarChatModelQA> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DcarChatModelQA::getChatSessionId, chatSessionId)
                .eq(DcarChatModelQA::getChatId, chatId)
                .set(DcarChatModelQA::getStatus, status);
        update(wrapper);
    }

    @Override
    public DcarChatModelQA getByChatKey(String chatSessionId, String chatId, Integer itemId) {
        return baseMapper.selectByChatKey(chatSessionId, chatId, itemId);
    }

    @Override
    public List<DcarChatModelQA> listByChatKey(String chatSessionId, String chatId, Long createdBy) {
        return list(new LambdaQueryWrapper<DcarChatModelQA>()
                .eq(DcarChatModelQA::getChatSessionId, chatSessionId)
                .eq(DcarChatModelQA::getChatId, chatId)
                .eq(createdBy != null, DcarChatModelQA::getCreatedBy, createdBy)
                .orderByAsc(DcarChatModelQA::getItemId));
    }

    @Override
    public int removeStepsByChatKey(String chatSessionId, String chatId, Long createdBy) {
        return baseMapper.delete(new LambdaQueryWrapper<DcarChatModelQA>()
                .eq(DcarChatModelQA::getChatSessionId, chatSessionId)
                .eq(DcarChatModelQA::getChatId, chatId)
                .eq(createdBy != null, DcarChatModelQA::getCreatedBy, createdBy)
                .gt(DcarChatModelQA::getItemId, 0));
    }
}
