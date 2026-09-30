package com.bi.enums;

import lombok.Getter;

/**
 * 指标类型枚举
 */
@Getter
public enum MetricType {

    ATOM("atom", "原子指标"),
    CALC("calc", "计算指标"),
    DERIVE("derive", "派生指标");

    private final String code;
    private final String desc;

    MetricType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MetricType fromCode(String code) {
        for (MetricType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown metric type: " + code);
    }
}
