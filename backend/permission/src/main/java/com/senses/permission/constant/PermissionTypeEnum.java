package com.senses.permission.constant;

/**
 * 功能类型
 */
public enum PermissionTypeEnum {
    MENU(0, "菜单"),
    BUTTON(1, "按钮"),
    SOURCE(2, "链接");

    private final int id;
    private final String val;
    private static PermissionTypeEnum[] var1 = values();

    PermissionTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        for (PermissionTypeEnum o : var1) {
            if (o.getId() == id) {
                return o.getVal();
            }
        }
        return null;
    }

    public static PermissionTypeEnum valueOfId(int id){
        for (PermissionTypeEnum o : var1) {
            if (o.getId() == id) {
                return o;
            }
        }
        return null;
    };
    public int getId() {
        return this.id;
    }

    public String getVal() {
        return this.val;
    }
}
