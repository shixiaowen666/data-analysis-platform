package com.senses.permission.constant;

/**
 * 按钮类型
 */
public enum ButtonTypeEnum {
    IN_BUTTON(2, "内部链接按钮"),
    OUT_BUTTON(1, "外部链接按钮"),
    FUN_BUTTON(0, "功能性按钮");

    private final int id;
    private final String val;

    ButtonTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        ButtonTypeEnum[] var1 = values();
        for (ButtonTypeEnum o : var1) {
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
