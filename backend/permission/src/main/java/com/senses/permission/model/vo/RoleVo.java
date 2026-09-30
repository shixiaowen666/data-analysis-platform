package com.senses.permission.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.senses.permission.entity.Permission;
import lombok.Data;

import java.util.Date;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "角色表VO")
@Data
public class RoleVo {
    
    /** ID */
    @Schema(description = "ID")
    private Long id;

    /** 角色名称 */
    @Schema(description = "应用id")
    private Long appId;

    /** 角色名称 */
    @Schema(description = "角色名称")
    private String name;

    /** 角色中文名称 */
    @Schema(description = "角色中文名称")
    private String cnName;
    
    /** 角色类型 0应用管理员1应用成员 */
    @Schema(description = "角色类型 0应用管理员1应用成员")
    private Integer type;
    
    /** 状态0禁用1启用2删除 */
    @Schema(description = "状态0禁用1启用2删除")
    private Integer status;
    
    /** 创建人 */
    @Schema(description = "创建人")
    private String createdUser;
    
    /** 创建日期 */
    @Schema(description = "创建日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 修改人 */
    @Schema(description = "修改人")
    private String modifyUser;
    
    /** 修改时间 */
    @Schema(description = "修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

    /** 角色权限集合 */
    @Schema(description = "角色权限集合")
    private List<Permission> permissions;
}