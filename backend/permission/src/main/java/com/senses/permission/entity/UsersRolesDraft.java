package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 用户和功能角色关系草稿表;
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Schema(description = "用户和功能角色关系草稿表")
@TableName("users_roles_draft")
@Data
public class UsersRolesDraft{
    
    /** 用户ID */
    @Schema(description = "用户ID")
    @TableField("user_id")
    private Long userId;
    
    /** 角色ID */
    @Schema(description = "角色ID")
    @TableField("role_id")
    private Long roleId;

    /** 申请人类型：0用户管理申请，1个人角色申请 */
    @Schema(description = "申请人类型：0用户管理申请，1个人角色申请")
    @TableField("applicant_type")
    private Integer applicantType;

}