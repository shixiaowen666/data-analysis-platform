package com.bi.enums;

import lombok.Getter;

/**
 * 字段注册类型
 */
@Getter
public enum FieldRegisterType {

    DIMENSION("dim", "维度"),
    DIMENSION_ID("dimid", "维度ID"),
    INDEX("index", "指标"),
    PARTITION_FIELD("pf", "分区字段"),
    OTHER("other", "其他");

    private final String key;
    private final String label;

    FieldRegisterType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static FieldRegisterType fromKey(String key) {
        for (FieldRegisterType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return OTHER;
    }
}
