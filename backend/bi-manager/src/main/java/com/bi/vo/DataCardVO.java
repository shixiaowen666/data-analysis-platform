package com.bi.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据结果卡片
 */
@Data
public class DataCardVO {

    /**
     * 查询标题（原始问句）
     */
    private String queryText;

    /**
     * 维度行
     */
    private List<DimensionRowVO> dimensions;

    /**
     * 指标行
     */
    private List<MetricRowVO> metrics;

    /**
     * 筛选器行
     */
    private List<FilterRowVO> filters;

    /**
     * 数据表格行
     */
    private List<Map<String, Object>> tableData;

    /**
     * 表格列头
     */
    private List<ColumnHeaderVO> columns;

    /**
     * 分页信息
     */
    private PaginationVO pagination;

    /**
     * 生成的SQL
     */
    private String generatedSql;
}
