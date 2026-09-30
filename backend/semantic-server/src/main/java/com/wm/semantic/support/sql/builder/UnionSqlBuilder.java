package com.wm.semantic.support.sql.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * UNION ALL 拼接器
 *
 * 把多数据源生成的 segment SQL 用 UNION ALL 串联起来。
 */
@Slf4j
@Component
public class UnionSqlBuilder {

    /**
     * 把多个 segment 用 UNION ALL 拼接。
     *
     * @param segments 各数据源生成的聚合 SQL 片段
     * @return 串联后的 SQL
     */
    public String buildUnionSql(List<String> segments) {
        log.info("[UnionSql] segmentCount={}", segments == null ? 0 : segments.size());
        return String.join("\nUNION ALL\n", segments);
    }
}
