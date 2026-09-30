package com.wm.semantic.support.time;

import com.wm.semantic.common.enums.ComparisonType;
import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.QueryDataRequest.TimeRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时间范围换算器：把本期 timeRange 换算成对比期 timeRange。
 *
 * 偏移规则：
 * | granularity | pop 偏移                           | yoy 偏移 |
 * | day         | 1 天                              | 1 年     |
 * | week        | 7 天                              | 1 年     |
 * | month       | 日历减 1 月（end 对齐上月末）        | 1 年     |
 * | quarter     | 日历减 3 月（end 对齐上季末）        | 1 年     |
 * | year        | 1 年                              | 1 年     |
 */
@Slf4j
@Component
public class TimeRangeShifter {

    private static final DateTimeFormatter STD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 换算对比期时间范围。
     *
     * @param current     本期时间范围
     * @param type        对比类型（POP 或 YOY）
     * @param granularity 时间粒度（day/week/month/quarter/year）
     * @return 对比期时间范围
     */
    public TimeRange shift(TimeRange current, ComparisonType type, String granularity) {
        LocalDate start = parse(current.getStart());
        LocalDate end = parse(current.getEnd());

        LocalDate shiftedStart;
        LocalDate shiftedEnd;
        if (type == ComparisonType.YOY) {
            shiftedStart = start.minusYears(1);
            shiftedEnd = end.minusYears(1);
        } else if (type == ComparisonType.POP) {
            // 月/季/年粒度按日历推算，end 用「+1天→减月→-1天」对齐上一周期最后一天，
            // 避免用固定天数平移导致月末/季末差一天（如 06-30 减92天=03-30 而非 03-31）
            String g = granularity == null || granularity.isEmpty() ? "day" : granularity.toLowerCase().trim();
            switch (g) {
                case "day":
                    shiftedStart = start.minusDays(1);
                    shiftedEnd = end.minusDays(1);
                    break;
                case "week":
                    shiftedStart = start.minusDays(7);
                    shiftedEnd = end.minusDays(7);
                    break;
                case "month":
                    shiftedStart = start.minusMonths(1);
                    shiftedEnd = end.plusDays(1).minusMonths(1).minusDays(1);
                    break;
                case "quarter":
                    shiftedStart = start.minusMonths(3);
                    shiftedEnd = end.plusDays(1).minusMonths(3).minusDays(1);
                    break;
                case "year":
                    shiftedStart = start.minusYears(1);
                    shiftedEnd = end.minusYears(1);
                    break;
                default:
                    throw new BizException("不支持的时间粒度: " + granularity);
            }
        } else {
            throw new BizException("不支持的对比类型: " + type);
        }

        TimeRange result = new TimeRange();
        result.setStart(formatLike(current.getStart(), shiftedStart));
        result.setEnd(formatLike(current.getEnd(), shiftedEnd));
        log.info("[时间换算] type={}, granularity={}, {}~{} → {}~{}",
                type, granularity, current.getStart(), current.getEnd(),
                result.getStart(), result.getEnd());
        return result;
    }

    /** 对比期日期偏移到本期日期所需的天数（用于 merge 时上期行日期映射）。 */
    public long popOffsetDays(TimeRange current, String granularity) {
        LocalDate start = parse(current.getStart());
        LocalDate end = parse(current.getEnd());
        return periodDays(start, end, granularity);
    }

    private long periodDays(LocalDate start, LocalDate end, String granularity) {
        if (granularity == null || granularity.isEmpty()) {
            // 默认日粒度：对比前一天（业界惯例），而非平移整个请求周期
            return 1;
        }
        switch (granularity.toLowerCase().trim()) {
            case "day":
                // 日粒度环比 = 昨天，固定偏移 1 天（与周/月/季/年粒度的"上一自然周期"语义一致）
                return 1;
            case "week":
                return 7;
            case "month":
                return ChronoUnit.DAYS.between(start.minusMonths(1), start);
            case "quarter":
                return ChronoUnit.DAYS.between(start.minusMonths(3), start);
            case "year":
                return ChronoUnit.DAYS.between(start.minusYears(1), start);
            default:
                throw new BizException("不支持的时间粒度: " + granularity);
        }
    }

    private LocalDate parse(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new BizException("timeRange 缺失");
        }
        String s = dateStr.trim();
        try {
            if (s.contains("-")) {
                return LocalDate.parse(s, STD);
            }
            return LocalDate.parse(s, COMPACT);
        } catch (Exception e) {
            throw new BizException("时间格式无法解析: " + dateStr);
        }
    }

    /** 按原始字符串的格式输出（保留 yyyy-MM-dd 或 yyyyMMdd 风格）。 */
    private String formatLike(String original, LocalDate date) {
        if (original != null && !original.contains("-")) {
            return date.format(COMPACT);
        }
        return date.format(STD);
    }
}
