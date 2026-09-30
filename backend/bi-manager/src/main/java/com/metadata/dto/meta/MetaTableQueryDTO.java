package com.metadata.dto.meta;

import lombok.Data;

/**
 * 元数据表列表查询参数
 */
@Data
public class MetaTableQueryDTO {

    private Long sourceId;

    private String tableName;

    private Integer page = 1;

    private Integer pageSize = 10;
}
