package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.senses.permission.constant.CommonStatusEnum;
import com.senses.permission.constant.DataRoleTypeEnum;
import com.senses.permission.model.dataroleVo.DataPermissionVo;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据角色表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "数据角色表")
@TableName("data_role")
@Data
public class DataRole{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;

    /** 部门id */
    @Schema(description = "部门id")
    @TableField("dept_id")
    private Long deptId;

    /** 部门名称 */
    @Schema(description = "部门名称")
    @TableField(exist = false)
    private String deptName;
    
    /** 数据角色名称 */
    @Schema(description = "数据角色名称")
    @TableField("name")
    private String name;
    
    /** 描述 */
    @Schema(description = "描述")
    @TableField("remark")
    private String remark;
    
    /** 状态0禁用1启用2删除 */
    @Schema(description = "状态0禁用1启用2删除")
    @TableField("status")
    private Integer status;
    
    /** 创建日期 */
    @Schema(description = "创建日期")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 创建者 */
    @Schema(description = "创建者")
    @TableField("created_user")
    private String createdUser;
    
    /** 提交人 */
    @Schema(description = "提交人")
    @TableField("modify_user")
    private String modifyUser;
    
    /** 更新时间 */
    @Schema(description = "更新时间")
    @TableField("modify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

    /** 数据角色类型 0普通数据角色，1个人数据角色，用户创建时，默认增加一个个人数据角色，作为查询自身权限时使用 */
    @Schema(description = "数据角色类型 0普通数据角色，1个人数据角色，用户创建时，默认增加一个个人数据角色，作为查询自身权限时使用")
    @TableField("data_role_type")
    private Integer dataRoleType;

    @Schema(description = "数据权限列表（更新时删除的权限可从列表移除，不必回传）")
    @TableField(exist = false)
    private List<DataRolesPermission> dataPermissions;

    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField(exist = false)
    private Integer approveStatus;

    /**所属分组 */
    @Schema(description = "所属分组")
    @TableField(exist = false)
    private Long groupId;

    public static DataRole newPersonDataRole(User user) {
        DataRole dataRole = new DataRole();
        dataRole.setName(user.getName()+"@Person");
        dataRole.setDataRoleType(DataRoleTypeEnum.PERSON.getId());
        dataRole.setDeptId(user.getDeptId());
        dataRole.setStatus(CommonStatusEnum.TRUE.getId());
        dataRole.setCreatedTime(user.getCreatedTime());
        dataRole.setCreatedUser(user.getCreatedUser());
        dataRole.setModifyTime(user.getModifyTime());
        dataRole.setModifyUser(user.getModifyUser());
        dataRole.setRemark("个人私有角色");
        return dataRole;
    }

    public boolean isNewerThan(DataRole role){
        return true;
    }
    public void copyValueBy(DataRole role){
        if(!this.name.equals(role.getName())){
            this.name = role.getName();
        }
        if(!this.remark.equals(role.getRemark())){
            this.remark = role.getRemark();
        }
        this.modifyUser = role.getModifyUser();
        this.modifyTime = role.getModifyTime();
    }

 }