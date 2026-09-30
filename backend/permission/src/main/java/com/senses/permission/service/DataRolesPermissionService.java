package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.DataRolesPermission;
import com.senses.permission.entity.User;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.UserPersonDataRoleParam;

import java.util.List;

/**
 * 数据角色与权限关系表;(data_roles_permission)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface DataRolesPermissionService extends IService<DataRolesPermission>{
    List<DataRolesPermission> getPrivileges(List<Long> dataRoleIds);

    List<DataRolesPermission> getUserDatarolePermission(User user, UserPersonDataRoleParam userPersonDataRoleParam);

    ResultData deleteDataPermissionByDsId(Long datasourceId);
}