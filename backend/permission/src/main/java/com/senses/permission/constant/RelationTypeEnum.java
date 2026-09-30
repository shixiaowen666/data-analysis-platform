package com.senses.permission.constant;

/**
 * 条件关系类型
 */
public enum RelationTypeEnum {
    OR("OR", "或"),
    AND("AND", "与");

    private final String id;
    private final String val;

    RelationTypeEnum(String id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(String id) {
        for (RelationTypeEnum type : values()) {
            if (type.getId().equals(id)) {
                return type.getVal();
            }
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public String getVal() {
        return val;
    }
}
