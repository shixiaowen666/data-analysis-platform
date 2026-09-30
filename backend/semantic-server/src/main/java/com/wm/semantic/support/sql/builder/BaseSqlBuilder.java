package com.wm.semantic.support.sql.builder;

import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.DataSourceInfo;
import com.wm.semantic.dto.FieldMappingInfo;
import com.wm.semantic.dto.ModelJoinInfo;
import com.wm.semantic.support.sql.SqlDialect;
import com.wm.semantic.support.sql.TimeRangeConverter;
import com.wm.semantic.support.sql.model.SqlBuildContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内层子查询构造器（两层 SQL 结构的内层）
 *
 * 输出形如：
 *   select
 *     t0.`amount_src`     as `amount_key`,
 *     t0.`order_date_src` as `order_date_key`,
 *     t1.`region_src`     as `region_key`
 *   from `db`.`fact` t0
 *   left join `db`.`dim` t1 on t0.`fk` = t1.`pk`
 *
 * 设计要点：
 * 1. 物理别名 t0/t1 仅在内层使用，不再外传给 ctx
 * 2. 内层 SELECT 只投影"用户请求里出现过的"维度+指标字段，不含 join key
 * 3. 列重命名为 field_key（来自 olap_table_field_mapping，由 ctx.fieldKeyMap 提供）
 * 4. 校验同表内 field_key 不冲突
 * 5. 不生成 where/group by，那是外层职责
 */
@Slf4j
@Component
public class BaseSqlBuilder {

    /**
     * 构建内层子查询。
     *
     * 调用前提：ctx.fieldKeyMap 已经由上游生成完毕（basicId -> field_key）。
     *
     * @param ctx SQL 构建上下文
     * @return 完整的内层 select+from+join 片段（不含 where/group by）
     */
    public String buildInnerSubquery(SqlBuildContext ctx) {
        DataSourceInfo source = ctx.getSource();
        if (source == null || source.getType() == null) {
            throw new BizException("DataSourceInfo 类型为空");
        }
        SqlDialect dialect = SqlDialect.from(source.getDbDialect());
        if (source.getType() == 1) {
            return buildTableInner(ctx, dialect);
        }
        if (source.getType() == 2) {
            return buildModelInner(ctx, dialect);
        }
        throw new BizException("不支持的数据源类型: " + source.getType());
    }

    /**
     * 单表内层子查询：单一别名 mainsrc（与用户侧模板约定保持一致）。
     */
    private String buildTableInner(SqlBuildContext ctx, SqlDialect dialect) {
        DataSourceInfo source = ctx.getSource();
        if (source.getTableId() == null || isBlank(source.getTableName())) {
            throw new BizException("单表数据源信息不完整, tableId=" + source.getTableId());
        }
        Map<Long, String> aliasMap = new LinkedHashMap<Long, String>();
        aliasMap.put(source.getTableId(), "mainsrc");

        List<String> projection = buildProjectionList(ctx, aliasMap, dialect);

        boolean isView = source.getTbTypeKey() != null && source.getTbTypeKey() == 1
                && !isBlank(source.getViewSql());

        StringBuilder sql = new StringBuilder();
        sql.append("select ").append(String.join(", ", projection));
        if (isView) {
            sql.append(" from (").append(source.getViewSql()).append(") mainsrc");
        } else {
            String fullName = qualifiedName(source.getDbName(), source.getTableName(), dialect);
            sql.append(" from ").append(fullName).append(" mainsrc");
        }

        log.info("[BaseSql-单表] tableId={}, tableName={}, dbName={}, isView={}, fields={}",
                source.getTableId(), source.getTableName(), source.getDbName(), isView, projection.size());
        return sql.toString();
    }

