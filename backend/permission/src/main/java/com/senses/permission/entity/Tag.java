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
 * 标签定义表;
 * @author : wanjei
 * @date : 2025-08-26
 */
@Schema(description = "用于存储全局标签定义信息")
@TableName("tags")
@Data
public class Tag {

    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标签名称 */
    @Schema(description = "标签名称")
    @TableField("tag_name")
    private String tagName;

    /** 标签描述 */
    @Schema(description = "标签描述")
    @TableField("description")
    private String description;

    /** 是否删除 0否1是 */
    @Schema(description = "是否删除 0否1是")
    @TableField("is_deleted")
    private Integer isDeleted;

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
