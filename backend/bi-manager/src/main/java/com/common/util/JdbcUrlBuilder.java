package com.common.util;

import com.common.enums.DbTypeEnum;
import com.common.exception.BizException;
import com.metadata.entity.MetaDataSource;
import org.apache.commons.lang3.StringUtils;

/**
 * JDBC URL 构建工具
 */
public final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
    }

    /**
     * 从已保存的数据源解析 JDBC URL（采集/远程查表等场景）。
     * 优先 host/port/defaultDb 构建，与测试连接、保存逻辑保持一致。
     */
    public static String resolveForDataSource(MetaDataSource dataSource) {
        if (dataSource == null) {
            throw new BizException("数据源不存在");
        }
        return resolve(dataSource.getDbType(), dataSource.getHost(), dataSource.getPort(),
                dataSource.getDefaultDb(), dataSource.getSchemaName(), dataSource.getJdbcUrl());
    }

    /**
     * 测试连接 / 预览 URL 时使用：优先用 host/port/defaultDb 构建，连接信息不完整时回退 jdbcUrl。
     * 保存入库请使用 {@link #build(String, String, Integer, String, String)}。
     */
    public static String resolve(String dbType, String host, Integer port, String defaultDb,
                                 String schemaName, String jdbcUrl) {
        if (StringUtils.isNoneBlank(dbType, host, defaultDb) && port != null) {
            return build(dbType, host, port, defaultDb, schemaName);
        }
        if (StringUtils.isNotBlank(jdbcUrl)) {
            return normalize(dbType, jdbcUrl.trim(), schemaName);
        }
        throw new BizException("构建 JDBC URL 缺少必要参数");
    }

    public static String build(String dbType, String host, Integer port, String defaultDb, String schemaName) {
        if (StringUtils.isAnyBlank(dbType, host, defaultDb) || port == null) {
            throw new BizException("构建 JDBC URL 缺少必要参数");
        }
        DbTypeEnum type = resolveDbType(dbType);
        switch (type) {
            case MYSQL:
                return String.format("%s%s:%d/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                        type.getJdbcPrefix(), host, port, defaultDb);
            case POSTGRESQL:
            case GAUSSDB:
                return appendSchemaParam(
                        String.format("%s%s:%d/%s", type.getJdbcPrefix(), host, port, defaultDb),
                        schemaName);
            case ORACLE:
                if (StringUtils.isNotBlank(schemaName)) {
                    return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, defaultDb);
                }
                return String.format("%s%s:%d:%s", type.getJdbcPrefix(), host, port, defaultDb);
            case CLICKHOUSE:
            case DAMENG:
                return String.format("%s%s:%d/%s", type.getJdbcPrefix(), host, port, defaultDb);
            default:
                throw new BizException("不支持的数据库类型: " + dbType);
        }
    }

    /**
     * 规范化已有 JDBC URL（补 MySQL 公钥参数、PostgreSQL schema 等）。
     */
    public static String normalize(String dbType, String jdbcUrl, String schemaName) {
        String url = ensureMySqlConnectParams(jdbcUrl);
        if (isPostgresFamily(dbType, url)) {
            return ensurePostgresSchemaParam(url, schemaName);
        }
        return url;
    }

    private static DbTypeEnum resolveDbType(String dbType) {
        try {
            return DbTypeEnum.ofName(dbType);
        } catch (BizException ex) {
            if ("达梦".equals(dbType.trim())) {
                return DbTypeEnum.DAMENG;
            }
            throw ex;
        }
    }

    /**
     * 补全 MySQL 连接参数，避免 caching_sha2_password 在非 SSL 场景下报 Public Key Retrieval is not allowed。
     */
    public static String ensureMySqlConnectParams(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl) || !jdbcUrl.trim().regionMatches(true, 0, "jdbc:mysql:", 0, 11)) {
            return jdbcUrl;
        }
        String url = jdbcUrl.trim();
        if (url.toLowerCase().contains("allowpublickeyretrieval=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "allowPublicKeyRetrieval=true";
    }

    private static boolean isPostgresFamily(String dbType, String jdbcUrl) {
        if (StringUtils.isNotBlank(dbType)) {
            String normalized = dbType.trim();
            if ("PostgreSQL".equalsIgnoreCase(normalized) || "GaussDB".equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return jdbcUrl != null && jdbcUrl.trim().regionMatches(true, 0, "jdbc:postgresql:", 0, 16);
    }

    private static String ensurePostgresSchemaParam(String jdbcUrl, String schemaName) {
        if (StringUtils.isBlank(schemaName) || containsQueryParam(jdbcUrl, "currentSchema")) {
            return jdbcUrl;
        }
        return appendSchemaParam(jdbcUrl, schemaName);
    }

    private static boolean containsQueryParam(String jdbcUrl, String paramName) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return false;
        }
        String lowerUrl = jdbcUrl.toLowerCase();
        String lowerParam = paramName.toLowerCase() + "=";
        int queryStart = lowerUrl.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        return lowerUrl.substring(queryStart + 1).contains(lowerParam);
    }

    private static String appendSchemaParam(String jdbcUrl, String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return jdbcUrl;
        }
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "currentSchema=" + schemaName;
    }
}
