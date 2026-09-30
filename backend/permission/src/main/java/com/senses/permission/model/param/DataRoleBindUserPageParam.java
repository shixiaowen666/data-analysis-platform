package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "角色绑定用户分页查询条件")
@Data
public class DataRoleBindUserPageParam {

    @Parameter(description = "角色id",example = "1")
    private Long id;

    @Schema(description = "过滤值 角色id，名称，创建人",example = "管理员")
    private String filterVal;

}
