package com.common.util;

import com.metadata.vo.meta.DataSourceTestResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC 连接测试工具
 */
@Slf4j
public final class JdbcConnectionTester {

    private JdbcConnectionTester() {
    }

    public static DataSourceTestResultVO test(String dbType, String jdbcUrl, String username, String password, String schemaName) {
        DataSourceTestResultVO result = new DataSourceTestResultVO();
        try (Connection connection = JdbcConnectionFactory.create(dbType, jdbcUrl, username, password)) {
            String productName = connection.getMetaData().getDatabaseProductName();
            String productVersion = connection.getMetaData().getDatabaseProductVersion();
            String catalog = connection.getCatalog();
            result.setSuccess(true);
            result.setDbVersion(productName + " " + productVersion);
            result.setDatabaseName(catalog);
            result.setTableCount(countTables(connection, dbType, catalog, schemaName));
            result.setMessage(buildSuccessMessage(result, schemaName));
            return result;
        } catch (SQLException ex) {
            log.warn("数据源连接测试失败: {}", ex.getMessage());
            result.setSuccess(false);
            result.setMessage("连接失败：" + ex.getMessage());
            return result;
        }
    }

    private static Integer countTables(Connection connection, String dbType, String catalog, String schemaName)
            throws SQLException {
        String normalizedType = dbType.trim();
        if ("MySQL".equalsIgnoreCase(normalizedType) && catalog != null) {
            String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, catalog);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        if (("PostgreSQL".equalsIgnoreCase(normalizedType) || "GaussDB".equalsIgnoreCase(normalizedType))
                && catalog != null) {
            String schema = StringUtils.isNotBlank(schemaName) ? schemaName.trim() : "public";
            String sql = "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_catalog = ? AND table_schema = ? AND table_type IN ('BASE TABLE', 'FOREIGN TABLE', 'FOREIGN')";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, catalog);
                statement.setString(2, schema);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    private static String buildSuccessMessage(DataSourceTestResultVO result, String schemaName) {
        StringBuilder message = new StringBuilder("连接成功");
        if (result.getDbVersion() != null) {
            message.append(" — 数据库版本 ").append(result.getDbVersion());
        }
        if (result.getDatabaseName() != null) {
            message.append("，当前库 ").append(result.getDatabaseName());
        }
        if (StringUtils.isNotBlank(schemaName)) {
            message.append("，Schema ").append(schemaName.trim());
        }
        if (result.getTableCount() != null) {
            message.append("，共 ").append(result.getTableCount()).append(" 张表");
        }
        return message.toString();
    }
}
