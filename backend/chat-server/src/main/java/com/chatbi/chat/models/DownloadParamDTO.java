package com.chatbi.chat.models;

import lombok.Data;

/**
 * 快照下载请求参数
 */
@Data
public class DownloadParamDTO {
    private String chatSessionId;
    private String chatId;
    private Integer itemId;
}
