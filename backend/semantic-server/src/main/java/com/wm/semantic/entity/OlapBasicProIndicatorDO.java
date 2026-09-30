package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 指标扩展信息表
 */
@Data
@TableName("olap_basic_pro_indicator")
public class OlapBasicProIndicatorDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long olapBasicProId;
    private String businessTheme;
    private String businessThemeName;
    private Long businessThemeId;
    private String businessLine;
    private String businessLineName;
    private Long businessProcessId;
    private String businessRoot;
    private String businessRootName;
    private String decorateWord;
    private String timePeriod;
    private String timePeriodName;
    private String caliberDescription;
    private String calculatedProduction;
    private String calculatedProductionCheck;
    private String derivativeProduction;
    private String derivativeProductionCheck;
    /** 单位：元、万kWh、吨、% 等 */
    private String unit;
    /** 小数点位数：0-无 1-.0 2-.00 3-.000 4-.0000 */
    private Integer decimalPlaces;
    private String relationIndicators;
    private String thresholdRule;
    private String nullHandling;
    private String monitorLevel;
    private String warningInfo;
    private Integer authorizeStrategy;
    private String oauthGroup;
    private Integer visitsNum;
    private Long workflowId;
    private Long tenantId;
    private String createdBy;
    private Date createdAt;
    private String updatedBy;
    private Date updatedAt;
}
