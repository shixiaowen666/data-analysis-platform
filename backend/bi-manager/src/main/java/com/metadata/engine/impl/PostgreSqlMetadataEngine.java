package com.metadata.engine.impl;

import com.common.enums.DbTypeEnum;
import com.common.util.JdbcConnectionFactory;
import com.metadata.engine.AbstractEngine;
import com.metadata.engine.model.CollectContext;
import com.metadata.engine.model.ColumnMetadata;
import com.metadata.engine.model.TableMetadata;
import com.metadata.entity.MetaDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PostgreSQL 元数据采集引擎
 */
@Slf4j
@Component
public class PostgreSqlMetadataEngine extends AbstractEngine {

    private static final String TABLE_SQL =
            "SELECT t.table_name, "
                    + "pg_catalog.obj_description(c.oid, 'pg_class') AS table_comment, "
                    + "COALESCE(c.reltuples, 0)::bigint AS table_rows "
                    + "FROM information_schema.tables t "
                    + "JOIN pg_catalog.pg_class c ON c.relname = t.table_name "
                    + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace AND n.nspname = t.table_schema "
                    + "WHERE t.table_catalog = ? AND t.table_schema = ? AND t.table_type IN ('BASE TABLE', 'FOREIGN TABLE', 'FOREIGN')";

    private static final String TABLE_ORDER_SQL = TABLE_SQL + " ORDER BY t.table_name";

    private static final String TABLE_LIKE_SQL = TABLE_SQL + " AND t.table_name LIKE ? ORDER BY t.table_name";

    private static final String TABLE_COUNT_SQL =
            "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_catalog = ? AND table_schema = ? AND table_type IN ('BASE TABLE', 'FOREIGN TABLE', 'FOREIGN')";

    private static final String TABLE_COUNT_LIKE_SQL = TABLE_COUNT_SQL + " AND table_name LIKE ?";

    private static final String COLUMN_SQL =
            "SELECT c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, c.numeric_scale, "
                    + "c.is_nullable, c.column_default, c.ordinal_position, "
                    + "pg_catalog.col_description(format('%I.%I', c.table_schema, c.table_name)::regclass::oid, "
                    + "c.ordinal_position) AS column_comment "
                    + "FROM information_schema.columns c "
                    + "WHERE c.table_catalog = ? AND c.table_schema = ? AND c.table_name = ? "
                    + "ORDER BY c.ordinal_position";

    private static final String PRIMARY_KEY_SQL =
            "SELECT kcu.column_name "
                    + "FROM information_schema.table_constraints tc "
                    + "JOIN information_schema.key_column_usage kcu "
                    + "ON tc.constraint_catalog = kcu.constraint_catalog "
                    + "AND tc.constraint_schema = kcu.constraint_schema "
                    + "AND tc.constraint_name = kcu.constraint_name "
                    + "WHERE tc.table_catalog = ? AND tc.table_schema = ? AND tc.table_name = ? "
                    + "AND tc.constraint_type = 'PRIMARY KEY'";

    @Override
    public boolean supports(DbTypeEnum dbType) {
        return DbTypeEnum.POSTGRESQL == dbType || DbTypeEnum.GAUSSDB == dbType;
    }

    @Override
    protected String resolveSchemaName(MetaDataSource dataSource) {
        return StringUtils.isNotBlank(dataSource.getSchemaName()) ? dataSource.getSchemaName().trim() : "public";
    }

    @Override
    protected Connection openConnection(MetaDataSource dataSource) throws Exception {
        return JdbcConnectionFactory.create(dataSource);
    }

    @Override
    protected List<TableMetadata> fetchTables(Connection connection, CollectContext context) throws Exception {
        MetaDataSource dataSource = context.getDataSource();
        return queryTables(connection, dataSource, null, 0, 0, false);
    }

