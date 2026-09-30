package com.senses.permission.constant;

/**
 * 变量状态 0审批中 1审批通过 2审批不通过
 */
public enum ApproveStatusEnum {
    WAIT_APPROVE(0, "审批中"),
    APPROVED(1, "审批通过"),
    NO_APPROVED(2, "审批不通过"),
    ROLLBACK(3, "已撤回");

    private final int id;
    private final String val;

    public static ApproveStatusEnum[] var1 = values();
    ApproveStatusEnum(int id, String val) {
        this.id = id;
        this.val = val;
    }

    public static String getVal(int id) {
        for (ApproveStatusEnum o : var1) {
            if (o.getId() == id) {
                return o.getVal();
            }
        }
        return null;
    }

    public static ApproveStatusEnum valueFromId(int id) {
        for (ApproveStatusEnum o : var1) {
            if (o.getId() == id) {
                return o;
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
