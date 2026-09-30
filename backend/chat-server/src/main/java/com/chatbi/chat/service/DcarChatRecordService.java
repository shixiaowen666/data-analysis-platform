package com.chatbi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chatbi.chat.models.DcarChatRecord;

public interface DcarChatRecordService extends IService<DcarChatRecord> {

    void saveOrUpdateRecord(DcarChatRecord chatRecord);
}
