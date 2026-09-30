package com.senses.permission.constant;

/**
 * 向审计中心发送的申请类型
 */
public enum AuthRoleTypeEnum {
    PERSON(0, "个人申请"),
    CONTROL(1, "管理员授权");

    private final int id;
    private final String val;

    AuthRoleTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        AuthRoleTypeEnum[] var1 = values();
        for (AuthRoleTypeEnum o : var1) {
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
