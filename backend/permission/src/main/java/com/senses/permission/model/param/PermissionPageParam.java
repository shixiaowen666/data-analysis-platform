package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "功能权限分页查询条件")
@Data
public class PermissionPageParam {
    @Schema(description = "过滤值 权限名称，标识",example = "add")
    private String filterVal;
    @Schema(description = "应用id",example = "1")
    private Long appId;
    @Schema(description = "功能类型 0菜单menu，1按钮button",example = "0")
    private Integer permissionType;
}
