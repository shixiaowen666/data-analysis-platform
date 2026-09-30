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
 * 用户和数据角色的关系草稿表;
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Schema(description = "用户和数据角色的关系草稿表")
@TableName("users_data_roles_draft")
@Data
public class UsersDataRolesDraft{
    
    /** 用户id */
    @Schema(description = "用户id")
    @TableField("user_id")
    private Long userId;
    
    /** 数据角色id */
    @Schema(description = "数据角色id")
    @TableField("data_role_id")
    private Long dataRoleId;
}