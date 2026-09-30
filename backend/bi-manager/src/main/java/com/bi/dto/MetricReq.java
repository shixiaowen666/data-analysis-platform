package com.bi.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 新建/编辑指标请求
 */
@Data
public class MetricReq {

    /**
     * 标准名称（必填）
     */
    private String standardName;

    /**
     * 中文名称
     */
    @NotBlank(message = "指标标准名称不能为空")
    private String chineseName;

    /**
     * 英文名
     */
    @NotBlank(message = "指标英文名不能为空")
    private String englishName;

    /**
     * 别名（｜分隔）
     */
    private String alias;

    /**
     * 缩写
     */
    private String abbreviation;

    /**
     * 分类标签
     */
    private String classificationLabel;

    /**
     * 分类标签名称
     */
    private String classificationLabelName;

    /**
     * OLAP 标签
     */
    private String olapLabel;

    /**
     * OLAP 标签名称
     */
    private String olapLabelName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 口径描述（必填）
     */
    private String caliberDescription;

    /**
     * 单位类型：0-不指定 1-金额 2-百分比，默认 0
     */
    private String unit;

    /**
     * 小数点位数，不选默认 2
     */
    private Integer decimalPlaces;

    /**
     * 阈值规则
     */
    private String thresholdRule;

    /**
     * 空值处理
     */
    private String nullHandling;

    /**
     * 授权策略：1-公开 2-私有 3-自定义，默认 2
     */
    private Integer authorizeStrategy;

    /**
     * 负责人 ID（必填）
     */

    private Long principalId;

    /**
     * 负责人名字（必填）
     */

    private String principalName;

    /**
     * 负责人邮箱
     */
    private String principalEmail;

    /**
     * 审批人 ID（必填）
     */

    private Long approverId;

    /**
     * 审批人名字（必填）
     */

    private String approverName;

    /**
     * 审批人邮箱
     */
    private String approverEmail;

    /**
     * 业务主题 ID
     */
    private Long businessThemeId;

    /**
     * 业务主题名称
     */
    private String businessThemeName;

    /**
     * 业务线 ID
     */
    private Long businessLineId;

    /**
     * 业务线名称
     */
    private String businessLineName;

    /**
     * 业务过程 ID
     */
    private Long businessProcessId;

    /**
     * 词根 ID
     */
    private Long businessRootId;

    /**
     * 词根名称
     */
    private String businessRootName;

    /**
     * 指标类型：atom / calc / derive（必填）
     */
    @NotBlank(message = "指标类型不能为空")
    private String type;

    /**
     * 聚合函数（原子指标必填）
     */
    private String aggregateFunction;

    /**
     * 计算生产公式（计算指标必填）
     */
    private CalculatedProductionDTO calculateFormula;

    /**
     * 衍生生产公式（计算指标必填）
     */
    private DerivativeProductionDTO derivativeFormula;
}
