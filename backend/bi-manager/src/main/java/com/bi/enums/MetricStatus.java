package com.bi.enums;

import lombok.Getter;

/**
 * 指标状态枚举
 */
@Getter
public enum MetricStatus {

    DRAFT(0, "草稿"),
    APPROVING(1, "审批中"),
    ONLINE(2, "已上线"),
    OFFLINE(3, "已下线");

    private final int code;
    private final String desc;

    MetricStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MetricStatus fromCode(int code) {
        for (MetricStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown metric status: " + code);
    }
}
