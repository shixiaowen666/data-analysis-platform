package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 数据角色与部门关联表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "数据角色与部门关联表")
@TableName("depts_data_roles")
@Data
public class DeptsDataRoles{
    
    /** 数据角色id */
    @Schema(description = "数据角色id")
    @TableField("data_role_id")
    private Long dataRoleId;
    
    /** 部门id */
    @Schema(description = "部门id")
    @TableField("dept_id")
    private Long deptId;

}