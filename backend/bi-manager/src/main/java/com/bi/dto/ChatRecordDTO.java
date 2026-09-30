package com.bi.dto;
import lombok.Data;

import java.util.List;

/***
 * @ClassName DcarChatRecordDTO
 * @Description
 * @Author chenxiwen
 * @Date 8/27/24 5:45 PM
 * @Version 1.0
 */
@Data
public class ChatRecordDTO {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 对话id
     */
    private String chatSessionId;

    /**
     * 对话名称
     */
    private String chatName;


    /**
     * 智能体编码
     */
    private String aiBodyCode;

    /**
     * 智能体名称
     */
    private String name;


    /**
     * 智能体图标
     */
    private List<ChatDTO> chatInfo;



}
