package com.wm.semantic.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

public class GetDataSqlResponse {
    private String sql;
    private List<ColumnMeta> columns;
    private List<Map<String, Object>> records;
    private Long total;
    private Integer page;
    private Integer pageSize;

    // ==== 同环比 merge 用的内部信息，不返回给前端 ====
    @JsonIgnore
    private String dateFieldKey;
    @JsonIgnore
    private List<String> dimensionKeys;
    @JsonIgnore
    private Map<Long, String> fieldKeyMap;

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
    public String getDateFieldKey() { return dateFieldKey; }
    public void setDateFieldKey(String dateFieldKey) { this.dateFieldKey = dateFieldKey; }
    public List<String> getDimensionKeys() { return dimensionKeys; }
    public void setDimensionKeys(List<String> dimensionKeys) { this.dimensionKeys = dimensionKeys; }
    public Map<Long, String> getFieldKeyMap() { return fieldKeyMap; }
    public void setFieldKeyMap(Map<Long, String> fieldKeyMap) { this.fieldKeyMap = fieldKeyMap; }
}