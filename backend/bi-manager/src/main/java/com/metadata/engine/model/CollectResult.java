package com.metadata.engine.model;

import lombok.Data;

/**
 * 采集结果统计
 */
@Data
public class CollectResult {

    private boolean success;

    private String errorMessage;

    private int newTableCount;

    private int updatedTableCount;

    private int newColumnCount;

    private int updatedColumnCount;

    private int totalTableCount;

    private int totalColumnCount;

    private int deletedTableCount;

    private int deletedColumnCount;

    private long durationSeconds;

    public void incrementNewTable() {
        newTableCount++;
    }

    public void incrementUpdatedTable() {
        updatedTableCount++;
    }

    public void incrementNewColumn() {
        newColumnCount++;
    }

    public void incrementUpdatedColumn() {
        updatedColumnCount++;
    }

    public void incrementDeletedColumn() {
        deletedColumnCount++;
    }

    public void addDeletedColumnCount(int count) {
        deletedColumnCount += count;
    }

    public void addTotalTable(int count) {
        totalTableCount += count;
    }

    public void addTotalColumn(int count) {
        totalColumnCount += count;
    }
}
