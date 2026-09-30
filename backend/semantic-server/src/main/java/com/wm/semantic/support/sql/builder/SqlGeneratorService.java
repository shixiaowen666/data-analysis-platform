package com.wm.semantic.support.sql.builder;

import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.IndicatorMeta;
import com.wm.semantic.dto.QueryDataRequest;
import com.wm.semantic.support.sql.SqlDialect;
import com.wm.semantic.support.sql.model.SqlBuildContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 生成总入口
 *
 * 对每个数据源构造 SqlBuildContext 后调用 SegmentSqlBuilder，
 * 多数据源场景再走 UNION ALL + 外层二次聚合包装。
 *
 * 不再回查任何元数据，全部依赖上游构造好的 SqlBuildContext。
 */
@Slf4j
@Service
public class SqlGeneratorService {

    @Resource
    private SegmentSqlBuilder segmentSqlBuilder;

    @Resource
    private UnionSqlBuilder unionSqlBuilder;

    @Resource
    private OuterSqlWrapper outerSqlWrapper;

    /**
     * 生成最终 SQL（不分页）。
     *
     * @param contexts 每个数据源对应一个 SqlBuildContext
     * @param outerGroupDimensions 外层 group by 维度列名（来自任一 segment 的维度别名集合）
     * @param requestIndicators 用户请求的全部指标元数据
     * @return 最终 SQL
     */
    public String generateFinalSql(
            List<SqlBuildContext> contexts,
            List<String> outerGroupDimensions,
            List<IndicatorMeta> requestIndicators
    ) {
        if (CollectionUtils.isEmpty(contexts)) {
            throw new BizException("SqlBuildContext 列表为空，无法生成 SQL");
        }

        List<String> segments = new ArrayList<String>();
        for (SqlBuildContext ctx : contexts) {
            segments.add(segmentSqlBuilder.buildSegment(ctx));
        }

        if (segments.size() == 1) {
            SqlBuildContext single = contexts.get(0);
            String typeLabel = single.getSource() != null && single.getSource().getType() != null
                    && single.getSource().getType() == 2 ? "模型" : "单表";
            Long sourceId = single.getSource() != null && single.getSource().getType() != null
                    && single.getSource().getType() == 2 ? single.getSource().getModelId() : single.getSource().getTableId();
            log.info("[SqlGen] 单数据源({}), sourceId={}, dimCount={}, indCount={}",
                    typeLabel, sourceId,
                    single.getDimensionFields() == null ? 0 : single.getDimensionFields().size(),
                    single.getRequestIndicators() == null ? 0 : single.getRequestIndicators().size());
            return segments.get(0);
        }

        // 多数据源场景，取首个 context 的 dialect（同 UNION ALL 必然同一数据库）
        String dbDialect = contexts.get(0).getSource().getDbDialect();
        log.info("[SqlGen] 多数据源, segmentCount={}, 走 UNION ALL + 外层包装", segments.size());
        String unionSql = unionSqlBuilder.buildUnionSql(segments);
        return outerSqlWrapper.wrapFinalSql(unionSql, outerGroupDimensions, requestIndicators, dbDialect);
    }

    /**
     * 分组 TOP N：用 ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...) 包装原 SQL，
     * 外层 WHERE _rn <= N 过滤。
     */
    public String appendGroupTopN(String sql, QueryDataRequest.GroupTopN groupTopN,
                                  Map<Long, String> fieldKeyMap, String dbDialect) {
        if (groupTopN == null || groupTopN.getN() == null || groupTopN.getN() <= 0
                || CollectionUtils.isEmpty(groupTopN.getGroupByBasicIds())) {
            return sql;
        }
        SqlDialect dialect = SqlDialect.from(dbDialect);

        // PARTITION BY
        StringBuilder partition = new StringBuilder();
        for (Long basicId : groupTopN.getGroupByBasicIds()) {
            String fieldKey = fieldKeyMap.get(basicId);
            if (fieldKey == null) {
                log.warn("[SqlGen] GroupTopN PARTITION BY basicId 未找到 fieldKey, basicId={}", basicId);
                continue;
            }
            if (partition.length() > 0) partition.append(", ");
            partition.append(dialect.quote(fieldKey));
        }
        if (partition.length() == 0) return sql;

        // ORDER BY (组内排序)
        StringBuilder order = new StringBuilder();
        if (!CollectionUtils.isEmpty(groupTopN.getOrderBy())) {
            for (QueryDataRequest.Sort sort : groupTopN.getOrderBy()) {
                if (sort == null || sort.getBasicId() == null) continue;
                String fieldKey = fieldKeyMap.get(sort.getBasicId());
                if (fieldKey == null) {
                    log.warn("[SqlGen] GroupTopN ORDER BY basicId 未找到 fieldKey, basicId={}", sort.getBasicId());
                    continue;
                }
                if (order.length() > 0) order.append(", ");
                order.append(dialect.quote(fieldKey));
                order.append("desc".equalsIgnoreCase(sort.getDirection()) ? " DESC" : " ASC");
            }
        }
        // 无组内排序时，降级为全局顺序
        if (order.length() == 0) {
            order.append("1");
        }

        return "SELECT * FROM (SELECT _ranked.*, ROW_NUMBER() OVER (PARTITION BY "
                + partition + " ORDER BY " + order + ") AS _rn FROM (" + sql + ") _ranked) _final WHERE _rn <= "
                + groupTopN.getN();
    }

    /**
     * 追加 ORDER BY 子句。
     *
     * @param sql          已生成的 SQL
     * @param sorts        排序字段列表
     * @param fieldKeyMap   basicId → fieldKey 映射（取自任一 context）
     * @param dbDialect    数据库方言
     */
    public String appendOrderBy(String sql, List<QueryDataRequest.Sort> sorts,
                                Map<Long, String> fieldKeyMap, String dbDialect) {
        if (CollectionUtils.isEmpty(sorts)) {
            return sql;
        }
        SqlDialect dialect = SqlDialect.from(dbDialect);
        StringBuilder sb = new StringBuilder(sql).append(" ORDER BY ");
        for (int i = 0; i < sorts.size(); i++) {
            QueryDataRequest.Sort sort = sorts.get(i);
            if (sort == null || sort.getBasicId() == null) {
                continue;
            }
            String fieldKey = fieldKeyMap.get(sort.getBasicId());
            if (fieldKey == null) {
                log.warn("[SqlGen] ORDER BY 字段未在 fieldKeyMap 中找到, basicId={}", sort.getBasicId());
                continue;
            }
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(dialect.quote(fieldKey));
            String direction = sort.getDirection();
            if ("desc".equalsIgnoreCase(direction)) {
                sb.append(" DESC");
            } else {
                sb.append(" ASC");
            }
        }
        return sb.toString();
    }

    /**
     * 追加分页子句，根据数据库方言选用不同语法。
     * page/pageSize 均为 null（如同环比对比期查询）时不追加分页。
     */
    public String appendPaging(String sql, Integer page, Integer pageSize, String dbDialect) {
        if (page == null || pageSize == null) {
            return sql;
        }
        int finalPage = page <= 0 ? 1 : page;
        int finalPageSize = pageSize <= 0 ? 5000 : pageSize;
        return sql + SqlDialect.from(dbDialect).paging(finalPage, finalPageSize);
    }
}
