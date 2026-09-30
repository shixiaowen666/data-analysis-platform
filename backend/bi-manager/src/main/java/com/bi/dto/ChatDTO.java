package com.bi.dto;

import lombok.Data;

import java.util.List;


@Data
public class ChatDTO {

    private String chatId;

    private List<ChatItemInfo> chatItemInfo;

}
