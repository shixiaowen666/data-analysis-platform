package com.common.util;

import com.common.exception.BizException;
import com.metadata.entity.MetaDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC 连接工厂：从 MetaDataSource 统一创建 Connection
 */
public final class JdbcConnectionFactory {

    private JdbcConnectionFactory() {
    }

    /**
     * 从 MetaDataSource 创建 JDBC 连接
     */
    public static Connection create(MetaDataSource ds) {
        loadDriver(ds.getDbType());
        String url = resolveJdbcUrl(ds);
        try {
            return DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
        } catch (SQLException e) {
            throw new BizException("数据源连接失败: " + e.getMessage());
        }
    }

    /**
     * 通过原始参数创建连接（用于连接测试等场景）
     */
    public static Connection create(String dbType, String jdbcUrl, String username, String password) {
        loadDriver(dbType);
        try {
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (SQLException e) {
            throw new BizException("数据源连接失败: " + e.getMessage());
        }
    }

    /**
     * 解析 JDBC URL：优先使用已有的，否则根据 host/port/db 等参数构建
     */
    public static String resolveJdbcUrl(MetaDataSource ds) {
        String url = ds.getJdbcUrl();
        if (url == null || url.trim().isEmpty()) {
            url = JdbcUrlBuilder.build(ds.getDbType(), ds.getHost(),
                    ds.getPort(), ds.getDefaultDb(), ds.getSchemaName());
        }
        if (url != null && url.regionMatches(true, 0, "jdbc:mysql:", 0, 11)) {
            url = JdbcUrlBuilder.ensureMySqlConnectParams(url);
        }
        return url;
    }

    /**
     * 加载 JDBC 驱动
     */
    public static void loadDriver(String dbType) {
        String driverClass = resolveDriver(dbType);
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new BizException("当前环境未配置 " + dbType + " 驱动，请先添加对应 JDBC 依赖");
        }
    }

    /**
     * 根据数据库类型解析 JDBC 驱动类名
     */
    public static String resolveDriver(String dbType) {
        if (dbType == null || dbType.trim().isEmpty()) {
            throw new BizException("数据库类型不能为空");
        }
        switch (dbType.trim()) {
            case "MySQL":
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "PostgreSQL":
            case "postgresql":
            case "GaussDB":
            case "gaussdb":
                return "org.postgresql.Driver";
            case "Oracle":
            case "oracle":
                return "oracle.jdbc.OracleDriver";
            case "ClickHouse":
            case "clickhouse":
                return "com.clickhouse.jdbc.ClickHouseDriver";
            case "达梦 DM":
            case "达梦":
            case "dameng":
                return "dm.jdbc.driver.DmDriver";
            default:
                throw new BizException("不支持的数据库类型: " + dbType);
        }
    }
}
