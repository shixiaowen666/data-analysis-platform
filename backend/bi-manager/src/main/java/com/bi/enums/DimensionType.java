package com.bi.enums;

import lombok.Getter;

/**
 * 维度类型枚举
 */
@Getter
public enum DimensionType {

    STANDARD(1, "标准维"),
    MISC(2, "杂项维");

    private final int code;
    private final String desc;

    DimensionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DimensionType fromCode(int code) {
        for (DimensionType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown dimension type: " + code);
    }
}
