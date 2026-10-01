package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 问答质量管理 · chat_error_type_dict（JSON 列以 String 存取） */
@Data
@TableName("chat_error_type_dict")
public class ChatErrorTypeDict implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String parentCode;
    private String name;
    private String scope;
    private String pipelineStage;
    private Integer sort;
    private Integer enabled;
}
