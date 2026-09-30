package com.bi.vo;

import lombok.Data;

@Data
public class MetricTreeItemVO {

    private Long id;

    private String name;

    /**
     * 英文名（english_name）
     */
    private String key;
}
