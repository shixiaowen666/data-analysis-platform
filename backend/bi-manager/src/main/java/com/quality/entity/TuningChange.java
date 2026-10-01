package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_change（JSON 列以 String 存取） */
@Data
@TableName("tuning_change")
public class TuningChange implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer seq;
    private String source;
    private String ruleCode;
    private String confidence;
    private Integer accepted;
    private String assetType;
    private String targetModule;
    private String targetId;
    private String targetLabel;
    private String field;
    private String beforeValue;
    private String afterValue;
    private String diffSummary;
    private String reason;
    private String applyStatus;
    private String applyError;
    private LocalDateTime appliedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime rolledBackAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
