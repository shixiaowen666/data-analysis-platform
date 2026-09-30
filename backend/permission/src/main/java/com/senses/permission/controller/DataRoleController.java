package com.senses.permission.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.DataRole;
import com.senses.permission.entity.User;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.dataroleVo.DataPermissionQueryCriteria;
import com.senses.permission.model.dataroleVo.DataRoleQueryCriteria;
import com.senses.permission.model.dataroleVo.DataRoleRelationVo;
import com.senses.permission.model.dataroleVo.DataRoleVo;
import com.senses.permission.model.param.DataRoleBindUserPageParam;
import com.senses.permission.model.param.DataRolePermissionDetailParam;
import com.senses.permission.model.param.RoleBindUserPageParam;
import com.senses.permission.service.DataRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/upc/dataRole")
@Tag(name = "数据角色管理 API")
public class DataRoleController {
    @Autowired
    DataRoleService dataRoleService;


    //获取角色列表（申请权限数据）
    @GetMapping(value = "/all/apply")
    @Operation(summary = "获取简单角色列表")
    public ResultData getDataRoles() {
        return  dataRoleService.getDataRoles();
    }

    //获取角色列表
    @PostMapping(value = "/all")
    @Operation(summary = "获取角色列表/分页")
    public ResultData getDataRoles(@RequestBody PageParam<DataRoleQueryCriteria> criteria) {
        return dataRoleService.getDataRoles(criteria);
    }

    @PostMapping(value = "/getDataPermissionListByPage")
    @Operation(summary = "获取角色权限列表/分页")
    public ResultData getDataPermissionListByPage(@RequestBody PageParam<DataPermissionQueryCriteria> criteria) {
        return dataRoleService.getDataPermissionListByPage(criteria);
    }

    //根据id获取角色信息
    @PostMapping(value = "/getDetail")
    @Operation(summary = "根据id获取角色信息")
    public ResultData<DataRole> getDataRoleDetail(@RequestBody DataRolePermissionDetailParam dataRolePermissionDetailParam) {
        return  dataRoleService.getDataRoleDetail(dataRolePermissionDetailParam);
    }

    //根据角色id切换角色状态
    @GetMapping(value = "/dataRoleChangeStatus")
    @Operation(summary = "根据角色id切换角色状态")
    public ResultData dataRoleChangeStatus(@RequestHeader("username")String username,@Parameter(description = "数据角色id")@RequestParam(value = "id") Long id,
                                           @Parameter(description = "数据角色状态，启用是1禁用是0")@RequestParam(value = "status") Integer status) {

        return  dataRoleService.dataRoleChangeStatus(id,status, username);
    }

    //创建角色
    @PostMapping(value = "/create")
    @Operation(summary = "创建数据角色(带权限列表)")
    public ResultData create(@RequestHeader("username")String username, @RequestBody DataRoleVo resources) {
        resources.setCreatedUser(username);
        resources.setModifyUser(username);
        return dataRoleService.create(resources);
    }

    //删除角色的权限信息
    @Operation(summary = "修改页面：删除角色的权限信息")
    @PostMapping(value = "/deleteDataRolePermissions")
    public ResultData deleteDataRolePermissions(@RequestHeader("username")String username,
                                                @Parameter(description = "权限id列表入参") @RequestParam List<Long> permisssionIds){
        return dataRoleService.deleteDataRolePermissions(username,permisssionIds);
    }
    //修改角色信息
    @PostMapping(value = "/update")
    @Operation(summary = "修改页面：修改角色及权限列表的增改")
    public ResultData update(@RequestHeader("username")String username,@RequestBody DataRoleVo resources) {
        resources.setModifyUser(username);
        return dataRoleService.overwriteDateRole(resources);
    }

    //根据id删除角色信息
    @GetMapping(value = "delete")
    @Operation(summary = "根据id删除角色信息")
    public ResultData deleteDataRole(@Parameter(description = "数据角色id")@RequestParam(value = "id") Long id) {
        return dataRoleService.deleteDataRole(id);
    }


    //为用户组，用户，部门授权或解除授权
    @PostMapping(value = "/bind")
    @Operation(summary = "角色授权绑定")
    public ResultData bind( @RequestBody DataRoleRelationVo resources) {
        //                授权方式0个人授权1部门授权2用户组授权
        return dataRoleService.bind(resources);
    }

    /**
     * 分页获取已授权用户
     * @param pageParam
     * @return true or false
     */
    @Operation(summary = "分页获取已授权用户")
    @PostMapping(value = "/getBindUserListByPage")
    public ResultData getBindUserListByPage(@Parameter(description = "获取授权用户分页入参") @RequestBody PageParam<DataRoleBindUserPageParam> pageParam){
        return dataRoleService.getBindUserListByPage(pageParam);
    }

    /**
     * 数据角色树列表
     * @param userId
     * @param deptId
     * @param filterVal
     * @param status
     * @return
     */
    @Operation(summary = "数据角色树列表")
    @GetMapping(value = "/treeList")
    public ResultData<List<TreeData<DataRole>>> treeList(
            @RequestHeader(value = "username")String username,
            @Parameter(description = "用户id") @RequestParam(required = false) Long userId,
            @Parameter(description = "所属部门id") @RequestParam(required = false) Long deptId,
            @Parameter(description = "过滤值 角色id，角色名称，创建人") @RequestParam(required = false) String filterVal,
            @Parameter(description = "状态 0禁用 1启用") @RequestParam Integer status){

        return ResultData.success(dataRoleService.treeList(username,userId,deptId,filterVal,status));
    }

    /**
     * 数据角色整树列表
     * @param deptId
     * @param filterVal
     * @param selectApprove
     * @param status
     * @return
     */
    @Operation(summary = "数据角色整树列表")
    @GetMapping(value = "/allTreeList")
    public ResultData<List<TreeData<DataRole>>> allTreeList(
            @Parameter(description = "所属部门id") @RequestParam(required = false) Long deptId,
            @Parameter(description = "过滤值 角色id，角色名称，创建人") @RequestParam(required = false) String filterVal,
            @Parameter(description = "是否查询审批中角色 0或null否 1是") @RequestParam(required = false) Integer selectApprove,
            @Parameter(description = "状态 0禁用 1启用") @RequestParam Integer status){

        return ResultData.success(dataRoleService.treeList(null,null,deptId,filterVal,status));
    }
}