    /**
     * 单模型内层子查询：fact=t0，dim=t1, t2,...，按 joinRelations 顺序展开。
     */
    private String buildModelInner(SqlBuildContext ctx, SqlDialect dialect) {
        DataSourceInfo source = ctx.getSource();
        if (source.getFactTableId() == null || isBlank(source.getFactTableName())) {
            throw new BizException("模型 fact 表信息缺失, modelId=" + source.getModelId());
        }

        Map<Long, String> aliasMap = new LinkedHashMap<Long, String>();
        aliasMap.put(source.getFactTableId(), "t0");

        StringBuilder fromAndJoin = new StringBuilder();
        fromAndJoin.append("from ")
                .append(qualifiedName(source.getFactDbName(), source.getFactTableName(), dialect))
                .append(" t0");

        if (!CollectionUtils.isEmpty(source.getJoinRelations())) {
            int dimCounter = 1;
            for (ModelJoinInfo join : source.getJoinRelations()) {
                if (join.getDimTableId() == null || isBlank(join.getDimTableName())) {
                    throw new BizException("模型 join 维度表信息缺失, modelId=" + source.getModelId());
                }
                String dimAlias = aliasMap.get(join.getDimTableId());
                if (dimAlias == null) {
                    dimAlias = "t" + dimCounter;
                    dimCounter++;
                    aliasMap.put(join.getDimTableId(), dimAlias);
                }
                String dimFullName = qualifiedName(join.getDimDbName(), join.getDimTableName(), dialect);
                fromAndJoin.append("\n").append(joinKeyword(join.getJoinType())).append(" ")
                        .append(dimFullName).append(" ").append(dimAlias)
                        .append(" on ").append(buildOnClause("t0", dimAlias, join, dialect));
            }
        }

        List<String> projection = buildProjectionList(ctx, aliasMap, dialect);

        StringBuilder sql = new StringBuilder();
        sql.append("select\n  ").append(String.join(",\n  ", projection));
        sql.append("\n").append(fromAndJoin);

        log.info("[BaseSql-模型] modelId={}, fact={}, dimCount={}, joinTables={}, fields={}",
                source.getModelId(),
                qualifiedName(source.getFactDbName(), source.getFactTableName(), dialect),
                source.getJoinRelations() == null ? 0 : source.getJoinRelations().size(),
                source.getJoinRelations() == null ? "[]"
                        : source.getJoinRelations().stream()
                            .map(j -> j.getDimDbName() + "." + j.getDimTableName())
                            .collect(java.util.stream.Collectors.joining(", ", "[", "]")),
                projection.size());
        return sql.toString();
    }

    /**
     * 构造内层 SELECT 投影列表：先维度（按请求顺序），再指标（按请求顺序）。
     *
     * 校验：
     *  - 每个字段必须有 tableId/srcField，且其 tableId 已在 aliasMap 中
     *  - field_key 在本次内层 SELECT 中不允许重复（重复会导致 (...) mainsrc 派生表列名冲突）
     */
    private List<String> buildProjectionList(SqlBuildContext ctx, Map<Long, String> aliasMap, SqlDialect dialect) {
        List<String> projection = new ArrayList<String>();
        Set<String> usedFieldKeys = new LinkedHashSet<String>();
        Map<String, Long> firstOwnerByFieldKey = new LinkedHashMap<String, Long>();

        // 维度字段
        if (!CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            for (FieldMappingInfo f : ctx.getDimensionFields()) {
                appendProjection(ctx, aliasMap, projection, usedFieldKeys, firstOwnerByFieldKey, f, dialect);
            }
        }
        // 指标字段（来自 source.indicatorFields，已在 enrich 阶段按请求顺序）
        if (ctx.getSource() != null && !CollectionUtils.isEmpty(ctx.getSource().getIndicatorFields())) {
            for (FieldMappingInfo f : ctx.getSource().getIndicatorFields()) {
                appendProjection(ctx, aliasMap, projection, usedFieldKeys, firstOwnerByFieldKey, f, dialect);
            }
        }
        // ptdate 分区字段，仅用于 timeRange WHERE 过滤，不参与外层 GROUP BY
        if (ctx.getPtdateField() != null) {
            String pk = ctx.getFieldKeyMap().get(ctx.getPtdateField().getBasicId());
            if (!isBlank(pk) && !usedFieldKeys.contains(pk)) {
                appendProjection(ctx, aliasMap, projection, usedFieldKeys, firstOwnerByFieldKey,
                        ctx.getPtdateField(), dialect);
            }
        }

        if (projection.isEmpty()) {
            throw new BizException("内层投影列为空，无法构建子查询");
        }
        return projection;
    }

