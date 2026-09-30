package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "角色分页查询条件")
@Data
public class RolePageParam {
    @Schema(description = "过滤值 角色id，名称，创建人",example = "管理员")
    private String filterVal;
}
