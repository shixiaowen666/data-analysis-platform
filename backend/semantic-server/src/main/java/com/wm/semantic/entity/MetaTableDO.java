package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 元数据-表
 */
@Data
@TableName("meta_table")
public class MetaTableDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceId;
    private Long tenantId;
    private String schemaName;
    private String tableName;
    private String tableComment;
    private Long rowCountEstimate;
    private Date lastCollectedAt;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
