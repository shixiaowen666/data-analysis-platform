package com.bi.vo;

import lombok.Data;

@Data
public class CompatibleFieldRow {

    private Long id;

    private String code;

    private String chineseName;

    private String englishName;

    private Integer dimensionType;

    private String partitionField;

    private String timeDynamic;

    private String calculatedProduction;

    private String derivativeProduction;

    private String unit;
}
