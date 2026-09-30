package com.bi.vo;

import lombok.Data;

/**
 * JOIN 配置详情
 */
@Data
public class DataModelJoinVO {

    private Long id;

    private Long dimTableId;

    private String dimTableName;

    private String joinType;

    private String factFkColumn;

    private String dimPkColumn;
}
