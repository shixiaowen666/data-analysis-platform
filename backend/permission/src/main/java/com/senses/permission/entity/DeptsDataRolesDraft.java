package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;


 /**
 * 部门和数据角色关系草稿表;
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Schema(description = "部门和数据角色关系草稿表")
@TableName("depts_data_roles_draft")
@Data
public class DeptsDataRolesDraft{
    
    /** 角色id */
    @Schema(description = "角色id")
    @TableField("data_role_id")
    private Long dataRoleId;
    
    /** 部门id */
    @Schema(description = "部门id")
    @TableField("dept_id")
    private Long deptId;

    /** 申请人类型：0用户管理申请，1个人角色申请 */
    @Schema(description = "申请人类型：0部门管理申请，1个人申请")
    @TableField("applicant_type")
    private Integer applicantType;
}