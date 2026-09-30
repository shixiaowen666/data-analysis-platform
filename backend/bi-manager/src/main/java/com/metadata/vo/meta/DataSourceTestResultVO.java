package com.metadata.vo.meta;

import lombok.Data;

/**
 * 数据源连接测试结果
 */
@Data
public class DataSourceTestResultVO {

    private Boolean success;

    private String message;

    private String dbVersion;

    private String databaseName;

    private Integer tableCount;
}
