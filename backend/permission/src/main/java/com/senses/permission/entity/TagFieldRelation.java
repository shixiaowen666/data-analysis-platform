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
 * 标签字段关系表;
 * @author : wanjie
 * @date : 2025-08-26
 */
@Schema(description = "用于存储标签与数据表的关联关系")
@TableName("tag_field_relation")
@Data
public class TagFieldRelation {

    /** 主键ID */
    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标签ID */
    @Schema(description = "标签ID")
    @TableField("tag_id")
    private Long tagId;

    /** 表ID */
    @Schema(description = "表ID")
    @TableField("table_id")
    private Long tableId;

    /** 表名 */
    @Schema(description = "表名")
    @TableField("table_name")
    private String tableName;

    /** 字段名 */
    @Schema(description = "字段名")
    @TableField("column_name")
    private String columnName;


    /** 创建时间 */
    @Schema(description = "创建时间")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;

    /** 更新时间 */
    @Schema(description = "更新时间")
    @TableField("updated_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedTime;

}
