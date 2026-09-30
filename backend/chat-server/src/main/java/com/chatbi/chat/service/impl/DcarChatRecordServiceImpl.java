package com.chatbi.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatbi.chat.mapper.DcarChatRecordMapper;
import com.chatbi.chat.models.DcarChatRecord;
import com.chatbi.chat.service.DcarChatRecordService;
import org.springframework.stereotype.Service;

@Service
public class DcarChatRecordServiceImpl extends ServiceImpl<DcarChatRecordMapper, DcarChatRecord> implements DcarChatRecordService {

    @Override
    public void saveOrUpdateRecord(DcarChatRecord chatRecord) {
        DcarChatRecord existing = getOne(new LambdaQueryWrapper<DcarChatRecord>()
                .eq(DcarChatRecord::getChatSessionId, chatRecord.getChatSessionId()));
        if (existing != null) {
            chatRecord.setId(existing.getId());
        }
        saveOrUpdate(chatRecord);
    }
}
