package com.chatbi.chat.feign.dto;

import java.util.List;
import java.util.Map;

public class GetDataSqlResponse {
    private String sql;
    private List<ColumnMeta> columns;
    private List<Map<String, Object>> records;
    private Long total;
    private Integer page;
    private Integer pageSize;

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public List<ColumnMeta> getColumns() { return columns; }
    public void setColumns(List<ColumnMeta> columns) { this.columns = columns; }
    public List<Map<String, Object>> getRecords() { return records; }
    public void setRecords(List<Map<String, Object>> records) { this.records = records; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}