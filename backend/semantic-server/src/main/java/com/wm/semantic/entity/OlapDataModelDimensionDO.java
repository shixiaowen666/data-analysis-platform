package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 模型-维度关联表
 */
@Data
@TableName("olap_data_model_dimension")
public class OlapDataModelDimensionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long dimTableId;
    private String joinType;
    private String factFkColumn;
    private String dimPkColumn;
    private Long tenantId;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
