package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 问答质量管理 · tuning_rule（JSON 列以 String 存取） */
@Data
@TableName("tuning_rule")
public class TuningRule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String errorType;
    private String name;
    private String conditionDesc;
    private String assetType;
    private String field;
    private String template;
    private String confidence;
    private Integer priority;
    private Integer enabled;
}
