package com.metadata.vo.meta;

import lombok.Data;

import java.util.List;

/**
 * 数据源选中表 VO
 */
@Data
public class MetaSelectTableVO {

    private Integer datasourceId;

    private List<String> tableNames;

    private Integer selectedCount;
}