    private void appendProjection(
            SqlBuildContext ctx,
            Map<Long, String> aliasMap,
            List<String> projection,
            Set<String> usedFieldKeys,
            Map<String, Long> firstOwnerByFieldKey,
            FieldMappingInfo f,
            SqlDialect dialect
    ) {
        if (f == null || f.getBasicId() == null) {
            return;
        }
        if (f.getTableId() == null || isBlank(f.getSrcField())) {
            throw new BizException("字段映射不完整, basicId=" + f.getBasicId());
        }
        String alias = aliasMap.get(f.getTableId());
        if (alias == null) {
            throw new BizException("字段所属表未在 FROM/JOIN 中注册, basicId=" + f.getBasicId()
                    + ", tableId=" + f.getTableId());
        }
        String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
        if (isBlank(fieldKey)) {
            throw new BizException("fieldKeyMap 未提供 field_key, basicId=" + f.getBasicId());
        }
        if (!usedFieldKeys.add(fieldKey)) {
            Long firstOwner = firstOwnerByFieldKey.get(fieldKey);
            throw new BizException("field_key 在内层 SELECT 中重复, field_key=" + fieldKey
                    + ", basicIds=[" + firstOwner + "," + f.getBasicId() + "]");
        }
        firstOwnerByFieldKey.put(fieldKey, f.getBasicId());

        String qualifiedCol = alias + "." + dialect.quote(f.getSrcField());
        boolean isPtdate = ctx.getPtdateField() != null
                && f.getBasicId().equals(ctx.getPtdateField().getBasicId());
        String srcExpr;
        if (!isBlank(f.getTimeGranularity())) {
            srcExpr = formatTimeGranularity(qualifiedCol, f.getSrcFieldType(), f.getDateFormat(),
                    f.getTimeGranularity(), dialect);
        } else {
            srcExpr = formatTimeExpression(qualifiedCol, f.getSrcFieldType(), f.getDateFormat(), dialect, isPtdate);
        }
        projection.add(srcExpr + " as " + dialect.quote(fieldKey));
    }

    /**
     * 时间字段输出格式化。
     * date → yyyy-MM-dd，datetime/timestamp → yyyy-MM-dd HH:mm:ss。
     * 非日期类型原样透传（但 dateFormat 指定的 varchar/int 也处理）。
     */
    private String formatTimeExpression(String qualifiedCol, String srcFieldType, String dateFormat,
                                         SqlDialect dialect, boolean isPtdate) {
        String type = TimeRangeConverter.normalizeFieldType(srcFieldType);
        boolean hasTime = "datetime".equals(type) || "timestamp".equals(type);
        if (!"date".equals(type) && !hasTime) {
            if ("yyyyMMddHHmmss".equals(dateFormat)) {
                qualifiedCol = strToDate(qualifiedCol, dateFormat, dialect);
                hasTime = true;
            } else if ("yyyy-MM-dd HH:mm:ss".equals(dateFormat)) {
                qualifiedCol = strToDate(qualifiedCol, dateFormat, dialect);
                hasTime = true;
            } else if ("yyyyMMdd".equals(dateFormat)) {
                qualifiedCol = strToDate(qualifiedCol, dateFormat, dialect);
            } else {
                return qualifiedCol;
            }
        }
        String d = dialect.name().toLowerCase();
        if (hasTime && !isPtdate) {
            if ("mysql".equals(d)) {
                return "DATE_FORMAT(" + qualifiedCol + ", '%Y-%m-%d %H:%i:%s')";
            }
            if ("oracle".equals(d) || "dm".equals(d)) {
                return "TO_CHAR(" + qualifiedCol + ", 'YYYY-MM-DD HH24:MI:SS')";
            }
            if ("hive".equals(d)) {
                return "DATE_FORMAT(" + qualifiedCol + ", 'yyyy-MM-dd HH:mm:ss')";
            }
            return "TO_CHAR(" + qualifiedCol + ", 'YYYY-MM-DD HH24:MI:SS')";
        }
        if ("mysql".equals(d)) {
            return "DATE_FORMAT(" + qualifiedCol + ", '%Y-%m-%d')";
        }
        if ("oracle".equals(d) || "dm".equals(d)) {
            return "TO_CHAR(" + qualifiedCol + ", 'YYYY-MM-DD')";
        }
        if ("hive".equals(d)) {
            return "DATE_FORMAT(" + qualifiedCol + ", 'yyyy-MM-dd')";
        }
        return "TO_CHAR(" + qualifiedCol + ", 'YYYY-MM-DD')";
    }

