package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;


@Schema(description = "角色功能关系分页查询条件")
@Data
public class RolePermissionPageParam {

    @Parameter(description = "角色id",example = "1")
    private Long roleId;

    @Parameter(description = "过滤条件：功能权限名称、标识",example = "app")
    private String filterVal;
}
