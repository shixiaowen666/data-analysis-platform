package com.bi.enums;

import lombok.Getter;

/**
 * 表状态
 */
@Getter
public enum TableStatus {

    DELETED(0, "删除"),
    APPROVING(1, "审批中"),
    PENDING(2, "待上线"),
    ONLINE(3, "已上线"),
    OFFLINE(4, "已下线"),
    REJECTED(5, "已驳回");

    private final int code;
    private final String label;

    TableStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static TableStatus fromCode(int code) {
        for (TableStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown table status: " + code);
    }
}
