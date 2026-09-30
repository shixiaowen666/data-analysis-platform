package com.senses.permission.constant;

/**
 * 所属业务标志 upcUser，upcRole，upcDataRole，upcDept，upcUserGroup
 */
public enum ApproveFlowTagEnum {
    UPC_USER("upcUser"),
    UPC_PER_USER_ROLE("upcPerUserRole"),
    UPC_PER_DEPT_DATA_ROLE("upcPerDeptDataRole"),
    UPC_ROLE("upcRole"),
    UPC_DATA_ROLE("upcDataRole"),
    UPC_APPEND_DATA_ROLE_FROM_META("upcAppendDataRoleFromMeta"),
    UPC_APPEND_DATA_ROLE_FROM_IDE("upcAppendDataRoleFromIde"),
    UPC_DEPT("upcDept"),
    UPC_USER_GROUP("upcUserGroup");

    private final String val;
    public static ApproveFlowTagEnum[] var1 = values();
    ApproveFlowTagEnum(String val) {
        this.val = val;
    }

    public static ApproveFlowTagEnum valueFromVal(String flowTag) {
        for(ApproveFlowTagEnum approveFlowTagEnum:var1){
            if(approveFlowTagEnum.getVal().equals(flowTag)){
                return approveFlowTagEnum;
            }
        }
        return null;
    }

    public String getVal() {
        return this.val;
    }
}
