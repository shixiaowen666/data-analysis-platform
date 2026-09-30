package com.metadata.vo.meta;

import lombok.Data;

/**
 * 元数据字段 VO
 */
@Data
public class MetaColumnVO {

    private Long id;

    private Long tableId;

    private Integer ordinal;

    private String columnName;

    private String dataType;

    private Integer isNullable;

    private Integer isPk;

    private String columnComment;
}
