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
 * 角色与权限管理关联草稿表;
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Schema(description = "角色与权限管理关联草稿表")
@TableName("roles_permission_draft")
@Data
public class RolesPermissionDraft{
    
    /** 角色ID */
    @Schema(description = "角色ID")
    @TableField("role_id")
    private Long roleId;
    
    /** 权限ID */
    @Schema(description = "权限ID")
    @TableField("permission_id")
    private Long permissionId;
}