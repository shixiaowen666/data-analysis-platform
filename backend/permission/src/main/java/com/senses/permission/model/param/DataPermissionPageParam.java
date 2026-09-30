package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "数据权限分页查询条件")
@Data
public class DataPermissionPageParam {
    @Schema(description = "过滤值 表名，列名",example = "*")
    private String filterVal;
}
