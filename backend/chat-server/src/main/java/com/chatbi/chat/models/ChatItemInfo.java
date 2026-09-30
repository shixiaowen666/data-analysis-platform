package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ChatItemInfo<T> {

    @Schema(description = "类型 ai/user")
    private String type;

    @Schema(description = "问题")
    private String question;

    @Schema(description = "子项id")
    private Integer itemId;

    @Schema(description = "详情信息")
    private T chartData;

    @Schema(description = "修改人")
    private String updateUser;

    @Schema(description = "修改时间")
    private String updateTime;

    @Schema(description = "思考")
    private ThinkVO think;

    @Schema(description = "状态 0-正在思考 1-已完成 2-正在查数")
    private Integer status;

    @Schema(description = "获取智能体交互模式0-NL2DSL 1-NL2SQL 3-知识问答 4-自动模式")
    private Integer interactionMode;

    @Schema(description = "是否开启上下文引用")
    private Boolean enableContext;

    @Schema(description = "host地址")
    private String host;

    @Schema(description = "问题重写")
    private String questionRewriter;


    @Schema(description = "步骤类型:query,compute,summarize")
    private String stepType = "";

    public ChatItemInfo() {
    }

    public ChatItemInfo(String type, String question, Integer itemId, T chartData, String updateUser,
                        String updateTime, Integer status, ThinkVO think, String host) {
        this.type = type;
        this.question = question;
        this.itemId = itemId;
        this.chartData = chartData;
        this.updateUser = updateUser;
        this.updateTime = updateTime;
        this.status = status;
        this.think = think;
        this.host = host;
    }

}
