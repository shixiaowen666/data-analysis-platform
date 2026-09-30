package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 用户与角色表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "用户与角色表")
@TableName("users_roles")
@Data
public class UsersRoles{
    
    /** 用户ID */
    @Schema(description = "用户ID")
    @TableField("user_id")
    private Long userId;
    
    /** 角色ID */
    @Schema(description = "角色ID")
    @TableField("role_id")
    private Long roleId;
}