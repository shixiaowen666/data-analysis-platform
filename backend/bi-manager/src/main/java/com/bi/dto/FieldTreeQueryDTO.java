package com.bi.dto;

import lombok.Data;

@Data
public class FieldTreeQueryDTO {

    /**
     * 中文名称模糊搜索
     */
    private String keyword;

    /**
     * 维度/指标英文名模糊搜索
     */
    private String key;
}
