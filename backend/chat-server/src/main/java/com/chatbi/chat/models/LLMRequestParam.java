package com.chatbi.chat.models;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class LLMRequestParam {

    @Schema(description = "用户id")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "用户名")
    @JsonProperty("username")
    private String username;

    @Schema(description = "会话id")
    @JsonProperty("session_id")
    private String chatSessionId;

    @Schema(description = "会话内的单次对话id")
    @JsonProperty("chat_id")
    private String chatId;

    @Schema(description = "子项id")
    @JsonProperty("item_id")
    private String itemId;

    @Schema(description = "问题代码")
    @JsonProperty("question_code")
    private String questionCode;

    @Schema(description = "是否是批量请求，默认是true")
    @JsonProperty("batch")
    private Boolean batch;

    @Schema(description = "本次问题")
    @JsonProperty("question")
    private String question;

    @Schema(description = "问题类型")
    @JsonProperty("questionType")
    private String questionType;

    @Schema(description = "重新生成答案所需参数")
    @JsonProperty("regenerate_param")
    private RegenerateParam regenerateParam;

    @Schema(description = "聊天上下文信息")
    @JsonProperty("history")
    private List<AIChatVO> history;

    @Schema(description = "场景编码")
    @JsonProperty("scenario")
    private String scenario;

    @Schema(description = "单条知识库")
    @JsonProperty("relatedKnowledge")
    private List<AiBodyKnowledgeInfo> relatedKnowledge;

    @JsonProperty("rewrite_question")
    private String rewriteQuestion;

    @JsonProperty("is_rewrite")
    private Boolean isReWrite = false;

    @Schema(description = "消息归属的stream")
    @JsonProperty("stream_name")
    private String streamName;

    @Schema(description = "提示词")
    private String agentPrompt;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("query")
    private String query;

    @JsonProperty("database_meta")
    private JSONObject databaseMeta;
}
