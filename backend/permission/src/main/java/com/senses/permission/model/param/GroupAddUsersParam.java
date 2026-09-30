package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户组添加成员参数")
public class GroupAddUsersParam {
    @Schema(description = "用户组id")
    private Long groupId;
    @Schema(description = "用户id集合")
    private List<Long> userIds;
}
