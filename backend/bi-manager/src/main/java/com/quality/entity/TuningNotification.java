package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · tuning_notification（JSON 列以 String 存取） */
@Data
@TableName("tuning_notification")
public class TuningNotification implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private Long taskId;
    private String title;
    private String content;
    private String targetRole;
    private Integer readFlag;
    private LocalDateTime createdAt;
}
