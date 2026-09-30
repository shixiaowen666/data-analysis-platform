package com.senses.permission.model.param;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.senses.permission.entity.Permission;
import com.senses.permission.model.TreeData;
import lombok.Data;

import java.util.Date;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色表新增修改入参
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "角色表新增修改入参")
@Data
public class RoleParam {
    
    /** ID */
    @Schema(description = "ID")
    private Long id;
    
    /** 角色名称 */
    @Schema(description = "角色名称")
    private String name;

    /** 角色中文名称 */
    @Schema(description = "角色中文名称")
    private String cnName;

    /** 角色名称 */
    @Schema(description = "所属应用id")
    private Long appId;
    
    /** 角色类型 0应用管理员1应用成员 */
    @Schema(description = "角色类型 0应用管理员1应用成员")
    private Integer type;
    
    /** 状态0禁用1启用2删除 */
    @Schema(description = "状态0禁用1启用2删除")
    private Integer status;

    /** 角色权限id集合*/
    @Schema(description = "角色权限id集合")
    private List<Long> permissionIds;
}