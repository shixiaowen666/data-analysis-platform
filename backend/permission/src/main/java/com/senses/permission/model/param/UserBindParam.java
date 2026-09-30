package com.senses.permission.model.param;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户授权参数")
@Data
public class UserBindParam {
    @Schema(description = "用户id",example = "1")
    private Long userId;
    @Schema(description = "功能角色id列表",example = "1")
    private List<Long> roleIds;
    @Schema(description = "数据角色id列表",example = "1")
    private List<Long> dataRoleIds;
}
