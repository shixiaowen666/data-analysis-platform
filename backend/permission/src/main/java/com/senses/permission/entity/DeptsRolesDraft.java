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
 * 角色与部门关联草稿表;
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Schema(description = "角色与部门关联草稿表")
@TableName("depts_roles_draft")
@Data
public class DeptsRolesDraft{
    
    /** 角色id */
    @Schema(description = "角色id")
    @TableField("role_id")
    private Long roleId;
    
    /** 部门id */
    @Schema(description = "部门id")
    @TableField("dept_id")
    private Long deptId;
}