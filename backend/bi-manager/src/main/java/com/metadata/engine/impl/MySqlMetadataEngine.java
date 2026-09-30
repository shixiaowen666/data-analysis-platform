package com.metadata.engine.impl;

import com.common.enums.DbTypeEnum;
import com.common.util.JdbcConnectionFactory;
import com.metadata.engine.AbstractEngine;
import com.metadata.engine.model.CollectContext;
import com.metadata.engine.model.ColumnMetadata;
import com.metadata.engine.model.TableMetadata;
import com.metadata.entity.MetaDataSource;
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
 * MySQL 元数据采集引擎
 */
@Component
public class MySqlMetadataEngine extends AbstractEngine {

    private static final String TABLE_SQL =
            "SELECT TABLE_NAME, TABLE_COMMENT, IFNULL(TABLE_ROWS, 0) AS TABLE_ROWS "
                    + "FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' "
                    + "ORDER BY TABLE_NAME";

    private static final String COLUMN_SQL =
            "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, ORDINAL_POSITION "
                    + "FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                    + "ORDER BY ORDINAL_POSITION";

    private static final String PRIMARY_KEY_SQL =
            "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'";

    private static final String TABLE_COUNT_SQL =
            "SELECT COUNT(*) FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'";

    private static final String TABLE_COUNT_LIKE_SQL =
            TABLE_COUNT_SQL + " AND TABLE_NAME LIKE ?";

    private static final String TABLE_PAGE_SQL =
            "SELECT TABLE_NAME, TABLE_COMMENT, IFNULL(TABLE_ROWS, 0) AS TABLE_ROWS "
                    + "FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'";

    private static final String TABLE_PAGE_LIKE_SQL =
            TABLE_PAGE_SQL + " AND TABLE_NAME LIKE ? ORDER BY TABLE_NAME";

    private static final String TABLE_PAGE_ORDER_SQL =
            TABLE_PAGE_SQL + " ORDER BY TABLE_NAME";

    @Override
    public boolean supports(DbTypeEnum dbType) {
        return DbTypeEnum.MYSQL == dbType;
    }

    @Override
    protected Connection openConnection(MetaDataSource dataSource) throws Exception {
        return JdbcConnectionFactory.create(dataSource);
    }

    @Override
    protected List<TableMetadata> fetchTables(Connection connection, CollectContext context) throws Exception {
        String schema = resolveSchemaName(context.getDataSource());
        return queryTables(connection, schema, null, 0, 0, false);
    }

    @Override
    protected List<ColumnMetadata> fetchColumns(Connection connection,
                                                CollectContext context,
                                                TableMetadata table) throws Exception {
        String schema = table.getSchemaName();
        Set<String> primaryKeys = loadPrimaryKeys(connection, schema, table.getTableName());
        List<ColumnMetadata> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(COLUMN_SQL)) {
            statement.setString(1, schema);
            statement.setString(2, table.getTableName());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ColumnMetadata column = new ColumnMetadata();
                    String columnName = rs.getString("COLUMN_NAME");
                    column.setColumnName(columnName);
                    column.setDataType(rs.getString("COLUMN_TYPE"));
                    column.setIsNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")) ? 1 : 0);
                    column.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                    column.setColumnComment(rs.getString("COLUMN_COMMENT"));
                    column.setOrdinal(rs.getInt("ORDINAL_POSITION"));
                    column.setIsPk(primaryKeys.contains(columnName) ? 1 : 0);
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    @Override
    protected long countRemoteTables(Connection connection, MetaDataSource dataSource, String keyword) throws Exception {
        String schema = resolveSchemaName(dataSource);
        if (StringUtils.isBlank(keyword)) {
            try (PreparedStatement statement = connection.prepareStatement(TABLE_COUNT_SQL)) {
                statement.setString(1, schema);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(TABLE_COUNT_LIKE_SQL)) {
            statement.setString(1, schema);
            statement.setString(2, "%" + keyword.trim() + "%");
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
        String schema = resolveSchemaName(dataSource);
        return queryTables(connection, schema, keyword, offset, limit, true);
    }

    private List<TableMetadata> queryTables(Connection connection,
                                            String schema,
                                            String keyword,
                                            int offset,
                                            int limit,
                                            boolean paged) throws Exception {
        List<TableMetadata> tables = new ArrayList<>();
        String pageClause = paged ? String.format(" LIMIT %d, %d", offset, limit) : "";
        String sql;
        if (StringUtils.isBlank(keyword)) {
            sql = TABLE_PAGE_ORDER_SQL + pageClause;
        } else {
            sql = TABLE_PAGE_LIKE_SQL + pageClause;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            if (StringUtils.isNotBlank(keyword)) {
                statement.setString(2, "%" + keyword.trim() + "%");
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
                table.setTableName(rs.getString("TABLE_NAME"));
                table.setTableComment(rs.getString("TABLE_COMMENT"));
                table.setRowCountEstimate(rs.getLong("TABLE_ROWS"));
                tables.add(table);
            }
        }
    }

    private Set<String> loadPrimaryKeys(Connection connection, String schema, String tableName) throws Exception {
        Set<String> primaryKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(PRIMARY_KEY_SQL)) {
            statement.setString(1, schema);
            statement.setString(2, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return primaryKeys;
    }
}
