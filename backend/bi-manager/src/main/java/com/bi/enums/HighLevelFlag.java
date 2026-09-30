package com.bi.enums;

import lombok.Getter;

/**
 * 高基维标识枚举
 */
@Getter
public enum HighLevelFlag {

    HIGH(1, "高基维"),
    NORMAL(2, "普通维");

    private final int code;
    private final String desc;

    HighLevelFlag(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static HighLevelFlag fromCode(int code) {
        for (HighLevelFlag flag : values()) {
            if (flag.code == code) {
                return flag;
            }
        }
        return NORMAL;
    }
}
