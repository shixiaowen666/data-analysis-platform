package com.wm.semantic.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class QueryDataRequest {
    private List<Long> dimensionIds;
    private List<Long> indicatorIds;
    private List<Long> preferredTableIds;
    private List<Long> preferredModelIds;
    private TimeRange timeRange;
    private List<Filter> filters;
    /** TOP N 限制，与 paging 互斥，limit 优先，与 groupTopN 互斥。 */
    private Integer limit;
    private List<Sort> sorts;
    private Paging paging = new Paging();
    /** 分组 TOP N，与 limit/paging 互斥。 优先级：groupTopN > limit > paging，互斥。 */
    private GroupTopN groupTopN;
    /** 时间粒度: day/week/month/quarter/year，同环比功能使用 */
    private String dateGranularity;
    /** 指标对比类型映射: indicatorId → none/pop/yoy/both，不传/null 表示全不对比 */
    private Map<Long, String> indicatorComparison;

    @Data
    public static class TimeRange {
        private String start;
        private String end;
    }

    @Data
    public static class Filter {
        private String type;  // dimension-维度过滤(WHERE), indicator-指标过滤(HAVING)
        private String field;  // 字段名
        private String operator;  // > < >= <= = != in notin
        private List<String> values; // 过滤值列表
    }

    @Data
    public static class Sort {
        /** 排序字段对应的 basic_id。 */
        private Long basicId;
        /** asc / desc，默认 asc。 */
        private String direction = "asc";
    }

    @Data
    public static class Paging {
        private int page = 1;
        private int pageSize = 5000;
    }

    @Data
    public static class GroupTopN {
        /** 分组字段 basic_id 列表（如 [ptdate的basicId]）。 */
        private List<Long> groupByBasicIds;
        /** 组内排序。 */
        private List<Sort> orderBy;
        /** 每组保留前几条。 */
        private Integer n;
    }
}