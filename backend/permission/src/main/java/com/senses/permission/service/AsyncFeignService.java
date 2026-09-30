package com.senses.permission.service;

import com.senses.permission.entity.*;
import java.util.List;

/**
 * 存放异步调用外部接口方法
 */
public interface AsyncFeignService {

    void addDeptBindRoleAuditLog(Dept dept, List<Long> roleIds,ApproveRecord approveRecord);

    void addGroupBindRoleAuditLog(Group group, List<Long> roleIds,ApproveRecord approveRecord);

    void addPersonBindRoleAuditLog(User user, List<Long> roleIds,ApproveRecord approveRecord);

    void addDeptBindDataRoleAuditLog(Dept dept, List<Long> dataRoleIds, ApproveRecord approveRecord);

    void addGroupBindDataRoleAuditLog(Group group, List<Long> dataRoleIds,ApproveRecord approveRecord);

    void addPersonBindDataRoleAuditLog(User user, List<Long> dataRoleIds,ApproveRecord approveRecord);

    void addDataRolePermissionAuditLog(DataRole dataRole, List<DataRolesPermission> dataRolesPermissions,ApproveRecord approveRecord,Integer authRoleType);

    void saveShareDirectory(String fileName);

}
