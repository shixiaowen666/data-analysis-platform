package com.bi.enums;

import lombok.Getter;

/**
 * 指标维度分类
 */
@Getter
public enum Category {

    DIMENSION(1, "维度"),
    METRIC(2, "指标");

    private final int code;
    private final String desc;

    Category(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
