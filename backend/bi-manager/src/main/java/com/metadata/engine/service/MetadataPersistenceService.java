package com.metadata.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.metadata.engine.model.CollectContext;
import com.metadata.engine.model.CollectResult;
import com.metadata.engine.model.ColumnMetadata;
import com.metadata.engine.model.TableMetadata;
import com.metadata.entity.MetaColumn;
import com.metadata.entity.MetaDataSource;
import com.metadata.entity.MetaTable;
import com.metadata.service.MetaColumnService;
import com.metadata.service.MetaTableService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 元数据持久化：增量采集，表/字段存在则更新，不存在则新增；不删除历史元数据
 */
@Service
@RequiredArgsConstructor
public class MetadataPersistenceService {

    private final MetaTableService metaTableService;
    private final MetaColumnService metaColumnService;

    @Transactional(rollbackFor = Exception.class)
    public void saveTableAndColumns(CollectContext context,
                                    TableMetadata tableMetadata,
                                    List<ColumnMetadata> columns,
                                    CollectResult result) {
        MetaDataSource dataSource = context.getDataSource();
        LocalDateTime now = LocalDateTime.now();

        MetaTable table = metaTableService.getOne(new LambdaQueryWrapper<MetaTable>()
                .eq(MetaTable::getSourceId, dataSource.getId())
                .eq(MetaTable::getSchemaName, tableMetadata.getSchemaName())
                .eq(MetaTable::getTableName, tableMetadata.getTableName()));

        if (table == null) {
            table = new MetaTable();
            table.setSourceId(dataSource.getId());
            table.setTenantId(dataSource.getTenantId());
            table.setSchemaName(tableMetadata.getSchemaName());
            table.setTableName(tableMetadata.getTableName());
            table.setTableComment(tableMetadata.getTableComment());
            table.setRowCountEstimate(tableMetadata.getRowCountEstimate());
            table.setLastCollectedAt(now);
            metaTableService.save(table);
            result.incrementNewTable();
        } else {
            boolean tableChanged = isTableChanged(table, tableMetadata);
            table.setTableComment(tableMetadata.getTableComment());
            table.setRowCountEstimate(tableMetadata.getRowCountEstimate());
            table.setLastCollectedAt(now);
            metaTableService.updateById(table);
            if (tableChanged) {
                result.incrementUpdatedTable();
            }
        }

        result.addTotalTable(1);
        for (ColumnMetadata columnMetadata : columns) {
            saveColumn(dataSource, table, columnMetadata, result);
        }
        result.addTotalColumn(columns.size());
    }

    private void saveColumn(MetaDataSource dataSource,
                            MetaTable table,
                            ColumnMetadata columnMetadata,
                            CollectResult result) {
        MetaColumn column = metaColumnService.getOne(new LambdaQueryWrapper<MetaColumn>()
                .eq(MetaColumn::getTableId, table.getId())
                .eq(MetaColumn::getColumnName, columnMetadata.getColumnName()));

        if (column == null) {
            column = new MetaColumn();
            column.setTableId(table.getId());
            column.setTenantId(dataSource.getTenantId());
            column.setColumnName(columnMetadata.getColumnName());
            fillColumnFields(column, columnMetadata);
            metaColumnService.save(column);
            result.incrementNewColumn();
            return;
        }

        if (isColumnChanged(column, columnMetadata)) {
            fillColumnFields(column, columnMetadata);
            metaColumnService.updateById(column);
            result.incrementUpdatedColumn();
        }
    }

    private boolean isTableChanged(MetaTable existing, TableMetadata latest) {
        return !StringUtils.equals(StringUtils.defaultString(existing.getTableComment()),
                StringUtils.defaultString(latest.getTableComment()))
                || !Objects.equals(existing.getRowCountEstimate(), latest.getRowCountEstimate());
    }

    private void fillColumnFields(MetaColumn column, ColumnMetadata columnMetadata) {
        column.setDataType(columnMetadata.getDataType());
        column.setIsNullable(columnMetadata.getIsNullable());
        column.setIsPk(columnMetadata.getIsPk());
        column.setDefaultValue(columnMetadata.getDefaultValue());
        column.setColumnComment(columnMetadata.getColumnComment());
        column.setOrdinal(columnMetadata.getOrdinal());
    }

    private boolean isColumnChanged(MetaColumn existing, ColumnMetadata latest) {
        return !Objects.equals(existing.getDataType(), latest.getDataType())
                || !Objects.equals(existing.getIsNullable(), latest.getIsNullable())
                || !Objects.equals(existing.getIsPk(), latest.getIsPk())
                || !StringUtils.equals(StringUtils.defaultString(existing.getDefaultValue()),
                StringUtils.defaultString(latest.getDefaultValue()))
                || !StringUtils.equals(StringUtils.defaultString(existing.getColumnComment()),
                StringUtils.defaultString(latest.getColumnComment()))
                || !Objects.equals(existing.getOrdinal(), latest.getOrdinal());
    }
}

