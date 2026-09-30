package com.bi.enums;

import lombok.Getter;

/**
 * 单位类型枚举
 */
@Getter
public enum UnitType {

    NONE(0, "不指定"),
    AMOUNT(1, "金额"),
    PERCENT(2, "百分比");

    private final int code;
    private final String desc;

    UnitType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UnitType fromCode(int code) {
        for (UnitType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return NONE;
    }
}
