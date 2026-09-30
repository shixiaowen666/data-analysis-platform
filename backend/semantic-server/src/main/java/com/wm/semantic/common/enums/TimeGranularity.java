package com.wm.semantic.common.enums;

import lombok.Getter;

/**
 * 时间派生维度粒度，由 englishName 前缀匹配决定。
 */
@Getter
public enum TimeGranularity {

    WEEK("week"),
    MONTH("month"),
    QUARTER("quarter"),
    YEAR("year");

    private final String value;

    TimeGranularity(String value) {
        this.value = value;
    }

    public static TimeGranularity fromValue(String value) {
        if (value == null) return null;
        for (TimeGranularity g : values()) {
            if (g.value.equals(value)) return g;
        }
        return null;
    }
}
