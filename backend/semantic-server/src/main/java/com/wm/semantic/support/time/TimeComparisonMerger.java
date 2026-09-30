package com.wm.semantic.support.time;

import com.wm.semantic.common.enums.ComparisonType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同环比结果合并器。
 *
 * 把本期 rows 与对比期 rows 按（周期偏移后的时间值 + 维度键）匹配，
 * 为本期行追加 _prev/_ratio（pop）和 _yoy/_yoy_ratio（yoy）列。
 *
 * 偏移按粒度:
 *   day     → 日期 + 天数（pop 偏移天数 / yoy 365 天）
 *   week    → ISO 周值 +1 周 / -1 年
 *   month   → 'yyyy-MM' +1 月 / +12 月
 *   quarter → 'yyyyQq' +1 季 / +4 季
 *   year    → +1 年
 */
@Slf4j
@Component
public class TimeComparisonMerger {

    private static final DateTimeFormatter STD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * @param currentRows     本期查询结果行
     * @param prevRows        pop 对比期行（可为 null）
     * @param yoyRows         yoy 对比期行（可为 null）
     * @param dateFieldKey    时间列 key；无时间维度时为 null
     * @param dimensionKeys   非时间维度列 key 列表（无时间维度时为全部维度）
     * @param indicatorKeys   需要对比的指标列 key 集合（如 {"num_in"}）
     * @param popOffsetDays   pop 日粒度偏移天数
     * @param type            对比类型
     * @param dateGranularity 时间粒度（day/week/month/quarter/year）
     * @return 合并后的本期行（追加了对比列）
     */
    public List<Map<String, Object>> merge(List<Map<String, Object>> currentRows,
                                           List<Map<String, Object>> prevRows,
                                           List<Map<String, Object>> yoyRows,
                                           String dateFieldKey,
                                           List<String> dimensionKeys,
                                           List<String> indicatorKeys,
                                           long popOffsetDays,
                                           ComparisonType type,
                                           String dateGranularity) {
        if (CollectionUtils.isEmpty(currentRows)) {
            return new ArrayList<>();
        }

        Map<String, Map<String, Object>> prevMap = buildLookupMap(prevRows, dateFieldKey,
                dimensionKeys, dateGranularity, false, popOffsetDays);
        Map<String, Map<String, Object>> yoyMap = buildLookupMap(yoyRows, dateFieldKey,
                dimensionKeys, dateGranularity, true, popOffsetDays);
        log.info("[同环比merge] 本期行数={}, 上期行数={}, 同期行数={}, dateFieldKey={}, dimKeys={}, indicatorKeys={}, popOffset={}, type={}, granularity={}",
                currentRows.size(), prevRows == null ? 0 : prevRows.size(),
                yoyRows == null ? 0 : yoyRows.size(),
                dateFieldKey, dimensionKeys, indicatorKeys, popOffsetDays, type, dateGranularity);

        for (Map<String, Object> row : currentRows) {
            String rowKey = buildRowKey(row, dateFieldKey, dimensionKeys, null, false, 0);
            log.debug("[同环比merge] 本期行key={}", rowKey);

            if (type.includesPop() && prevMap != null) {
                Map<String, Object> prevRow = prevMap.get(rowKey);
                if (prevRow == null) {
                    log.warn("[同环比merge] 上期未匹配: key={}", rowKey);
                }
                appendCompareColumns(row, prevRow, indicatorKeys, "_prev", "_ratio");
            }
            if (type.includesYoy() && yoyMap != null) {
                Map<String, Object> yoyRow = yoyMap.get(rowKey);
                appendCompareColumns(row, yoyRow, indicatorKeys, "_yoy", "_yoy_ratio");
            }
        }
        return currentRows;
    }

