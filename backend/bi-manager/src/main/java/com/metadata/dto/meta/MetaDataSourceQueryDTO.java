package com.metadata.dto.meta;

import lombok.Data;

/**
 * 数据源列表查询参数
 */
@Data
public class MetaDataSourceQueryDTO {

    /**
     * 按数据源名称模糊匹配（文档参数名）
     */
    private String keyword;

    /**
     * @deprecated 兼容旧参数，请使用 keyword
     */
    private String name;

    private String dbType;

    private Integer status;

    private Long tenantId;

    /**
     * 页码，从 1 开始
     */
    private Integer page = 1;

    private Integer pageSize = 10;
}
