package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_verify_report（JSON 列以 String 存取） */
@Data
@TableName("tuning_verify_report")
public class TuningVerifyReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer round;
    private String status;
    private Integer caseTotal;
    private Integer caseDone;
    private Integer originFixed;
    private Integer regressionTotal;
    private Integer regressionPass;
    private Integer regressionFail;
    private Integer degradedCount;
    private Integer improvedCount;
    private Integer unchangedCount;
    private Integer avgElapsedBeforeMs;
    private Integer avgElapsedAfterMs;
    private String conclusion;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
