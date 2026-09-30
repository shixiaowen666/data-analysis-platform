package com.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 元数据-字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meta_column")
public class MetaColumn extends MetaBaseEntity {

    private Long tableId;

    private Long tenantId;

    private String columnName;

    private String dataType;

    private Integer isNullable;

    private Integer isPk;

    private String defaultValue;

    private String columnComment;

    private Integer ordinal;
}
