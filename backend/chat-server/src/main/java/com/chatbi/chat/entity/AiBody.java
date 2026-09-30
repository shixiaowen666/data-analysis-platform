package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chatbi.chat.models.AiBodyKnowledgeInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("dcar_ai_body")
public class AiBody {

    private Integer id;

    @Schema(description = "标识编码")
    private String code;

    @Schema(description = "智能体名称")
    private String name;

    @Schema(description = "智能体描述")
    private String description;

    @Schema(description = "交互模式0-多维分析1-即席分析")
    private Integer interactionMode;

    @Schema(description = "关联主题编码")
    private String themeCode;

    @Schema(description = "授权策略(0-私有 1-公开 2-自定义)")
    private Integer authorizeStrategy;

    @Schema(description = "创建人")
    private Long createdBy;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @Schema(description = "修改人")
    private Long updatedBy;

    @Schema(description = "修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    @Schema(description = "语音热词")
    private String hotWords;

    @Schema(description = "租户id")
    private Long tenantId;

    @TableField(exist = false)
    private List<AiBodyKnowledgeInfo> AiBodyKnowledgeInfoList;
}
