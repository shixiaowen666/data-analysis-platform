package com.bi.vo;

import com.bi.dto.CalculatedProductionDTO;
import com.bi.dto.DerivativeProductionDTO;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.bi.dto.MetricMappingItem;

/**
 * 指标详情 VO
 */
@Data
public class MetricDetailVO {

    private Long id;

    private String standardName;

    private String chineseName;

    private String alias;

    private String englishName;

    private String abbreviation;

    private String classificationLabel;

    private String dataType;

    /**
     * 1-维度 2-指标
     */
    private Integer category;

    /**
     * 0-草稿 1-审批中 2-已上线 3-已下线
     */
    private Integer status;


    /**
     * 单位
     */
    private String unit;

    private Integer decimalPlaces;

    private String caliberDescription;

    private String thresholdRule;

    private String nullHandling;

    /**
     * 1-公开 2-私有 3-自定义
     */
    private Integer authorizeStrategy;

    /**
     * atom / calc / derive
     */
    private String type;



    /**
     * 依赖指标映射
     */
    private List<MetricMappingItem> mapping;


    /**
     * 计算生产公式（计算指标必填）
     */
    private CalculatedProductionDTO calculateFormula;

    /**
     * 衍生生产公式（计算指标必填）
     */
    private DerivativeProductionDTO derivativeFormula;

    private List<Map<String,Object>> mappingList;

    private OlapBasicProIndicatorVo extension;
}
