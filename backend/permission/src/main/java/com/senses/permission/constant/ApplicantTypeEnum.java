package com.senses.permission.constant;

/**
 * 申请人类型
 */
public enum ApplicantTypeEnum {
    CONTROL(0, "管理功能申请"),
    PERSON(1, "个人申请");

    private final int id;
    private final String val;

    ApplicantTypeEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        ApplicantTypeEnum[] var1 = values();
        for (ApplicantTypeEnum o : var1) {
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