    @Override
    protected List<ColumnMetadata> fetchColumns(Connection connection,
                                                CollectContext context,
                                                TableMetadata table) throws Exception {
        MetaDataSource dataSource = context.getDataSource();
        String catalog = resolveCatalogName(dataSource);
        String schema = table.getSchemaName();
        Set<String> primaryKeys = loadPrimaryKeys(connection, catalog, schema, table.getTableName());
        List<ColumnMetadata> columns = new ArrayList<>();
        log.info("采集字段元数据 | dbType={} | sql={} | catalog={} | schema={} | table={}",
                dataSource.getDbType(), COLUMN_SQL, catalog, schema, table.getTableName());
        try (PreparedStatement statement = connection.prepareStatement(COLUMN_SQL)) {
            statement.setString(1, catalog);
            statement.setString(2, schema);
            statement.setString(3, table.getTableName());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ColumnMetadata column = new ColumnMetadata();
                    String columnName = rs.getString("column_name");
                    column.setColumnName(columnName);
                    column.setDataType(buildColumnType(rs));
                    column.setIsNullable("YES".equalsIgnoreCase(rs.getString("is_nullable")) ? 1 : 0);
                    column.setDefaultValue(rs.getString("column_default"));
                    column.setColumnComment(rs.getString("column_comment"));
                    column.setOrdinal(rs.getInt("ordinal_position"));
                    column.setIsPk(primaryKeys.contains(columnName) ? 1 : 0);
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    @Override
    protected long countRemoteTables(Connection connection, MetaDataSource dataSource, String keyword) throws Exception {
        String catalog = resolveCatalogName(dataSource);
        String schema = resolveSchemaName(dataSource);
        if (StringUtils.isBlank(keyword)) {
            try (PreparedStatement statement = connection.prepareStatement(TABLE_COUNT_SQL)) {
                statement.setString(1, catalog);
                statement.setString(2, schema);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(TABLE_COUNT_LIKE_SQL)) {
            statement.setString(1, catalog);
            statement.setString(2, schema);
            statement.setString(3, "%" + keyword.trim() + "%");
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    protected List<TableMetadata> fetchRemoteTablesPage(Connection connection,
                                                        MetaDataSource dataSource,
                                                        String keyword,
                                                        int offset,
                                                        int limit) throws Exception {
        return queryTables(connection, dataSource, keyword, offset, limit, true);
    }

    private List<TableMetadata> queryTables(Connection connection,
                                            MetaDataSource dataSource,
                                            String keyword,
                                            int offset,
                                            int limit,
                                            boolean paged) throws Exception {
        String catalog = resolveCatalogName(dataSource);
        String schema = resolveSchemaName(dataSource);
        List<TableMetadata> tables = new ArrayList<>();
        String pageClause = paged ? String.format(" LIMIT %d OFFSET %d", limit, offset) : "";
        String sql = StringUtils.isBlank(keyword) ? TABLE_ORDER_SQL + pageClause : TABLE_LIKE_SQL + pageClause;
        log.info("采集表元数据 | dbType={} | sql={} | catalog={} | schema={} | keyword={} | page={}/{}",
                dataSource.getDbType(), sql, catalog, schema, keyword, offset, limit);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catalog);
            statement.setString(2, schema);
            if (StringUtils.isNotBlank(keyword)) {
                statement.setString(3, "%" + keyword.trim() + "%");
            }
            appendTablesFromResultSet(schema, statement, tables);
        }
        return tables;
    }

    private void appendTablesFromResultSet(String schema,
                                           PreparedStatement statement,
                                           List<TableMetadata> tables) throws Exception {
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                TableMetadata table = new TableMetadata();
                table.setSchemaName(schema);
                table.setTableName(rs.getString("table_name"));
                table.setTableComment(rs.getString("table_comment"));
                table.setRowCountEstimate(rs.getLong("table_rows"));
                tables.add(table);
            }
        }
    }

    private Set<String> loadPrimaryKeys(Connection connection,
                                        String catalog,
                                        String schema,
                                        String tableName) throws Exception {
        Set<String> primaryKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(PRIMARY_KEY_SQL)) {
            statement.setString(1, catalog);
            statement.setString(2, schema);
            statement.setString(3, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("column_name"));
                }
            }
        }
        return primaryKeys;
    }

    private String buildColumnType(ResultSet rs) throws Exception {
        String dataType = rs.getString("data_type");
        Object charLen = rs.getObject("character_maximum_length");
        if (charLen != null) {
            return dataType + "(" + charLen + ")";
        }
        Object precision = rs.getObject("numeric_precision");
        Object scale = rs.getObject("numeric_scale");
        if (precision != null && scale != null) {
            return dataType + "(" + precision + "," + scale + ")";
        }
        if (precision != null) {
            return dataType + "(" + precision + ")";
        }
        return dataType;
    }
}
