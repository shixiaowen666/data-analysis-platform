package com.common.enums;

import com.common.exception.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 数据库类型枚举
 */
@Getter
@AllArgsConstructor
public enum DbTypeEnum {

    MYSQL(1, "MySQL", "jdbc:mysql://", 3306),
    POSTGRESQL(2, "PostgreSQL", "jdbc:postgresql://", 5432),
    ORACLE(3, "Oracle", "jdbc:oracle:thin:@", 1521),
    DAMENG(4, "达梦 DM", "jdbc:dm://", 5236),
    GAUSSDB(5, "GaussDB", "jdbc:postgresql://", 8000),
    CLICKHOUSE(6, "ClickHouse", "jdbc:clickhouse://", 8123);

    private final Integer id;
    private final String name;
    private final String jdbcPrefix;
    private final Integer defaultPort;

    public static DbTypeEnum ofId(Integer id) {
        if (id == null) {
            throw new BizException("数据库类型 ID 不能为空");
        }
        return Arrays.stream(values())
                .filter(item -> item.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new BizException("不支持的数据库类型 ID: " + id));
    }

    public static String getJdbcPrefixById(Integer id) {
        return ofId(id).getJdbcPrefix();
    }

    public static DbTypeEnum ofName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("数据库类型名称不能为空");
        }
        String value = name.trim();
        return Arrays.stream(values())
                .filter(item -> item.name.equals(value)
                        || item.name.replace(" ", "").equalsIgnoreCase(value.replace(" ", ""))
                        || ("达梦".equals(value) && item == DAMENG))
                .findFirst()
                .orElseThrow(() -> new BizException("不支持的数据库类型: " + name));
    }

    public static java.util.List<DbTypeEnum> listAll() {
        return Arrays.asList(values());
    }
}
