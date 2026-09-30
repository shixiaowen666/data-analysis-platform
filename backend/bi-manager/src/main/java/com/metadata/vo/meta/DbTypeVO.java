package com.metadata.vo.meta;

import lombok.Data;

/**
 * 数据库类型 VO
 */
@Data
public class DbTypeVO {

    private Integer id;

    private String name;

    private String jdbcPrefix;

    private Integer defaultPort;
}
