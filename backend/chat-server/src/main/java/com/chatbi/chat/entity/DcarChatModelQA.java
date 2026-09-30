package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chatbi.chat.models.ChatItemInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dcar_chat_model_qa")
public class DcarChatModelQA {

    private Long id;

    private String chatSessionId;

    private String chatId;

    private Integer itemId;

    private String question;

    @TableField(exist = false)
    private ChatItemInfo itemInfo;

    private String minioFilePath;

    @Schema(description = "创建人id")
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "修改人id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", locale = "zh", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", locale = "zh", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String chatInfo;

    public static final String CHAT_ID = "chat_id";

    public static final String ITEM_ID = "item_id";

    @TableField(exist = false)
    @Schema(description = "aiQuery返回的数据")
    private String queryData;

    public DcarChatModelQA(String chatSessionId, String chatId, Integer itemId, ChatItemInfo itemInfo, String minioFilePath, Long updatedBy, Integer status) {
        this.chatSessionId = chatSessionId;
        this.chatId = chatId;
        this.itemId = itemId;
        this.itemInfo = itemInfo;
        this.minioFilePath = minioFilePath;
        this.updatedBy = updatedBy;
        this.status = status;
    }

    public DcarChatModelQA() {
    }
}
