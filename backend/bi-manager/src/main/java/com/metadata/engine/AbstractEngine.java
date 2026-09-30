package com.metadata.engine;

import com.common.exception.BizException;
import com.common.result.PageResult;
import com.metadata.dto.meta.RemoteTableQueryDTO;
import com.metadata.engine.model.CollectContext;
import com.metadata.engine.model.CollectResult;
import com.metadata.engine.model.ColumnMetadata;
import com.metadata.engine.model.TableMetadata;
import com.metadata.engine.service.MetadataPersistenceService;
import com.metadata.engine.support.CollectLogWriter;
import com.metadata.entity.MetaDataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 元数据采集引擎模板：统一流程，子类实现各数据库的读取逻辑
 */
public abstract class AbstractEngine implements RemoteTableQueryEngine {

    @Autowired
    private MetadataPersistenceService metadataPersistenceService;

    @Override
    public CollectResult collect(CollectContext context) {
        CollectLogWriter logWriter = context.getLogWriter();
        MetaDataSource dataSource = context.getDataSource();
        CollectResult result = new CollectResult();
        LocalDateTime startedAt = LocalDateTime.now();

        try (Connection connection = openConnection(dataSource)) {
            String collectLabel = context.getRequest().isFullCollect() ? "全库采集" : "选表采集";
            logWriter.info("开始" + collectLabel + " " + dataSource.getDefaultDb());
            logWriter.info("连接数据源 " + dataSource.getHost() + ":" + dataSource.getPort()
                    + " (" + connection.getMetaData().getDatabaseProductName()
                    + " " + connection.getMetaData().getDatabaseProductVersion() + ")");

            List<TableMetadata> tables = fetchTables(connection, context);
            logWriter.info("读取 INFORMATION_SCHEMA.TABLES，共发现 " + tables.size() + " 张表");

            if (context.getRequest().isSelectCollect()) {
                tables = filterSelectedTables(tables, context.getRequest().getTableNames(), logWriter);
                logWriter.info("选表采集，实际处理 " + tables.size() + " 张表");
            }

            int totalColumnCount = 0;
            for (TableMetadata table : tables) {
                List<ColumnMetadata> columns = fetchColumns(connection, context, table);
                totalColumnCount += columns.size();
                metadataPersistenceService.saveTableAndColumns(context, table, columns, result);
            }

            logWriter.info("读取 INFORMATION_SCHEMA.COLUMNS，共 " + totalColumnCount + " 个字段");
            logWriter.info("表变更：新增 " + result.getNewTableCount() + " 张，修改 "
                    + result.getUpdatedTableCount() + " 张（增量采集，不删除历史元数据）");
            logWriter.info("字段变更：新增 " + result.getNewColumnCount() + " 个，修改 "
                    + result.getUpdatedColumnCount() + " 个（增量采集，不删除历史元数据）");
            logWriter.info("写入 meta_table 完成，共 " + result.getTotalTableCount() + " 条");
            logWriter.info("写入 meta_column 完成，共 " + result.getTotalColumnCount() + " 条");

            long durationSeconds = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
            logWriter.success("采集完成，耗时 " + durationSeconds + "s");

            result.setSuccess(true);
            result.setDurationSeconds(durationSeconds);
            return result;
        } catch (Exception ex) {
            logWriter.error("采集失败：" + ex.getMessage());
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            result.setDurationSeconds(Duration.between(startedAt, LocalDateTime.now()).getSeconds());
            return result;
        }
    }

    protected List<TableMetadata> filterSelectedTables(List<TableMetadata> tables,
                                                       List<String> tableNames,
                                                       CollectLogWriter logWriter) {
        Set<String> selected = tableNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        List<TableMetadata> filtered = tables.stream()
                .filter(table -> selected.contains(table.getTableName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        Set<String> exists = filtered.stream()
                .map(table -> table.getTableName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        for (String tableName : selected) {
            if (!exists.contains(tableName)) {
                logWriter.warn("表 " + tableName + " 在目标库中不存在，已跳过");
            }
        }
        return filtered;
    }

    protected abstract Connection openConnection(MetaDataSource dataSource) throws Exception;

    protected abstract List<TableMetadata> fetchTables(Connection connection, CollectContext context) throws Exception;

    protected abstract List<ColumnMetadata> fetchColumns(Connection connection,
                                                         CollectContext context,
                                                         TableMetadata table) throws Exception;

    @Override
    public PageResult<TableMetadata> pageRemoteTables(MetaDataSource dataSource, RemoteTableQueryDTO query) {
        RemoteTableQueryDTO params = query != null ? query : new RemoteTableQueryDTO();
        int pageNo = params.resolvedPage();
        int pageSize = params.resolvedPageSize();
        String keyword = StringUtils.trimToNull(params.getTableName());
        int offset = (pageNo - 1) * pageSize;

        try (Connection connection = openConnection(dataSource)) {
            long total = countRemoteTables(connection, dataSource, keyword);
            List<TableMetadata> records = fetchRemoteTablesPage(connection, dataSource, keyword, offset, pageSize);
            return new PageResult<TableMetadata>(
                    total,
                    records,
                    Long.valueOf(pageNo),
                    Long.valueOf(pageSize));
        } catch (Exception ex) {
            throw new BizException("读取远程表列表失败：" + ex.getMessage());
        }
    }

    /**
     * 库/目录名，MySQL 为 database；PostgreSQL 为 database（table_catalog）
     */
    protected String resolveCatalogName(MetaDataSource dataSource) {
        return dataSource.getDefaultDb();
    }

    /**
     * Schema 名，MySQL 与库名相同；PostgreSQL 默认 public
     */
    protected String resolveSchemaName(MetaDataSource dataSource) {
        return dataSource.getDefaultDb();
    }

    protected abstract long countRemoteTables(Connection connection, MetaDataSource dataSource, String keyword)
            throws Exception;

    protected abstract List<TableMetadata> fetchRemoteTablesPage(Connection connection,
                                                                 MetaDataSource dataSource,
                                                                 String keyword,
                                                                 int offset,
                                                                 int limit) throws Exception;
}