    /**
     * 建对比期查找表。
     * key = 周期偏移后的时间值 + "|" + 维度值们；value = 整行。
     */
    private Map<String, Map<String, Object>> buildLookupMap(List<Map<String, Object>> rows,
                                                            String dateFieldKey,
                                                            List<String> dimensionKeys,
                                                            String granularity,
                                                            boolean isYoy,
                                                            long popOffsetDays) {
        if (CollectionUtils.isEmpty(rows)) {
            return new HashMap<>();
        }
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String key = buildRowKey(row, dateFieldKey, dimensionKeys, granularity, isYoy, popOffsetDays);
            map.put(key, row);
        }
        return map;
    }

    /**
     * 行 key：周期偏移后的时间值|维度值1|维度值2|...
     * 无时间维度时 key = 维度值1|维度值2|...
     */
    private String buildRowKey(Map<String, Object> row, String dateFieldKey,
                               List<String> dimensionKeys, String granularity,
                               boolean isYoy, long popOffsetDays) {
        StringBuilder sb = new StringBuilder();
        if (dateFieldKey != null) {
            Object dateVal = row.get(dateFieldKey);
            sb.append(shiftPeriodValue(dateVal, granularity, isYoy, popOffsetDays)).append("|");
        }
        if (!CollectionUtils.isEmpty(dimensionKeys)) {
            for (String dimKey : dimensionKeys) {
                Object v = row.get(dimKey);
                sb.append(v == null ? "null" : v.toString()).append("|");
            }
        }
        return sb.toString();
    }

    /** 对比期周期值偏移到本期周期（字符串）。 */
    private String shiftPeriodValue(Object value, String granularity, boolean isYoy, long popOffsetDays) {
        if (value == null) {
            return "null";
        }
        String v = value.toString();
        String g = granularity == null ? "day" : granularity.trim().toLowerCase();
        try {
            switch (g) {
                case "week": {
                    // '2026W32' → ISO 年第 32 周周一
                    int isoYear = Integer.parseInt(v.substring(0, 4));
                    int isoWeek = Integer.parseInt(v.substring(5));
                    LocalDate monday = LocalDate.of(isoYear, 1, 4)
                            .with(ChronoField.DAY_OF_WEEK, 1)
                            .plusWeeks(isoWeek - 1);
                    LocalDate shifted = isYoy ? monday.minusYears(1) : monday.plusDays(popOffsetDays);
                    int w = shifted.get(WeekFields.ISO.weekOfWeekBasedYear());
                    int y = shifted.get(WeekFields.ISO.weekBasedYear());
                    return y + "W" + String.format("%02d", w);
                }
                case "month": {
                    YearMonth ym = YearMonth.parse(v);
                    YearMonth shifted = ym.plusMonths(isYoy ? 12 : 1);
                    return shifted.toString();
                }
                case "quarter": {
                    int year = Integer.parseInt(v.substring(0, 4));
                    int quarter = Integer.parseInt(v.substring(5));
                    int shift = isYoy ? 4 : 1;
                    int total = (year * 4 + (quarter - 1)) + shift;
                    return (total / 4) + "Q" + (total % 4 + 1);
                }
                case "year":
                    return String.valueOf(Integer.parseInt(v) + 1);
                default: {
                    // day: 日期 + 天数（yoy 365 天近似）
                    LocalDate date = parseDate(v);
                    LocalDate shifted = date.plusDays(isYoy ? 365 : popOffsetDays);
                    return shifted.format(STD);
                }
            }
        } catch (Exception e) {
            // 无法解析的值原样返回
            return v;
        }
    }

    private LocalDate parseDate(String s) {
        if (s.contains("-")) {
            return LocalDate.parse(s, STD);
        }
        return LocalDate.parse(s, COMPACT);
    }

    /** 为本期行追加对比列：_prev/_yoy（原值）+ _ratio/_yoy_ratio（变化率%）。 */
    private void appendCompareColumns(Map<String, Object> row,
                                      Map<String, Object> compareRow,
                                      List<String> indicatorKeys,
                                      String valueSuffix,
                                      String ratioSuffix) {
        if (CollectionUtils.isEmpty(indicatorKeys)) {
            return;
        }
        for (String key : indicatorKeys) {
            Object currentVal = row.get(key);
            Object compareVal = compareRow != null ? compareRow.get(key) : null;

            row.put(key + valueSuffix, compareVal);

            BigDecimal ratio = calcRatio(currentVal, compareVal);
            row.put(key + ratioSuffix, ratio);
        }
    }

    /** (本期-对比期)/对比期*100，保留1位小数；对比期为 null 或 0 时返回 null。 */
    private BigDecimal calcRatio(Object currentVal, Object compareVal) {
        if (currentVal == null || compareVal == null) {
            return null;
        }
        try {
            BigDecimal current = new BigDecimal(currentVal.toString());
            BigDecimal compare = new BigDecimal(compareVal.toString());
            if (compare.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return current.subtract(compare)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(compare, 1, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
