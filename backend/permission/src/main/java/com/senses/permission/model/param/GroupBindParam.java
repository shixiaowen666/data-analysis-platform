package com.senses.permission.model.param;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户组授权参数")
@Data
public class GroupBindParam {
    @Schema(description = "用户组id",example = "1")
    private Long groupId;
    @Schema(description = "功能角色id列表")
    private List<Long> roleIds;
    @Schema(description = "数据角色id列表")
    private List<Long> dataRoleIds;
}
