package com.bi.vo;

import lombok.Data;

@Data
public class FieldTreeRow {

    private Long id;

    private String chineseName;

    private String englishName;

    private String olapLabelName;

    private String partitionField;

    private String timeDynamic;
}
