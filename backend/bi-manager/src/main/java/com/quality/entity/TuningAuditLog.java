package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_audit_log（JSON 列以 String 存取） */
@Data
@TableName("tuning_audit_log")
public class TuningAuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String action;
    private String operator;
    private String beforeStatus;
    private String afterStatus;
    private String detail;
    private LocalDateTime createdAt;
}
