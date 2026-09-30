package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.*;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.RoleBindUserPageParam;
import com.senses.permission.model.param.RolePageParam;
import com.senses.permission.model.param.RoleParam;
import com.senses.permission.model.param.RolePermissionPageParam;
import com.senses.permission.model.vo.RoleInfoVO;
import com.senses.permission.model.vo.RoleVo;

import java.util.List;

/**
 * 角色表;(role)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface RoleService extends IService<Role>{
    ResultData deleteRole(Long roleId, String userName);

    Page<Role> listByPage(PageParam<RolePageParam> pageParam);

    ResultData saveOrUpdateRole(RoleParam roleParam,String userName);

    Page<User> getUserListByPage(PageParam<RoleBindUserPageParam> pageParam);

    Page<Permission> getPermissionListByPage(PageParam<RolePermissionPageParam> pageParam);

    RoleVo getRoleDetail(Long roleId);

    ResultData updateStatus(Long roleId, Integer status, String userName);

    List<TreeData<Role>> treeList(Long appId, String filterVal, Integer status);

    List<TreeData<Role>> installAppRoleTree(List<Application> applications, List<Role> roles);

    List<TreeData<Permission>> permissionTreeList(Long roleId, String filterVal,Integer selectApprove);

    List<Long> getPermissionIds(Long roleId);

    Role getInfoById(Long roleId);

    void confirmApprove(ApproveRecord approveRecord);

    List<RoleInfoVO> getAllUserRoleInfos(Long userId);
}