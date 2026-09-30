package com.wm.semantic.support.sql.builder;

import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.CalculatedIndicatorMeta;
import com.wm.semantic.dto.DerivativeFilter;
import com.wm.semantic.dto.DerivativeIndicatorMeta;
import com.wm.semantic.dto.FieldMappingInfo;
import com.wm.semantic.dto.IndicatorMeta;
import com.wm.semantic.dto.SubIndicatorMeta;
import com.wm.semantic.support.sql.SqlDialect;
import com.wm.semantic.support.sql.model.SqlBuildContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外层聚合包装器（两层 SQL 结构的外层）
 *
 * 输入：BaseSqlBuilder 输出的内层子查询（已重命名为 field_key）
 * 输出形如：
 *   select f.`order_date_key` as `order_date_key`, f.`region_key` as `region_key`, sum(f.`amount_key`) as `amount_key`
 *   from (<inner subquery>) f
 *   where 1=1 and f.`region_key` in ('East','West')
 *   group by f.`order_date_key`, f.`region_key`
 *
 * 设计要点：
 * 1. 外层只引用 mainsrc.field_key，完全不感知物理别名
 * 2. 输出列名一律使用 field_key（维度和指标都是）
 * 3. 当前数据源未拥有的指标输出 0 占位，便于多源 UNION ALL 列对齐
 */
@Slf4j
@Component
public class SegmentSqlBuilder {

    /** 内层子查询的固定外层别名。 */
    public static final String OUTER_ALIAS = "f";

    @Resource
    private BaseSqlBuilder baseSqlBuilder;

    /**
     * 构建当前数据源的完整两层 SQL。
     *
     * @param ctx SQL 构建上下文，调用前需保证 fieldKeyMap / dimensionFields /
     *            requestIndicators / ownIndicatorIds / whereCondition 已就位
     * @return 完整的两层 SQL（外层 SELECT + (inner) f + WHERE + GROUP BY）
     */
    public String buildSegment(SqlBuildContext ctx) {
        if (ctx == null || ctx.getSource() == null) {
            throw new BizException("SqlBuildContext 缺失");
        }

        SqlDialect dialect = SqlDialect.from(ctx.getSource().getDbDialect());

        String inner = baseSqlBuilder.buildInnerSubquery(ctx);

        List<String> selectFields = new ArrayList<String>();
        appendDimensionSelect(selectFields, ctx, dialect);
        appendIndicatorSelect(selectFields, ctx, dialect);

        StringBuilder sql = new StringBuilder();
        sql.append("select ").append(String.join(", ", selectFields));
        sql.append(" from (").append(inner).append(") ").append(OUTER_ALIAS);
        if (!isBlank(ctx.getWhereCondition())) {
            sql.append(" where 1=1 ").append(normalizeWhereCondition(ctx.getWhereCondition()));
        }

        if (!CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            Set<Long> internalDims = ctx.getInternalDimIds();
            String groupBy = ctx.getDimensionFields().stream()
                    .filter(f -> internalDims == null || !internalDims.contains(f.getBasicId()))
                    .map(f -> OUTER_ALIAS + "." + dialect.quote(ctx.getFieldKeyMap().get(f.getBasicId())))
                    .collect(Collectors.joining(", "));
            // 维度全部为 internal（如无维度请求 + 衍生指标 filter 维度）时不拼 group by
            if (!groupBy.isEmpty()) {
                sql.append(" group by ").append(groupBy);
            }
        }

        if (!isBlank(ctx.getHavingCondition())) {
            sql.append(" having ").append(normalizeHavingCondition(ctx.getHavingCondition()));
        }

        String typeLabel = ctx.getSource().getType() != null && ctx.getSource().getType() == 2 ? "模型" : "单表";
        Long sourceId = ctx.getSource().getType() != null && ctx.getSource().getType() == 2
                ? ctx.getSource().getModelId() : ctx.getSource().getTableId();
        log.info("[SegmentSql] {} sourceId={}, dim={}, ind={}, where有{}, having有{}",
                typeLabel, sourceId,
                ctx.getDimensionFields() == null ? 0 : ctx.getDimensionFields().size(),
                ctx.getRequestIndicators() == null ? 0 : ctx.getRequestIndicators().size(),
                isBlank(ctx.getWhereCondition()) ? "否" : "是",
                isBlank(ctx.getHavingCondition()) ? "否" : "是");
        return sql.toString();
    }

