package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.vo.RoleInfoVO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.management.relation.RoleInfo;
import java.util.Date;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "用户表")
@TableName("user")
@Data
public class User{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 邮箱 */
    @Schema(description = "邮箱")
    @TableField("email")
    private String email;
    
    /** 密码 */
    @Schema(description = "密码")
    @TableField("password")
    private String password;
    
    /** 用户名 */
    @Schema(description = "用户名")
    @TableField("username")
    private String username;

     /** 姓名 */
     @Schema(description = "姓名")
     @TableField("name")
     private String name;
    
    /** 最后修改密码的日期 */
    @Schema(description = "最后修改密码的日期")
    @TableField("last_password_reset_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastPasswordResetTime;
    
    /** 手机号 */
    @Schema(description = "手机号")
    @TableField("phone")
    private String phone;
    
    /** 部门id */
    @Schema(description = "部门id")
    @TableField("dept_id")
    private Long deptId;

     /** 部门名称 */
     @Schema(description = "部门名称")
     @TableField(exist = false)
     private String deptName;

    /** 是否管理员 0否1是 */
    @Schema(description = "是否管理员 0否1是")
    @TableField("is_admin")
    private Integer isAdmin;
    
    /** 状态 0禁用1启用2已删除 */
    @Schema(description = "状态 0禁用1启用2已删除（删除状态不会返给前端）")
    @TableField("status")
    private Integer status;

    /** 用户来源 */
    @Schema(description = "状态 0外部同步，1内部创建")
    @TableField("source")
    private Integer source;

    /** SSO外部用户标识 */
    @Schema(description = "SSO外部用户标识")
    @TableField("sso_id")
    private String ssoId;

    /** 创建时间 */
    @Schema(description = "创建时间")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 创建者 */
    @Schema(description = "创建者")
    @TableField("created_user")
    private String createdUser;
    
    /** 更新者 */
    @Schema(description = "更新者")
    @TableField("modify_user")
    private String modifyUser;
    
    /** 更新时间 */
    @Schema(description = "更新时间")
    @TableField("modify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

     /** 授权方式0个人授权1部门授权2用户组授权*/
     @Schema(description = "授权方式0个人授权1部门授权2用户组授权")
     @TableField(exist = false)
     private Integer bindType;

     /** 授权对象*/
     @Schema(description = "授权对象")
     @TableField(exist = false)
     private String bindTarget;

    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField(exist = false)
    private Integer approveStatus;

    /** 用户部门信息 */
    @Schema(description = "用户部门信息")
    @TableField(exist = false)
    private List<Dept> depts;

    /** 用户组信息 */
    @Schema(description = "用户组信息")
    @TableField(exist = false)
    private List<Group> groups;

    /** 权限 */
    @Schema(description = "权限")
    @TableField(exist = false)
    private List<TreeData<Permission>> permissionTree;

    /** 用户功能角色信息 */
    @Schema(description = "用户功能角色信息")
    @TableField(exist = false)
    private List<RoleInfoVO> roleInfos;

    /** 用户功能角色信息 */
    @Schema(description = "用户数据角色id")
    @TableField(exist = false)
    private List<Long> dataRoleIds;

}