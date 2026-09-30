package com.wm.semantic.support.sql;

import java.util.Locale;

/**
 * 时间范围条件生成器
 *
 * 内层子查询已将 ptdate 统一格式化为 yyyy-MM-dd 字符串，
 * 因此外层 WHERE 直接做字符串比较即可，不再需要按字段类型适配。
 */
public class TimeRangeConverter {

    private TimeRangeConverter() {
    }

    /**
     * 生成时间范围 WHERE 条件片段。
     *
     * @param columnRef 外层列引用，如 f.`ptdate`
     * @param startDate 起始日期 yyyy-MM-dd（可为 null）
     * @param endDate   截止日期 yyyy-MM-dd（可为 null）
     * @return 条件片段（以 "and " 开头），两端都为 null 时返回空串
     */
    public static String buildCondition(String columnRef, String startDate, String endDate) {
        if (isBlank(startDate) && isBlank(endDate)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!isBlank(startDate)) {
            sb.append("and ").append(columnRef).append(" >= '").append(startDate).append("'");
        }
        if (!isBlank(endDate)) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("and ").append(columnRef).append(" <= '").append(endDate).append("'");
        }
        return sb.toString();
    }

    /**
     * 归一化源字段类型，仅用于内层 SQL 的日期格式化判断。
     */
    public static String normalizeFieldType(String srcFieldType) {
        if (isBlank(srcFieldType)) {
            return "varchar";
        }
        String t = srcFieldType.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("date") && !t.startsWith("datetime")) {
            return "date";
        }
        if (t.startsWith("datetime")) {
            return "datetime";
        }
        if (t.startsWith("timestamp")) {
            return "timestamp";
        }
        if (t.startsWith("int") || t.startsWith("bigint") || t.equals("integer") || t.equals("number")) {
            return "int";
        }
        return "varchar";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
