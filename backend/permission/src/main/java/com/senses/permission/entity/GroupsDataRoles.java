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
@TableName("groups_data_roles")
@Data
public class GroupsDataRoles{
    
    /** 数据角色id */
    @Schema(description = "数据角色id")
    @TableField("data_role_id")
    private Long dataRoleId;
    
    /** 用户分组id */
    @Schema(description = "用户分组id")
    @TableField("group_id")
    private Long groupId;

}