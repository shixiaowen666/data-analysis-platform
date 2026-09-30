package com.senses.permission.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Schema(description = "用户组已绑定角色id集合")
public class GroupRolesIdsVO {
    @Schema(description = "功能角色id集合")
    private List<Long> roleIds;
    @Schema(description = "数据角色id集合")
    private List<Long> dataRoleIds;

}
