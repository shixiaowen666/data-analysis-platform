package com.metadata.vo.meta;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源列表/详情 VO
 */
@Data
public class MetaDataSourceVO {

    private Long id;

    private String name;

    private String dbType;

    private String host;

    private Integer port;

    private String defaultDb;

    private String schemaName;

    private String username;

    private String jdbcUrl;

    private Integer status;

    private String statusName;

    private String displayDbSchema;

    private Long tenantId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
