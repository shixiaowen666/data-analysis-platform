package com.wm.semantic.support.sql.builder;

import com.wm.semantic.dto.CalculatedIndicatorMeta;
import com.wm.semantic.dto.IndicatorMeta;
import com.wm.semantic.dto.SubIndicatorMeta;
import com.wm.semantic.support.sql.SqlDialect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 最外层包装器
 *
 * 多数据源 UNION ALL 后，在 unionTable 之上做一次二次聚合，
 * 把同一个维度组合下、来自不同数据源的指标列合并。
 *
 * 重构要点：尊重 IndicatorMeta.aggFunc，不再硬编码 sum。
 * 二次聚合的语义并不总等于原始聚合（例如 count -> sum 才对、avg 这种需要外层 sum/sum 才对），
 * 当前阶段为了保证主链路可用，规则简化如下：
 *  - sum / count / countDistinct 外层一律 sum（计数类二次聚合用 sum 是正确的）
 *  - max 外层 max
 *  - min 外层 min
 *  - avg 暂不在 UNION 场景下保证精确，外层用 sum（avg 的精确合并需配合 count 列重写）
 * 对应未来增强见 LOGIC-ARCHITECTURE §13。
 */
@Slf4j
@Component
public class OuterSqlWrapper {

    /**
     * 外层包装：select 维度 + 二次聚合指标 from (union) unionTable group by 维度。
     *
     * @param unionSql union all SQL
     * @param groupDimensions 维度字段名列表（与 segment select 列别名一致）
     * @param indicators 指标元数据列表
     * @return 最终 SQL
     */
    public String wrapFinalSql(String unionSql, List<String> groupDimensions, List<IndicatorMeta> indicators,
                               String dbDialect) {
        SqlDialect dialect = SqlDialect.from(dbDialect);
        List<String> selectFields = new ArrayList<String>();

        if (groupDimensions != null) {
            for (String dim : groupDimensions) {
                selectFields.add("unionTable." + dialect.quote(dim) + " as " + dialect.quote(dim));
            }
        }

        if (indicators != null) {
            for (IndicatorMeta meta : indicators) {
                if (meta.isCalculated()) {
                    CalculatedIndicatorMeta calcMeta = meta.getFormulaMeta();
                    String alias = meta.getAlias();
                    String expr = buildCalculatedOuterExpr(calcMeta, dialect);
                    selectFields.add(expr + " as " + dialect.quote(alias));
                    continue;
                }
                String outerFunc = pickOuterAggFunc(meta.getAggFunc());
                selectFields.add(outerFunc + "(unionTable." + dialect.quote(meta.getAlias()) + ") as " + dialect.quote(meta.getAlias()));
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("select\n  ").append(String.join(",\n  ", selectFields));
        sql.append("\nfrom\n(\n").append(indent(unionSql)).append("\n) unionTable");

        if (!CollectionUtils.isEmpty(groupDimensions)) {
            String groupBy = groupDimensions.stream()
                    .map(dim -> "unionTable." + dialect.quote(dim))
                    .collect(Collectors.joining(",\n  "));
            sql.append("\ngroup by\n  ").append(groupBy);
        }
        log.info("[OuterSql] dimCount={}, indCount={}",
                groupDimensions == null ? 0 : groupDimensions.size(),
                indicators == null ? 0 : indicators.size());
        return sql.toString();
    }

    /**
     * 外层二次聚合函数选择：
     * - max / min 保持原函数
     * - 其它（sum / count / countDistinct / avg / 未知）一律 sum
     */
    private String pickOuterAggFunc(String aggFunc) {
        if (aggFunc == null) {
            return "sum";
        }
        String func = aggFunc.trim().toLowerCase();
        if ("max".equals(func) || "min".equals(func)) {
            return func;
        }
        return "sum";
    }

    /**
     * 将计算指标公式中的 {id} 替换为多源外层聚合表达式。
     * 例：{374} → COALESCE(sum(unionTable.amount_key), 0)
     *
     * 空值/除零处理与 SegmentSqlBuilder.buildCalculatedExpr 一致：
     * 占位符统一包 COALESCE(expr, 0)，替换完成后由 FormulaProtector
     * 对每个除法的分母整段包 NULLIF(expr, 0)。
     */
    private String buildCalculatedOuterExpr(CalculatedIndicatorMeta calcMeta, SqlDialect dialect) {
        String expr = calcMeta.getFormula();
        Map<Long, String> idToFieldKey = calcMeta.getIdToFieldKey();
        if (idToFieldKey == null) return expr;

        for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
            String fieldKey = idToFieldKey.get(sub.getId());
            if (fieldKey == null) continue;
            String aggExpr = "sum(unionTable." + dialect.quote(fieldKey) + ")";
            expr = expr.replace("{" + sub.getId() + "}", "COALESCE(" + aggExpr + ", 0)");
        }
        return FormulaProtector.protect(expr);
    }

    private String indent(String sql) {
        return "  " + sql.replace("\n", "\n  ");
    }
}
