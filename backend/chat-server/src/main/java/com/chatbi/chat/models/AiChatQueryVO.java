package com.chatbi.chat.models;

import com.chatbi.chat.request.AIChatParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 存到minio的数据格式
 * @param <T>
 */
@Data
public class AiChatQueryVO<T> {

    @Schema(description = "getdata请求参数")
    private DataRequestDTO dataRequestDTO;

    @Schema(description = "getdata返回参数")
    private T dataVO;

   private AIChatVO aiChatVO;

    public AiChatQueryVO() {
    }

    public AiChatQueryVO(DataRequestDTO dataRequestDTO, T dataVO,AIChatVO aiChatVO) {
        this.dataRequestDTO = dataRequestDTO;
        this.dataVO = dataVO;
        this.aiChatVO=aiChatVO;
    }
}
