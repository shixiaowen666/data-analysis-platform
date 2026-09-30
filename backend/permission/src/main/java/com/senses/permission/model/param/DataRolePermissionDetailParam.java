package com.senses.permission.model.param;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "数据角色权限详情参数")
public class DataRolePermissionDetailParam {
    @Schema(description = "数据角色Id")
    private Long id;
    @Schema(description = "过滤值 表名，列名")
    private String filterVal;
    @Schema(description = "是否查询审批中数据权限")
    private Integer selectApprove;
    @Schema(description = "引擎id")
    private List<Long> engineIds;
    @Schema(description = "数据库名称")
    private List<String> databaseNames;
    @Schema(description = "操作权限")
    private List<Integer> actions;
}
