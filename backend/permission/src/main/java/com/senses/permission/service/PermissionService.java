package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.Permission;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.PermissionPageParam;
import com.senses.permission.model.param.PermissionParam;
import com.senses.permission.model.param.RolePermissionPageParam;

import java.util.List;

/**
 * 权限表;(permission)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface PermissionService extends IService<Permission>{

    Page<Permission> listByPage(PageParam<PermissionPageParam> pageParam);

    ResultData logicDelete(Long permissionId, String userName);

    Page<Permission> listSelectedPageByRoleId(PageParam<RolePermissionPageParam> pageParam);


    ResultData saveOrUpdatePermission(PermissionParam permissionParam,String userName);

    Permission getInfo(Long permissionId);

    List<Permission> listByPid(Long pid,Long roleId);

    List<Permission> getRootMenus(Long userId, Long appId);

    ResultData initPermission();
}