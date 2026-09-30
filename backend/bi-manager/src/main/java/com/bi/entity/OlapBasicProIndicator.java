package com.bi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 指标扩展信息表
 */
@Data
@TableName("olap_basic_pro_indicator")
public class OlapBasicProIndicator extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableField("olap_basic_pro_id")
    private Long olapBasicProId;

    private String businessTheme;

    private String businessThemeName;

    @TableField("business_theme_id")
    private Long businessThemeId;

    private String businessLine;

    private String businessLineName;

    @TableField("business_process_id")
    private Long businessProcessId;

    private String businessRoot;

    private String businessRootName;

    /**
     * 修饰词 JSON 数组
     */
    @TableField("decorate_word")
    private String decoratedWord;

    private String timePeriod;

    private String timePeriodName;

    @TableField(value = "time_period_id", exist = false)
    private Long timePeriodId;

    /**
     * 口径描述
     */
    @TableField("caliber_description")
    private String caliberDescription;

    /**
     * 计算指标公式 JSON {formula,mapping[{symbol,metricId,code,name}]}
     */
    @TableField("calculated_production")
    private String calculatedProduction;

    /**
     * 计算指标公式复核
     */
    @TableField("calculated_production_check")
    private String calculatedProductionCheck;

    /**
     * 衍生指标公式 JSON
     */
    @TableField("derivative_production")
    private String derivativeProduction;

    /**
     * 衍生指标公式复核
     */
    @TableField("derivative_production_check")
    private String derivativeProductionCheck;

    /**
     * 聚合函数（原子指标，如 SUM, COUNT, AVG 等）
     */
    @TableField(value = "aggregate_function", exist = false)
    private String aggregateFunction;

    /**
     * 单位类型：0-不指定 1-金额 2-百分比（库中 varchar 存储，如 1/2/kwh）
     */
    private String unit;

    /**
     * 小数点位数：0-无 1-.0 2-.00 3-.000 4-.0000 默认2
     */
    private Integer decimalPlaces;

    /**
     * 关联指标 ID JSON 数组
     */

    private String relationIndicators;

    private String thresholdRule;

    private String nullHandling;

    private String monitorLevel;

    /**
     * 预警信息
     */
    private String warningInfo;

    /**
     * 1-公开 2-私有 3-自定义
     */
    @TableField("authorize_strategy")
    private Integer authorizeStrategy;

    private String oauthGroup;

    @TableField("visits_num")
    private Integer visitsNum;

    @TableField("workflow_id")
    private Long workflowId;

    private Long tenantId;
}
