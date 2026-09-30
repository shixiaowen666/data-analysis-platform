package com.wm.semantic.dto;

import lombok.Data;

import java.util.Set;

/**
 * 表/模型与指标覆盖关系
 */
@Data
public class TableIndicatorRelation {
    private DataSourceInfo dataSource;
    private Set<Long> coveredIndicators;

    public TableIndicatorRelation(DataSourceInfo dataSource, Set<Long> coveredIndicators) {
        this.dataSource = dataSource;
        this.coveredIndicators = coveredIndicators;
    }
}