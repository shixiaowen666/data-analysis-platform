package com.bi.vo;

import lombok.Data;

/**
 * 数据源选项
 */
@Data
public class DataSourceOptionVO {

    private Long id;

    private String name;

    private String dbName;

    private String dbType;
}
