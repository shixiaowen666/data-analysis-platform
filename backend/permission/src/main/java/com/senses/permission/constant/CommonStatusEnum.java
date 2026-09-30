package com.senses.permission.constant;

/**
 * 通用状态
 */
public enum CommonStatusEnum {
    FALSE(0, "否，禁用等表示否定"),
    TRUE(1, "是，开启等表示肯定"),
    DELETE(2, "已删除");

    private final int id;
    private final String val;

    CommonStatusEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        CommonStatusEnum[] var1 = values();
        for (CommonStatusEnum o : var1) {
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
