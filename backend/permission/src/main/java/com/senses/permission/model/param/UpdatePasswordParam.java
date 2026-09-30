package com.senses.permission.model.param;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.media.Schema;

@Slf4j
@Data
@Schema(description = "修改密码参数")
public class UpdatePasswordParam {
    @Schema(description = "新密码")
    private String newPassword;
    @Schema(description = "重复新密码")
    private String rePassword;
}
