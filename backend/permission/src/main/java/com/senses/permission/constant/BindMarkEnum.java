package com.senses.permission.constant;

import lombok.Data;

/**
 * 申请权限审批分组标志
 */
public enum BindMarkEnum {
    UPC_BINDROLE_DEPT("upc_bindrole_dept","权限中心-绑定角色-部门授权"),
    UPC_BINDROLE_USER("upc_bindrole_user","权限中心-绑定角色-用户授权"),
    UPC_BINDROLE_GROUP("upc_bindrole_group","权限中心-绑定角色-用户组授权"),
    UPC_BINDROLE_PERMISSION("upc_bindrole_person","权限中心-绑定角色-个人功能角色申请"),
    UPC_BINDPERMISSION_DATAROLE("upc_bindpermission_datarole","权限中心-绑定权限-数据角色授权"),
    UPC_BINDPERMISSION_ROLE("upc_bindpermission_role","权限中心-绑定权限-功能角色授权"),
    UPC_BINDPERMISSION_PERSONDATAROLE("upc_bindpermission_persondatarole","权限中心-绑定权限-个人数据权限申请"),
    UPC_META_DATAPERMISSION("upc_meta_datapermission","即席查询-查询元数据-数据权限申请"),
    UPC_VIEWMETA_TABLEPERMISSION("upc_viewmeta_tablepermission","数据资产-元数据目录-表级数据权限申请");

    private final String code;
    private final String val;

    BindMarkEnum(String code, String val) {
        this.code = code;
        this.val = val;
    }

    public String getCode() {
        return code;
    }

    public String getVal() {
        return val;
    }
}
