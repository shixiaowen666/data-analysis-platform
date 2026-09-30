package com.senses.permission.model.dataroleVo;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "待校验权限参数VO")
public class CheckPermissionVo {
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "请求鉴权参数列表(源->库->表->列->操作)")
    private List<String> requestPrivileges;

}
