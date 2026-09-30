package com.wm.semantic.dto;

import lombok.Data;

import java.util.List;

/**
 * 衍生指标公式元数据，由预处理阶段解析 {@code derivative_production} JSON 得到。
 *
 * <p>JSON 格式：
 * <pre>
 * {
 *   "baseIndicatorId": 1990,
 *   "baseIndicatorName": "剩余份额",
 *   "filters": [
 *     {"dimensionId": 1641, "operator": "=", "values": ["版本2"]}
 *   ]
 * }
 * </pre>
 */
@Data
public class DerivativeIndicatorMeta {

    /** 衍生指标自身的 ID */
    private Long indicatorId;

    /** 基础指标 ID */
    private Long baseIndicatorId;

    /** 基础指标名称 */
    private String baseIndicatorName;

    /** 维度过滤条件列表，多个之间 AND 关系 */
    private List<DerivativeFilter> filters;

    /** 运行时填充：基础指标对应的 fieldKey */
    private String baseFieldKey;
}
