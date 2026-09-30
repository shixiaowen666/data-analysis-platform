package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Schema(description = "用户分页查询条件")
@Data
public class UserPageParam {
    @Schema(description = "用户id，姓名，用户名")
    private String filterVal;
    @Schema(description = "部门id")
    private Integer deptId;
    @Schema(description = "状态0禁用1启用")
    private Integer status;
}