    /**
     * 时间粒度格式化：week → YYYYW周数, month → YYYY-MM, quarter → YYYYQ季度, year → YYYY。
     *
     * 非日期类型字段（varchar/int 存日期字符串）走字符串截取，
     * dateFormat 为 null 时兜底按 yyyy-MM-dd 处理。
     */
    private String formatTimeGranularity(String qualifiedCol, String srcFieldType, String dateFormat,
                                          String granularity, SqlDialect dialect) {
        String type = TimeRangeConverter.normalizeFieldType(srcFieldType);
        boolean isDateType = "date".equals(type) || "datetime".equals(type) || "timestamp".equals(type);
        if (!isDateType && !"week".equals(granularity)) {
            return formatGranularityBySubstring(qualifiedCol, dateFormat, granularity, dialect);
        }
        // week 粒度（字符串算 ISO 周复杂，走转日期路径）：dateFormat 缺失时兜底 yyyy-MM-dd
        String fmt = isBlank(dateFormat) ? "yyyy-MM-dd" : dateFormat;
        qualifiedCol = strToDate(qualifiedCol, fmt, dialect);
        String d = dialect.name().toLowerCase();
        switch (granularity) {
            case "week":
                // 用 ISO 年（IYYY/%x），与 ISO 周数（IW/%v）同一套规则，跨年周值自洽
                if ("mysql".equals(d)) return "CONCAT(DATE_FORMAT(" + qualifiedCol + ",'%x'),'W',DATE_FORMAT(" + qualifiedCol + ",'%v'))";
                if ("hive".equals(d)) return "CONCAT(DATE_FORMAT(" + qualifiedCol + ",'yyyy'),'W',DATE_FORMAT(" + qualifiedCol + ",'ww'))";
                return "TO_CHAR(" + qualifiedCol + ",'IYYY')||'W'||TO_CHAR(" + qualifiedCol + ",'IW')";
            case "month":
                if ("mysql".equals(d)) return "DATE_FORMAT(" + qualifiedCol + ",'%Y-%m')";
                if ("hive".equals(d)) return "DATE_FORMAT(" + qualifiedCol + ",'yyyy-MM')";
                return "TO_CHAR(" + qualifiedCol + ",'YYYY-MM')";
            case "quarter":
                if ("mysql".equals(d)) return "CONCAT(YEAR(" + qualifiedCol + "),'Q',QUARTER(" + qualifiedCol + "))";
                if ("hive".equals(d)) return "CONCAT(YEAR(" + qualifiedCol + "),'Q',QUARTER(" + qualifiedCol + "))";
                return "TO_CHAR(" + qualifiedCol + ",'YYYY')||'Q'||TO_CHAR(" + qualifiedCol + ",'Q')";
            case "year":
                if ("mysql".equals(d)) return "DATE_FORMAT(" + qualifiedCol + ",'%Y')";
                if ("hive".equals(d)) return "DATE_FORMAT(" + qualifiedCol + ",'yyyy')";
                return "TO_CHAR(" + qualifiedCol + ",'YYYY')";
            default:
                return qualifiedCol;
        }
    }

