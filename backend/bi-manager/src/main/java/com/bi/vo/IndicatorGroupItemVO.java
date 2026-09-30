package com.bi.vo;

import lombok.Data;

@Data
public class IndicatorGroupItemVO {

    private Long id;

    private String itemType;

    private String itemTypeName;

    private Long objectId;

    private String objectName;

    private String displayName;

    private Integer displayOrder;

    private Integer isRequired;

    private Integer isDefaultVisible;

    private String formatType;

    private String unit;

    private String defaultSort;
}
