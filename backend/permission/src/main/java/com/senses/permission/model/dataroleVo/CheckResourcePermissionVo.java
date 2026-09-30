package com.senses.permission.model.dataroleVo;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "待校验权限参数VO")
public class CheckResourcePermissionVo {
    @Schema(description = "用户名")
    private String userName;
    @Schema(description = "请求鉴权参数列表(系统编码->资源类型->对象id->访问权限->下载权限)")
    private List<String> requestPrivileges;
    
}
