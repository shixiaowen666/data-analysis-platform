package com.metadata.dto.meta;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 数据源新增/修改参数
 */
@Data
public class MetaDataSourceSaveDTO {

    private Long id;

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    private Integer port;

    @NotBlank(message = "数据库不能为空")
    private String defaultDb;

    private String schemaName;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    /**
     * JDBC URL，前端可传入确认值；为空时后端自动生成
     */
    private String jdbcUrl;

    private Long tenantId;
}