    /**
     * 字符串存日期的派生维度截取。
     * dateFormat 支持 yyyy-MM-dd（含 null 兜底）和 yyyyMMdd，其他格式回退 strToDate 路径。
     */
    private String formatGranularityBySubstring(String qualifiedCol, String dateFormat,
                                                String granularity, SqlDialect dialect) {
        String fmt = isBlank(dateFormat) ? "yyyy-MM-dd" : dateFormat.trim();
        String yearExpr;
        String monthExpr;
        if ("yyyy-MM-dd".equals(fmt)) {
            yearExpr = substrExpr(qualifiedCol, 1, 4, dialect);
            monthExpr = substrExpr(qualifiedCol, 6, 2, dialect);
        } else if ("yyyyMMdd".equals(fmt)) {
            yearExpr = substrExpr(qualifiedCol, 1, 4, dialect);
            monthExpr = substrExpr(qualifiedCol, 5, 2, dialect);
        } else {
            // 非常规格式回退：转日期再走 TO_CHAR
            return formatTimeGranularity(strToDate(qualifiedCol, fmt, dialect), "date", null,
                    granularity, dialect);
        }
        String d = dialect.name().toLowerCase();
        switch (granularity) {
            case "year":
                return yearExpr;
            case "month":
                if ("yyyy-MM-dd".equals(fmt)) {
                    return substrExpr(qualifiedCol, 1, 7, dialect);
                }
                return substrExpr(qualifiedCol, 1, 6, dialect);
            case "quarter":
                // 季度 = (月份+2)/3 整数除法: 01-03→1, 04-06→2, 07-09→3, 10-12→4
                String quarterCalc;
                if ("mysql".equals(d)) {
                    quarterCalc = "(" + monthExpr + " + 2) div 3";
                } else if ("oracle".equals(d) || "dm".equals(d)) {
                    quarterCalc = "trunc((to_number(" + monthExpr + ") + 2) / 3)";
                } else if ("hive".equals(d)) {
                    quarterCalc = "floor((cast(" + monthExpr + " as int) + 2) / 3)";
                } else {
                    quarterCalc = "(cast(" + monthExpr + " as int) + 2) / 3";
                }
                if ("mysql".equals(d) || "hive".equals(d)) {
                    return "concat(" + yearExpr + ", 'Q', " + quarterCalc + ")";
                }
                return yearExpr + " || 'Q' || " + quarterCalc;
            default:
                return qualifiedCol;
        }
    }

    /** 方言感知的字符串截取表达式。 */
    private String substrExpr(String qualifiedCol, int start, int length, SqlDialect dialect) {
        String d = dialect.name().toLowerCase();
        if ("oracle".equals(d) || "dm".equals(d)) {
            return "SUBSTR(" + qualifiedCol + "," + start + "," + length + ")";
        }
        return "SUBSTRING(" + qualifiedCol + "," + start + "," + length + ")";
    }

