package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户标签值表;
 * @author : wanjei
 * @date : 2025-08-26
 */
@Schema(description = "用于存储用户与标签值的关联关系")
@TableName("user_tag_values")
@Data
public class UserTagValue {

    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    @Schema(description = "用户名")
    @TableField("username")
    private String username;

    /** 标签ID */
    @Schema(description = "标签ID")
    @TableField("tag_id")
    private Long tagId;

    /** 标签名称 */
    @Schema(description = "标签名称")
    @TableField("tag_name")
    private String tagName;

    /** 标签值 */
    @Schema(description = "标签值")
    @TableField("tag_value")
    private String tagValue;

}
