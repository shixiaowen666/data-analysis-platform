package com.wm.semantic.support.sql;

import java.util.Locale;

/**
 * SQL 方言枚举，集中处理标识符引用、分页子句等数据库差异。
 *
 * 支持的方言：mysql / postgresql / oracle / gaussdb / dm / hive
 */
public enum SqlDialect {

    MYSQL {
        @Override public String quote(String name) { return "`" + name.replace("`", "") + "`"; }
        @Override public String paging(int page, int pageSize) {
            int offset = (page - 1) * pageSize;
            return "\nlimit\n  " + pageSize + " offset " + offset;
        }
    },

    POSTGRESQL {
        @Override public String quote(String name) { return "\"" + name.replace("\"", "") + "\""; }
        @Override public String paging(int page, int pageSize) {
            return MYSQL.paging(page, pageSize);
        }
    },

    ORACLE {
        @Override public String quote(String name) { return "\"" + name.replace("\"", "") + "\""; }
        @Override public String paging(int page, int pageSize) {
            int offset = (page - 1) * pageSize;
            return "\noffset " + offset + " rows fetch next " + pageSize + " rows only";
        }
    },

    GAUSSDB {
        @Override public String quote(String name) { return POSTGRESQL.quote(name); }
        @Override public String paging(int page, int pageSize) { return MYSQL.paging(page, pageSize); }
    },

    DM {
        @Override public String quote(String name) { return ORACLE.quote(name); }
        @Override public String paging(int page, int pageSize) { return MYSQL.paging(page, pageSize); }
    },

    HIVE {
        @Override public String quote(String name) { return MYSQL.quote(name); }
        @Override public String paging(int page, int pageSize) {
            return "\nlimit\n  " + pageSize;
        }
    };

    /** 标识符引用，如 mysql 用 `，pg/oracle 用 " */
    public abstract String quote(String name);

    /** 分页子句 */
    public abstract String paging(int page, int pageSize);

    /**
     * 从 dbDialect 字符串解析方言，未识别默认返回 MYSQL。
     */
    public static SqlDialect from(String dbDialect) {
        if (dbDialect == null) return MYSQL;
        switch (dbDialect.trim().toLowerCase(Locale.ROOT)) {
            case "mysql":       return MYSQL;
            case "postgresql":  return POSTGRESQL;
            case "oracle":      return ORACLE;
            case "gaussdb":     return GAUSSDB;
            case "dm":          return DM;
            case "hive":        return HIVE;
            default:            return MYSQL;
        }
    }
}
