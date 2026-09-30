package com.senses.permission.constant;

/**
 * 数据角色类型
 */
public enum DataRoleTypeEnum {
    NORMAL(0, "普通数据角色"),
    PERSON(1, "个人数据角色");

    private final int id;
    private final String val;

    DataRoleTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        DataRoleTypeEnum[] var1 = values();
        for (DataRoleTypeEnum o : var1) {
            if (o.getId() == id) {
                return o.getVal();
            }
        }
        return null;
    }

    public int getId() {
        return this.id;
    }

    public String getVal() {
        return this.val;
    }
}
