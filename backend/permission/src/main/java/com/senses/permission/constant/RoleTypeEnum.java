package com.senses.permission.constant;

/**
 * 角色类型
 */
public enum RoleTypeEnum {
    APP_ADMIN(0, "应用管理员"),
    APP_WORKER(1, "应用成员");

    private final int id;
    private final String val;

    RoleTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        RoleTypeEnum[] var1 = values();
        for (RoleTypeEnum o : var1) {
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
