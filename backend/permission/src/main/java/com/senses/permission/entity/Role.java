package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.senses.permission.model.TreeData;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "角色表")
@TableName("role")
@Data
public class Role{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;

    /** 应用id */
    @Schema(description = "应用id")
    @TableField("app_id")
    private Long appId;

    /** 角色名称 */
    @Schema(description = "应用名称")
    @TableField(exist = false)
    private String appName;

    /** 角色名称 */
    @Schema(description = "角色名称")
    @TableField("name")
    private String name;

    /** 角色中文名称 */
    @Schema(description = "角色中文名称")
    @TableField("cn_name")
    private String cnName;
    
    /** 角色类型 0应用管理员1应用成员 */
    @Schema(description = "角色类型 0应用管理员1应用成员")
    @TableField("type")
    private Integer type;
    
    /** 状态0禁用1启用2删除 */
    @Schema(description = "状态0禁用1启用2删除")
    @TableField("status")
    private Integer status;
    
    /** 创建人 */
    @Schema(description = "创建人")
    @TableField("created_user")
    private String createdUser;
    
    /** 创建日期 */
    @Schema(description = "创建日期")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 修改人 */
    @Schema(description = "修改人")
    @TableField("modify_user")
    private String modifyUser;
    
    /** 修改时间 */
    @Schema(description = "修改时间")
    @TableField("modify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField(exist = false)
    private Integer approveStatus;

    /** 角色权限树列表 */
    @Schema(description = "角色权限树列表")
    @TableField(exist = false)
    private List<TreeData<Permission>> rolePermissions;
}