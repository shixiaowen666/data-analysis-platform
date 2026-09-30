package com.senses.permission.model.param;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "部门组授权参数")
@Data
public class DeptBindParam {
    @Schema(description = "部门id",example = "1")
    private Long deptId;
    @Schema(description = "功能角色id列表")
    private List<Long> roleIds;
    @Schema(description = "数据角色id列表")
    private List<Long> dataRoleIds;
}
