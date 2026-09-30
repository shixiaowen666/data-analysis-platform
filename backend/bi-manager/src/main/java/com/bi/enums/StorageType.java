package com.bi.enums;

import lombok.Getter;

/**
 * 存储类型
 */
@Getter
public enum StorageType {

    PHYSICAL("0", "物理表"),
    VIEW("1", "视图");

    private final String key;
    private final String label;

    StorageType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static StorageType fromKey(String key) {
        for (StorageType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return PHYSICAL;
    }
}
