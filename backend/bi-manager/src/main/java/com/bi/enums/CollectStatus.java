package com.bi.enums;

import lombok.Getter;

/**
 * 采集状态枚举
 */
@Getter
public enum CollectStatus {

    UNCOLLECTED(0, "未采集"),
    FULL(1, "全量采集"),
    PARTIAL(2, "部分采集");

    private final int code;
    private final String desc;

    CollectStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CollectStatus fromCode(int code) {
        for (CollectStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown collect status: " + code);
    }
}
