package com.senses.permission.model.param;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotEmpty;
import io.swagger.v3.oas.annotations.media.Schema;

@Slf4j
@Data
@Schema(description = "登录前修改密码参数")
public class LoginPreUpdatePasswordParam {
    @Schema(description = "用户名")
    @NotEmpty
    private String username;
    @Schema(description = "旧密码")
    @NotEmpty
    private String oldPassword;
    @Schema(description = "新密码")
    @NotEmpty
    private String newPassword;
    @Schema(description = "重复新密码")
    @NotEmpty
    private String rePassword;
}
