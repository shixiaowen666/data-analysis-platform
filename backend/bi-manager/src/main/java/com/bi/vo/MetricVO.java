package com.bi.vo;

import com.bi.dto.CalculatedProductionDTO;
import com.bi.dto.DerivativeProductionDTO;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 指标列表项 VO
 */
@Data
public class MetricVO {

    private Long id;

    private String code;

    private String standardName;

    private String chineseName;

    private String englishName;

    private String alias;

    /**
     * atom / calc / derive
     */
    private String type;

    /**
     * 聚合函数
     */
    private String fn;

    /**
     * 口径描述
     */
    private String caliber;

    /**
     * 单位类型：0-不指定 1-金额 2-百分比
     */
    private Integer unitType;

    /**
     * 单位：元 / % 等
     */
    private String unit;

    /**
     * 小数点位数
     */
    private Integer decimalPlaces;

    /**
     * 0-草稿 1-审批中 2-已上线 3-已下线
     */
    private Integer status;

    private String principalName;

    private LocalDateTime updatedAt;

    /**
     * 计算生产公式（计算指标必填）
     */
    private CalculatedProductionDTO calculateFormula;

    /**
     * 衍生生产公式（计算指标必填）
     */
    private DerivativeProductionDTO derivativeFormula;
}
