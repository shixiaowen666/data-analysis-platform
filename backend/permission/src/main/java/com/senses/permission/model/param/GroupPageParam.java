package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Schema(description = "用户组分页查询条件")
@Data
public class GroupPageParam {
    @Schema(description = "角色id，组名称，创建人")
    private String filterVal;
    @Schema(description = "状态0禁用1启用")
    private Integer status;
}
