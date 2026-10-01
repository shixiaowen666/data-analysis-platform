package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_verify_config（JSON 列以 String 存取） */
@Data
@TableName("tuning_verify_config")
public class TuningVerifyConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scope;
    private Integer maxCases;
    private Integer similarLimit;
    private Integer regressionLimit;
    private Integer concurrency;
    private Integer caseTimeoutS;
    private Integer totalTimeoutMin;
    private Integer judgeAnswer;
    private Integer judgeLlm;
    private BigDecimal temperature;
    private Integer requireApproval;
    private Integer warnNeedNote;
    private Integer onlineRecheck;
    private Integer observeDays;
    private Integer alertWindowHours;
    private Integer alertThreshold;
    private Integer retainTraceDays;
    private Integer retainSnapshotDays;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
