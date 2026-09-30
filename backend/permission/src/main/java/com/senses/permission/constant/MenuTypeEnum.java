package com.senses.permission.constant;

/**
 * 菜单类型
 */
public enum MenuTypeEnum {
    OUT_MENU(0, "外部菜单"),
    IN_MENU(1, "内部菜单");

    private final int id;
    private final String val;

    MenuTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        MenuTypeEnum[] var1 = values();
        for (MenuTypeEnum o : var1) {
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