    /**
     * 维度列：mainsrc.`field_key` as `field_key`。
     */
    private void appendDimensionSelect(List<String> selectFields, SqlBuildContext ctx, SqlDialect dialect) {
        if (CollectionUtils.isEmpty(ctx.getDimensionFields())) {
            return;
        }
        Set<Long> internalDims = ctx.getInternalDimIds();
        for (FieldMappingInfo f : ctx.getDimensionFields()) {
            // internal 维度（衍生指标 filter 维度）不作为输出列，仅在 CASE WHEN 表达式内引用
            if (internalDims != null && internalDims.contains(f.getBasicId())) {
                continue;
            }
            String fieldKey = ctx.getFieldKeyMap().get(f.getBasicId());
            if (isBlank(fieldKey)) {
                throw new BizException("fieldKeyMap 未提供维度 field_key, basicId=" + f.getBasicId());
            }
            selectFields.add(OUTER_ALIAS + "." + dialect.quote(fieldKey) + " as " + dialect.quote(fieldKey));
        }
    }

    /**
     * 指标列：拥有则 agg(mainsrc.`field_key`) as `field_key`，未拥有则 0 as `field_key`。
     */
    private void appendIndicatorSelect(List<String> selectFields, SqlBuildContext ctx, SqlDialect dialect) {
        if (CollectionUtils.isEmpty(ctx.getRequestIndicators())) {
            return;
        }
        Map<Long, String> aggFuncByFieldId = new LinkedHashMap<Long, String>();
        if (ctx.getSource() != null && !CollectionUtils.isEmpty(ctx.getSource().getIndicatorFields())) {
            for (FieldMappingInfo f : ctx.getSource().getIndicatorFields()) {
                if (f.getBasicId() != null) {
                    aggFuncByFieldId.put(f.getBasicId(), f.getAggFunc());
                }
            }
        }

        for (IndicatorMeta meta : ctx.getRequestIndicators()) {
            // 计算指标：公式列
            if (meta.isCalculated()) {
                CalculatedIndicatorMeta calcMeta = meta.getFormulaMeta();
                String alias = ctx.getFieldKeyMap().get(meta.getId());
                if (isBlank(alias)) {
                    alias = meta.getAlias();
                }
                boolean allOwned = true;
                for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
                    if (ctx.getOwnIndicatorIds() == null
                            || !ctx.getOwnIndicatorIds().contains(sub.getId())) {
                        allOwned = false;
                        break;
                    }
                }
                if (allOwned) {
                    String expr = buildCalculatedExpr(calcMeta, aggFuncByFieldId, dialect);
                    selectFields.add(expr + " as " + dialect.quote(alias));
                } else {
                    selectFields.add("0 as " + dialect.quote(alias));
                }
                continue;
            }

            // 衍生指标：CASE WHEN 过滤列
            if (meta.isDerivative()) {
                DerivativeIndicatorMeta derivMeta = meta.getDerivativeMeta();
                String alias = ctx.getFieldKeyMap().get(meta.getId());
                if (isBlank(alias)) {
                    alias = meta.getAlias();
                }
                boolean baseOwned = ctx.getOwnIndicatorIds() != null
                        && ctx.getOwnIndicatorIds().contains(derivMeta.getBaseIndicatorId());
                if (baseOwned) {
                    String expr = buildDerivativeExpr(derivMeta, aggFuncByFieldId, dialect);
                    selectFields.add(expr + " as " + dialect.quote(alias));
                } else {
                    selectFields.add("0 as " + dialect.quote(alias));
                }
                continue;
            }

            // 纯内部子指标：不输出 SQL 列（公式已内联聚合表达式）
            if (meta.isInternal()) {
                continue;
            }

            String fieldKey = ctx.getFieldKeyMap().get(meta.getId());
            boolean owned = ctx.getOwnIndicatorIds() != null && ctx.getOwnIndicatorIds().contains(meta.getId());
            String outputAlias = isBlank(fieldKey) ? meta.getAlias() : fieldKey;
            if (isBlank(outputAlias)) {
                throw new BizException("指标输出别名缺失, indicatorId=" + meta.getId());
            }
            if (owned) {
                if (isBlank(fieldKey)) {
                    throw new BizException("拥有指标但 fieldKeyMap 未提供, indicatorId=" + meta.getId());
                }
                String agg = aggFuncByFieldId.get(meta.getId());
                if (agg == null || agg.trim().isEmpty()) {
                    agg = meta.getAggFunc();
                }
                selectFields.add(buildAggregateExpr(agg, OUTER_ALIAS + "." + dialect.quote(fieldKey))
                        + " as " + dialect.quote(outputAlias));
            } else {
                selectFields.add("0 as " + dialect.quote(outputAlias));
            }
        }
    }

    /**
     * 将计算指标公式中的 {id} 替换为当前 segment 层的聚合表达式。
     * 例：{374} → COALESCE(sum(f.amount_key), 0)
     *
     * 空值/除零处理：占位符统一包 COALESCE(expr, 0)（NULL 按 0 参与运算），
     * 替换完成后由 FormulaProtector 对每个除法的分母整段包 NULLIF(expr, 0)，
     * 复合分母（如 ({a}+{b})/({c}+{d})）也能整体防护，除零返回 NULL 不报错。
     */
    private String buildCalculatedExpr(CalculatedIndicatorMeta calcMeta,
                                        Map<Long, String> aggFuncByFieldId,
                                        SqlDialect dialect) {
        String expr = calcMeta.getFormula();
        Map<Long, String> idToFieldKey = calcMeta.getIdToFieldKey();
        if (idToFieldKey == null) return expr;

        for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
            String fieldKey = idToFieldKey.get(sub.getId());
            if (fieldKey == null) continue;
            String agg = aggFuncByFieldId.getOrDefault(sub.getId(), "sum");
            String aggExpr = buildAggregateExpr(agg, OUTER_ALIAS + "." + dialect.quote(fieldKey));
            expr = expr.replace("{" + sub.getId() + "}", "COALESCE(" + aggExpr + ", 0)");
        }
        return FormulaProtector.protect(expr);
    }

    /**
     * 将衍生指标的 CASE WHEN 过滤表达式应用到基础指标列上。
     * 例：sum(CASE WHEN f.currency_key = '版本2' THEN f.amount_key ELSE 0 END)
     */
    private String buildDerivativeExpr(DerivativeIndicatorMeta derivMeta,
                                        Map<Long, String> aggFuncByFieldId,
                                        SqlDialect dialect) {
        String baseFieldKey = derivMeta.getBaseFieldKey();
        if (baseFieldKey == null) return "0";

        String agg = aggFuncByFieldId.getOrDefault(derivMeta.getBaseIndicatorId(), "sum");
        String baseExpr = OUTER_ALIAS + "." + dialect.quote(baseFieldKey);

        // 拼接 CASE WHEN 条件
        StringBuilder caseWhen = new StringBuilder("CASE");
        if (derivMeta.getFilters() != null && !derivMeta.getFilters().isEmpty()) {
            StringBuilder cond = new StringBuilder();
            for (DerivativeFilter df : derivMeta.getFilters()) {
                if (df.getFieldKey() == null) continue;
                if (cond.length() > 0) {
                    cond.append(" AND ");
                }
                cond.append(OUTER_ALIAS).append(".").append(dialect.quote(df.getFieldKey()));
                cond.append(" ").append(df.getOperator()).append(" ");
                cond.append(formatFilterValue(df));
            }
            if (cond.length() > 0) {
                caseWhen.append(" WHEN ").append(cond);
            }
        }
        caseWhen.append(" THEN ").append(baseExpr).append(" ELSE 0 END");
        return buildAggregateExpr(agg, caseWhen.toString());
    }

    private String formatFilterValue(DerivativeFilter df) {
        if (df.getValues() == null || df.getValues().isEmpty()) {
            return "''";
        }
        if (df.getValues().size() == 1) {
            return "'" + df.getValues().get(0).replace("'", "''") + "'";
        }
        // IN / NOT IN
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < df.getValues().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(df.getValues().get(i).replace("'", "''")).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildAggregateExpr(String aggFunc, String expr) {
        String func = aggFunc == null ? "sum" : aggFunc.trim().toLowerCase();
        if ("countdistinct".equals(func) || "count_distinct".equals(func)) {
            return "count(distinct " + expr + ")";
        }
        if ("count".equals(func) || "avg".equals(func) || "max".equals(func)
                || "min".equals(func) || "sum".equals(func)) {
            return func + "(" + expr + ")";
        }
        return "sum(" + expr + ")";
    }

    private String normalizeHavingCondition(String havingCondition) {
        String normalized = havingCondition.trim();
        if (normalized.toLowerCase(java.util.Locale.ROOT).startsWith("and ")) {
            return normalized.substring(4);
        }
        return normalized;
    }

    private String normalizeWhereCondition(String whereCondition) {
        String normalized = whereCondition.trim();
        if (normalized.startsWith("and ")) {
            return normalized;
        }
        if (normalized.startsWith("AND ")) {
            return "and " + normalized.substring(4);
        }
        return "and " + normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