    /**
     * 将非标准格式的源字段转为标准日期表达式。
     */
    private String strToDate(String qualifiedCol, String dateFormat, SqlDialect dialect) {
        if (isBlank(dateFormat)) return qualifiedCol;
        String d = dialect.name().toLowerCase();
        if ("yyyy-MM-dd".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(" + qualifiedCol + ", '%Y-%m-%d')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + ", 'YYYY-MM-DD')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(" + qualifiedCol + ", 'yyyy-MM-dd'))";
            return "TO_DATE(" + qualifiedCol + ", 'YYYY-MM-DD')";
        }
        if ("yyyy-MM-dd HH:mm:ss".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(" + qualifiedCol + ", '%Y-%m-%d %H:%i:%s')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + ", 'YYYY-MM-DD HH24:MI:SS')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(" + qualifiedCol + ", 'yyyy-MM-dd HH:mm:ss'))";
            return "TO_DATE(" + qualifiedCol + ", 'YYYY-MM-DD HH24:MI:SS')";
        }
        if ("yyyyMMdd".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(" + qualifiedCol + ", '%Y%m%d')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + ", 'YYYYMMDD')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(" + qualifiedCol + ", 'yyyyMMdd'))";
            return "TO_DATE(" + qualifiedCol + ", 'YYYYMMDD')";
        }
        if ("yyyyMMddHHmmss".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(" + qualifiedCol + ", '%Y%m%d%H%i%s')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + ", 'YYYYMMDDHH24MISS')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(" + qualifiedCol + ", 'yyyyMMddHHmmss'))";
            return "TO_DATE(" + qualifiedCol + ", 'YYYYMMDDHH24MISS')";
        }
        if ("yyyyMM".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(CONCAT(" + qualifiedCol + ",'01'), '%Y%m%d')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + " || '01', 'YYYYMMDD')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(CONCAT(" + qualifiedCol + ",'01'), 'yyyyMMdd'))";
            return "TO_DATE(" + qualifiedCol + " || '01', 'YYYYMMDD')";
        }
        if ("yyyy-MM".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(CONCAT(" + qualifiedCol + ",'-01'), '%Y-%m-%d')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + " || '-01', 'YYYY-MM-DD')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(CONCAT(" + qualifiedCol + ",'-01'), 'yyyy-MM-dd'))";
            return "TO_DATE(" + qualifiedCol + " || '-01', 'YYYY-MM-DD')";
        }
        if ("yyyy".equals(dateFormat)) {
            if ("mysql".equals(d))  return "STR_TO_DATE(CONCAT(" + qualifiedCol + ",'-01-01'), '%Y-%m-%d')";
            if ("oracle".equals(d) || "dm".equals(d)) return "TO_DATE(" + qualifiedCol + " || '-01-01', 'YYYY-MM-DD')";
            if ("hive".equals(d))  return "FROM_UNIXTIME(UNIX_TIMESTAMP(CONCAT(" + qualifiedCol + ",'-01-01'), 'yyyy-MM-dd'))";
            return "TO_DATE(" + qualifiedCol + " || '-01-01', 'YYYY-MM-DD')";
        }
        return qualifiedCol;
    }

    private String buildOnClause(String factAlias, String dimAlias, ModelJoinInfo join, SqlDialect dialect) {
        if (CollectionUtils.isEmpty(join.getJoinKeys())) {
            throw new BizException("模型 join 缺少关联字段, factTableId=" + join.getFactTableId()
                    + ", dimTableId=" + join.getDimTableId());
        }
        StringBuilder on = new StringBuilder();
        for (int i = 0; i < join.getJoinKeys().size(); i++) {
            ModelJoinInfo.JoinKeyPair pair = join.getJoinKeys().get(i);
            if (isBlank(pair.getFactFieldKey()) || isBlank(pair.getDimFieldKey())) {
                throw new BizException("模型 join 字段为空, factTableId=" + join.getFactTableId()
                        + ", dimTableId=" + join.getDimTableId());
            }
            if (i > 0) {
                on.append(" and ");
            }
            on.append(factAlias).append(".").append(dialect.quote(pair.getFactFieldKey()))
                    .append(" = ")
                    .append(dimAlias).append(".").append(dialect.quote(pair.getDimFieldKey()));
        }
        return on.toString();
    }

    /**
     * 0=inner join, 1=left join, 2=right join，其它默认 left join 并打 warn。
     */
    private String joinKeyword(String joinType) {
        if (joinType == null || joinType.trim().isEmpty()) {
            return "left join";
        }
        return joinType.trim().toLowerCase();
    }

    private String qualifiedName(String dbName, String tableName, SqlDialect dialect) {
        if (isBlank(tableName)) {
            throw new BizException("表名为空");
        }
        if (isBlank(dbName)) {
            return dialect.quote(tableName);
        }
        return dialect.quote(dbName) + "." + dialect.quote(tableName);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
