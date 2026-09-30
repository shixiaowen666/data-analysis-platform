package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据表标签条件关系配置表;
 * @author : wanjei
 * @date : 2025-08-26
 */
@Schema(description = "用于定义数据表下多个标签条件之间的逻辑组合关系")
@TableName("table_tag_relations")
@Data
public class TableTagRelation {

    /** 主键ID */
    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据表ID */
    @Schema(description = "数据表ID")
    @TableField("table_id")
    private Long tableId;

    /** 条件关系类型(OR:或关系,AND:与关系) */
    @Schema(description = "条件关系类型(OR:或关系,AND:与关系)")
    @TableField("relation_type")
    private String relationType;

    /** 创建时间 */
    @Schema(description = "创建时间")
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新时间 */
    @Schema(description = "更新时间")
    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** 是否删除(0:未删除,1:已删除) */
    @Schema(description = "是否删除(0:未删除,1:已删除)")
    @TableField("is_deleted")
    private Integer isDeleted;

    /** 数据表名称 */
    @Schema(description = "数据表名称")
    @TableField(exist = false)
    private String tableName;
}
