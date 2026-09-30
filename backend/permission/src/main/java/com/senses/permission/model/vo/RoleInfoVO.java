package com.senses.permission.model.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户功能角色信息")
@Data
public class RoleInfoVO {
    /**
     * 应用编码
     */
    @Schema(description = "应用编码")
    private String appCode;
    /**
     * 角色Id
     */
    @Schema(description = "角色Id")
    private Long roleId;
    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    private String roleName;
    /**
     * 角色中文名称
     */
    @Schema(description = "角色中文名称")
    private String roleCnName;
    /**
     * 角色类型 0应用管理员1应用成员
     */
    @Schema(description = "角色类型 0应用管理员1应用成员")
    private Integer roleType;
}
