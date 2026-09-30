package com.senses.permission.model.dataroleVo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "数据角色列表简单VO")
public class DataRoleListVo {
    @Schema(description = "角色id",example = "1212")
    private Long id;
    @Schema(description = "角色名称")
    private String name;
    @Schema(description = "角色中文名称")
    private String remark;
    @Schema(description = "部门id",example = "1212")
    private Long deptId;
    @Schema(description = "创建用户")
    private String createdUser;
}
