package com.metadata.engine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 采集方式
 */
@Getter
@AllArgsConstructor
public enum CollectTypeEnum {

    FULL("full", "全库采集"),
    SELECT("select", "选表采集");

    private final String code;
    private final String label;

    public static CollectTypeEnum ofCode(String code) {
        for (CollectTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        throw new IllegalArgumentException("不支持的采集方式: " + code);
    }
}
