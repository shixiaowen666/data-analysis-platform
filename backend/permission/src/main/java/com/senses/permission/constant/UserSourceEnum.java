package com.senses.permission.constant;

/**
 * 用户来源
 */
public enum UserSourceEnum {
    OUT_SYNC(0, "外部同步"),
    IN_CREATE(1, "内部创建");

    private final int id;
    private final String val;

    UserSourceEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        UserSourceEnum[] var1 = values();
        for (UserSourceEnum o : var1) {
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
