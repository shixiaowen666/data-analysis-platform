package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · regression_case（JSON 列以 String 存取） */
@Data
@TableName("regression_case")
public class RegressionCase implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String aiBodyCode;
    private Long tenantId;
    private String question;
    private String expected;
    private String source;
    private String sourceChatId;
    private String tags;
    private Integer enabled;
    private LocalDateTime lastPassAt;
    private Integer failCount;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
