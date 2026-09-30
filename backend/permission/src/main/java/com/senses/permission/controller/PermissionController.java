package com.senses.permission.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.Permission;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.PermissionPageParam;
import com.senses.permission.model.param.PermissionParam;
import com.senses.permission.model.param.RolePermissionPageParam;
import com.senses.permission.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>
 *  功能权限管理
 * </p>
 *
 * @author liaojinlei
 * @since 2022-12-07
 */

@Slf4j
@RestController
@RequestMapping("/upc/permission")
@Tag(name = "功能权限管理 API")
public class PermissionController {

    @Resource
    private PermissionService permissionService;

    /**
     * 查询权限列表
     * @param pageParam
     * @return
     */
    @Operation(summary = "查询权限列表-只返回根权限")
    @PostMapping(value = "/listByPage")
    public ResultData<Page<Permission>> listByPage(@Parameter(description = "权限列表分页查询入参") @RequestBody PageParam<PermissionPageParam> pageParam){
        return ResultData.success(permissionService.listByPage(pageParam));
    }


    /**
     * 添加或修改功能权限
     * @param permissionParam
     * @return
     */
    @Operation(summary = "添加或修改功能权限")
    @PostMapping(value = "/saveOrUpdate")
    public ResultData saveOrUpdate(HttpServletRequest request, @RequestBody PermissionParam permissionParam){
        String userName = request.getHeader("username");
        return permissionService.saveOrUpdatePermission(permissionParam,userName);

    }

    /**
     * 查看权限详情
     * @param permissionId
     * @return
     */
    @Operation(summary = "查看功能权限详情")
    @GetMapping(value = "/getInfo")
    public ResultData<Permission> getInfo(@Parameter(description = "功能权限id",example = "1")@RequestParam Long permissionId){

        return ResultData.success(permissionService.getInfo(permissionId));
    }


    /**
     * 删除权限
     * @param permissionId
     * @return
     */
    @Operation(summary = "删除权限")
    @GetMapping(value = "/delete")
    public ResultData delete(HttpServletRequest request,@Parameter(description = "权限id",example = "1") @RequestParam Long permissionId){
        String userName = request.getHeader("username");
        return permissionService.logicDelete(permissionId,userName);
    }


    /**
     * 根据角色id获取所有权限以及角色选中状态
     * @param pageParam
     * @return
     */
    @Operation(summary = "根据角色id获取所有权限以及角色选中状态")
    @PostMapping(value = "/listSelectedPageByRoleId")
    public ResultData<Page<Permission>> listAllByRoleId(@Parameter(description = "分页参数，角色ID，必传",example = "1") @RequestBody PageParam<RolePermissionPageParam> pageParam){
        RolePermissionPageParam param = pageParam.getQueryParam();
        if(param.getRoleId()==null){
            return ResultData.fail("角色id不能为空");
        }
        return ResultData.success(permissionService.listSelectedPageByRoleId(pageParam));
    }

    /**
     * 获取下级权限
     * @param pid
     * @return
     */
    @Operation(summary = "获取下级权限")
    @GetMapping(value = "/listByPid")
    public ResultData<List<Permission>> listByPid(@Parameter(description = "功能权限id",example = "1")@RequestParam Long pid,@Parameter(description = "功能角色id",example = "1")@RequestParam(required = false) Long roleId){

        return ResultData.success(permissionService.listByPid(pid,roleId));
    }

    /**
     * 批量导入
     * @param multipartFile
     * @return
     */
    @Operation(summary = "批量导入")
    @PostMapping(value = "/export")
    public ResultData export(@Parameter(description = "批量导入excel文件",example = "1") MultipartFile multipartFile){
        return ResultData.success();
    }

    /**
     * 初始化权限
     * @return
     */
    @Operation(summary = "初始化权限")
    @PostMapping(value = "/initPermission")
    public ResultData initPermission(){
        return permissionService.initPermission();
    }
}

