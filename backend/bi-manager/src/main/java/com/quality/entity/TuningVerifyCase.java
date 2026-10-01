package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_verify_case（JSON 列以 String 存取） */
@Data
@TableName("tuning_verify_case")
public class TuningVerifyCase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String caseType;
    private String sourceChatId;
    private Long regressionCaseId;
    private String question;
    private String expected;
    private String beforeResult;
    private String afterResult;
    private Integer beforePass;
    private Integer afterPass;
    private String verdict;
    private String judgeDetail;
    private String afterRequestId;
    private Integer elapsedBeforeMs;
    private Integer elapsedAfterMs;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
