package com.bi.enums;

import lombok.Getter;

/**
 * SQL 解析允许的字段类型
 */
@Getter
public enum AcceptedFieldType {

    DECIMAL("decimal"),
    INT("int"),
    DATE("date"),
    VARCHAR("varchar");

    @Getter
    private final String typeName;

    AcceptedFieldType(String typeName) {
        this.typeName = typeName;
    }

    public static boolean isAccepted(String dataType) {
        if (dataType == null) return false;
        String lower = dataType.toLowerCase();
        for (AcceptedFieldType t : values()) {
            if (lower.contains(t.typeName)) {
                return true;
            }
        }
        return false;
    }
}
