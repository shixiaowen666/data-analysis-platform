package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 角色与部门关联表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "角色与部门关联表")
@TableName("groups_roles")
@Data
public class GroupsRoles{
    
    /** 角色id */
    @Schema(description = "角色id")
    @TableField("role_id")
    private Long roleId;
    
    /** 用户分组id */
    @Schema(description = "用户分组id")
    @TableField("group_id")
    private Long groupId;

}