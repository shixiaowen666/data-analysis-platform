package com.senses.permission.constant;

/**
 * 授权方式
 */
public enum BindTypeEnum {
    PERSON(0, "个人"),
    DEPT(1, "部门"),
    GROUP(2, "用户组");

    private final int id;
    private final String val;

    BindTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        BindTypeEnum[] var1 = values();
        for (BindTypeEnum o : var1) {
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
