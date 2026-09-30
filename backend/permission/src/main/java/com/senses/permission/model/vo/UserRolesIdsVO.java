package com.senses.permission.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Schema(description = "用户已绑定角色id集合")
public class UserRolesIdsVO {
    @Schema(description = "可修改绑定状态功能角色id集合")
    private List<Long> roleIds;
    @Schema(description = "不可修改绑定状态功能角色id集合")
    private Set<Long> cannotRoleIds;
    @Schema(description = "可修改绑定状态数据角色id集合")
    private List<Long> dataRoleIds;
    @Schema(description = "不可修改绑定状态数据角色id集合")
    private Set<Long> cannotDataRoleIds;

}
