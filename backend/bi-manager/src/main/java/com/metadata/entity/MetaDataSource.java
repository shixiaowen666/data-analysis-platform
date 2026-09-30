package com.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源注册表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meta_data_source")
public class MetaDataSource extends MetaBaseEntity {

    private String name;

    private String dbType;

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String defaultDb;

    private String schemaName;

    private String jdbcUrl;

    private Integer status;

    private Long tenantId;
}
