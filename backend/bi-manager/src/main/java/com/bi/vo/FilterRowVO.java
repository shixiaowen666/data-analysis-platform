package com.bi.vo;

import lombok.Data;

import java.util.List;

/**
 * 筛选器行
 */
@Data
public class FilterRowVO {

    /**
     * 筛选字段标签
     */
    private String label;

    /**
     * 筛选操作符
     */
    private String operator;

    /**
     * 筛选值
     */
    private String value;

    /**
     * 可选值列表（用于下拉选择）
     */
    private List<String> options;
}
