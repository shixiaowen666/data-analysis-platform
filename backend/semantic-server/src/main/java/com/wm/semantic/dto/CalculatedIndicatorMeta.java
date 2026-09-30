package com.wm.semantic.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 计算指标公式元数据，由预处理阶段解析 {@code calculated_production} JSON 得到。
 *
 * <p>JSON 格式（管理端保存的样例）：
 * <pre>
 * {
 *   "displayFormula": "${A}-${B}",
 *   "formula": "{374}-{158}",
 *   "indicatorList": [
 *     {"id": 374, "letter": "A", "name": "管理资产总额"},
 *     {"id": 158, "letter": "B", "name": "贷款余额"}
 *   ]
 * }
 * </pre>
 */
@Data
public class CalculatedIndicatorMeta {

    /** 计算指标自身的 ID */
    private Long indicatorId;

    /** 后端计算公式，{id} 占位符，如 "{374}-{158}" */
    private String formula;

    /** 前端展示公式，${letter} 占位符，如 "${A}-${B}" */
    private String displayFormula;

    /** 子指标列表 */
    private List<SubIndicatorMeta> subIndicators;

    /** 运行时填充：计算指标自身的输出别名（来自 olap_basic_pro.english_name） */
    private String englishName;

    /**
     * 运行时填充：子指标 ID → 数据源选择后对应的 fieldKey（列别名），
     * 用于 SQL Builder 中将公式中的 {id} 替换为实际的列引用。
     * 例：{374: "amount_key", 158: "loan_key"}
     */
    private Map<Long, String> idToFieldKey;
}
