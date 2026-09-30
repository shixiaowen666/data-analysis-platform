package com.common.util;

/**
 * 预估行数展示格式化
 */
public final class RowCountFormatter {

    private RowCountFormatter() {
    }

    public static String format(Long rowCount) {
        if (rowCount == null || rowCount <= 0) {
            return "—";
        }
        if (rowCount >= 100_000_000L) {
            return "约 " + formatCompact(rowCount / 100_000_000.0) + " 亿";
        }
        if (rowCount >= 10_000L) {
            return "约 " + formatCompact(rowCount / 10_000.0) + " 万";
        }
        return String.format("%,d", rowCount);
    }

    private static String formatCompact(double value) {
        if (value >= 100) {
            return String.format("%,.0f", value);
        }
        if (value >= 10) {
            return String.format("%.1f", value).replaceAll("\\.0$", "");
        }
        return String.format("%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
