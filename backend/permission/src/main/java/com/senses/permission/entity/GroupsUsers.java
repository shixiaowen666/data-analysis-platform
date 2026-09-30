package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 用户组与用户关系表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "用户组与用户关系表")
@TableName("groups_users")
@Data
public class GroupsUsers{
    
    /** 用户组ID */
    @Schema(description = "用户组ID")
    @TableField("group_id")
    private Long groupId;
    
    /** 用户ID */
    @Schema(description = "用户ID")
    @TableField("user_id")
    private Long userId;

}