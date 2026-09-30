package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 数据模型表
 */
@Data
@TableName("olap_data_model")
public class OlapDataModelDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long sourceId;
    private Long factTableId;
    private String description;
    private Integer status;
    private Long tenantId;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
