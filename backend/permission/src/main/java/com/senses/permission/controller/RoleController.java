package com.senses.permission.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.Permission;
import com.senses.permission.entity.Role;
import com.senses.permission.entity.User;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.*;
import com.senses.permission.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>
 *  功能角色管理
 * </p>
 *
 * @author liaojinlei
 * @since 2022-12-07
 */

@Slf4j
@RestController
@RequestMapping("/upc/role")
@Tag(name = "功能角色管理 API")
public class RoleController {

    @Resource
    private RoleService roleService;

    /**
     * 角色树列表
     * @param filterVal
     * @param appId
     * @param status
     * @return
     */
    @Operation(summary = "角色树列表")
    @GetMapping(value = "/treeList")
    public ResultData<List<TreeData<Role>>> treeList(
            @Parameter(description = "所属应用id") @RequestParam(required = false)Long appId,
            @Parameter(description = "过滤值 角色id，角色名称，创建人") @RequestParam(required = false)String filterVal,
            @Parameter(description = "状态 0禁用1启用") @RequestParam Integer status){

        return ResultData.success(roleService.treeList(appId,filterVal,status));
    }

    /**
     * 功能角色分页列表
     * @param pageParam
     * @return
     */
    @Operation(summary = "功能角色分页列表")
    @PostMapping(value = "/listByPage")
    public ResultData<Page<Role>> listByPage(@Parameter(description = "角色分页查询入参") @RequestBody PageParam<RolePageParam> pageParam){
        return ResultData.success(roleService.listByPage(pageParam));
    }

    /**
     * 添加或修改用户
     * @param roleParam
     * @return
     */
    @Operation(summary = "添加或修改角色")
    @PostMapping(value = "/saveOrUpdate")
    public ResultData saveOrUpdate(HttpServletRequest request, @RequestBody RoleParam roleParam){
        String userName = request.getHeader("username");
        return roleService.saveOrUpdateRole(roleParam,userName);

    }

    /**
     * 查看角色详情
     * @param roleId
     * @return
     */
    @Operation(summary = "查看角色信息")
    @GetMapping(value = "/getInfo")
    public ResultData<Role> getInfo(@Parameter(description = "角色id",example = "1")@RequestParam Long roleId){
        return ResultData.success(roleService.getInfoById(roleId));
    }
    /**
     * 根据角色id获取功能权限树
     * @return
     */
    @Operation(summary = "根据角色id获取功能权限树")
    @GetMapping(value = "/permissionTreeList")
    public ResultData<List<TreeData<Permission>>> permissionTreeList(
            @Parameter(description = "角色Id") @RequestParam(required = false) Long roleId,
            @Parameter(description = "过滤值 权限名称，标识") @RequestParam(required = false)String filterVal,
            @Parameter(description = "是否查询审批中权限 0或null否 1是") @RequestParam(required = false) Integer selectApprove) {
        return ResultData.success(roleService.permissionTreeList(roleId,filterVal,selectApprove));
    }
    /**
     * 根据角色id获取已选择功能权限id集合，如果角色是管理员，返回为空
     * @return
     */
    @Operation(summary = "根据角色id获取已选择功能权限id集合")
    @GetMapping(value = "/getPermissionIds")
    public ResultData<List<Long>> getPermissionIds(
            @Parameter(description = "角色Id") @RequestParam(required = false) Long roleId) {
        return ResultData.success(roleService.getPermissionIds(roleId));
    }
    /**
     * 删除角色
     * @param roleId
     * @return
     */
    @Operation(summary = "删除角色")
    @GetMapping(value = "/delete")
    public ResultData delete(HttpServletRequest request,@Parameter(description = "角色id",example = "1") @RequestParam Long roleId){
        String userName = request.getHeader("username");
        return roleService.deleteRole(roleId,userName);
    }
    /**
     * 获取授权用户
     * @param pageParam
     * @return true or false
     */
    @Operation(summary = "分页查询获取授权用户")
    @PostMapping(value = "/getUserListByPage")
    public ResultData<Page<User>> getUserListByPage(@Parameter(description = "获取授权用户分页入参") @RequestBody PageParam<RoleBindUserPageParam> pageParam){
        return ResultData.success(roleService.getUserListByPage(pageParam));
    }
    /**
     * 查询角色下权限列表
     * @param pageParam
     * @return
     */
    @Operation(summary = "查询角色下权限列表")
    @PostMapping(value = "/getPermissionListByPage")
    public ResultData<Page<Permission>> getPermissionListByPage(@Parameter(description = "查询角色下权限列表入参") @RequestBody PageParam<RolePermissionPageParam> pageParam){
        return ResultData.success(roleService.getPermissionListByPage(pageParam));
    }

    //根据id获取角色详情
    @GetMapping(value = "/getDetail")
    @Operation(summary = "根据id获取角色信息")
    public ResultData getRoleDetail(@Parameter(description = "角色id")@RequestParam(value = "id") Long id) {
        return  ResultData.success(roleService.getRoleDetail(id));
    }

    /**
     * 修改状态
     * @return
     */
    @Operation(summary = "修改状态")
    @PostMapping(value = "/updateStatus")
    public ResultData updateStatus(HttpServletRequest request,@Parameter(description = "状态0禁用1启用") @RequestBody UpdateStatusParam updateStatusParam){
        String userName = request.getHeader("username");
        return roleService.updateStatus(updateStatusParam.getId(),updateStatusParam.getStatus(),userName);
    }
}

