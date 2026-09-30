package com.senses.permission.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.DataRole;
import com.senses.permission.entity.Group;
import com.senses.permission.entity.Role;
import com.senses.permission.entity.User;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.GroupAddUsersParam;
import com.senses.permission.model.param.GroupBindParam;
import com.senses.permission.model.param.GroupPageParam;
import com.senses.permission.model.param.GroupParam;
import com.senses.permission.model.vo.GroupRolesIdsVO;
import com.senses.permission.service.GroupService;
import com.senses.permission.service.GroupsUsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;


/**
 * <p>
 * 用户组管理
 * </p>
 *
 * @author shinan
 * @since 2022-12-08
 */

@Slf4j
@RestController
@RequestMapping("/upc/group")
@Tag(name = "用户组管理 API")
public class GroupController {

    @Resource
    private GroupService groupService;
    @Resource
    private GroupsUsersService groupsUsersService;

    /**
     * list
     *
     * @param groupName
     * @return true or false
     */
    @Operation(summary = "用户组列表")
    @GetMapping(value = "/list")
    public ResultData<List<Group>> list(
            @Parameter(description = "组名称") @RequestParam(required = false) String groupName) {
        return ResultData.success(groupService.listLikeGroupname(groupName));
    }

    /**
     * 分页查询用户组列表
     *
     * @param pageParam
     * @return
     */
    @Operation(summary = "分页查询用户组列表")
    @PostMapping(value = "/listByPage")
    public ResultData<Page<Group>> listByPage(@Parameter(description = "搜索值：角色id，组名称，创建人") @RequestBody PageParam<GroupPageParam> pageParam) {
        return ResultData.success(groupService.listByPage(pageParam));
    }


    /**
     * 查看详细信息
     *
     * @return true or false
     */
    @Operation(summary = "查看用户组信息")
    @GetMapping(value = "/getInfo")
    public ResultData<Group> getInfo(@Parameter(description = "用户组id", example = "1212") @RequestParam Integer groupId) {
        return ResultData.success(groupService.getInfo(groupId));
    }


    /**
     * 添加或修改用户组
     *
     * @param groupParam
     * @return true or false
     */
    @Operation(summary = "添加或修改")
    @PostMapping(value = "/saveOrUpdate")
    public ResultData saveOrUpdate(HttpServletRequest request, @RequestBody GroupParam groupParam) {
        try {
            String userName = request.getHeader("username");
            return groupService.saveOrUpdateGroup(groupParam, userName);
        } catch (Exception e) {
            log.error("", e);
            return ResultData.fail("保存错误：" + e.getMessage());
        }
    }


    /**
     * 添加组成员
     *
     * @param groupAddUsersParam
     * @return
     */
    @Operation(summary = "添加组成员")
    @PostMapping(value = "/addUsers")
    public ResultData addUsers(@Parameter(description = "用户id集合") @RequestBody GroupAddUsersParam groupAddUsersParam) {
        return groupService.addUsers(groupAddUsersParam);
    }


    /**
     * 查看已选组成员信息
     *
     * @return
     */
    @Operation(summary = "获取用户组下用户列表")
    @GetMapping(value = "/getUsers")
    public ResultData<List<User>> getUsers(@Parameter(description = "用户组id", example = "1") @RequestParam Long groupId) {
        return ResultData.success(groupsUsersService.getBindUserIds(groupId));
    }

    /**
     * 获取组已选功能和数据角色id集合
     *
     * @return
     */
    @Operation(summary = "获取用户组角色")
    @GetMapping(value = "/getRoles")
    public ResultData<GroupRolesIdsVO> getRoles(@Parameter(description = "用户组id", example = "1") @RequestParam Long groupId) {
        return ResultData.success(groupService.getRoleAndDataRoleIds(groupId));
    }


    /**
     * 删除用户组
     *
     * @param groupId
     * @return true or false
     */
    @Operation(summary = "删除用户组")
    @GetMapping(value = "/delete")
    public ResultData delete(HttpServletRequest request,@Parameter(description = "用户组id", example = "1212") @RequestParam Long groupId) {
        String userName = request.getHeader("username");
        return groupService.deleteGroup(groupId, userName);
    }

    /**
     * 授权
     *
     * @param groupBindParam
     * @return true or false
     */
    @Operation(summary = "给用户组授权")
    @PostMapping(value = "/bind")
    public ResultData bind(@RequestHeader("username")String username, @Parameter(description = "用户组授权参数") @RequestBody GroupBindParam groupBindParam) {
        return groupService.bind(groupBindParam.getGroupId(), groupBindParam.getRoleIds(), groupBindParam.getDataRoleIds(),username);
    }
    /**
     * 查看功能角色
     * @param groupId
     * @return
     */
    @Operation(summary = "查看分组授权功能角色树列表")
    @GetMapping(value = "/roleTreeList")
    public ResultData<List<TreeData<Role>>> treeList(
            @Parameter(description = "用户组Id") @RequestParam Long groupId){

        return ResultData.success(groupService.roleTreeList(groupId));
    }

    /**
     * 查看功能角色
     * @param groupId
     * @return
     */
    @Operation(summary = "查看分组授权数据功能角色树列表")
    @GetMapping(value = "/dataRoleTreeList")
    public ResultData<List<TreeData<DataRole>>> dataRoleTreeList(
            @Parameter(description = "用户组Id") @RequestParam Long groupId){

        return ResultData.success(groupService.dataRoleTreeList(groupId));
    }

    /**
     * 通过用户名获取用户组信息
     * @param usernames
     * @return
     */
    @Operation(summary = "通过用户名获取用户组信息")
    @PostMapping(value = "/getGroupsByUsernames")
    public ResultData<List<Group>> getGroupsByUsernames(
            @Parameter(description = "用户名集合") @RequestParam List<String> usernames){

        return ResultData.success(groupService.getGroupsByUsernames(usernames));
    }

    /**
     * 获取用户组树
     * @return
     */
    @Operation(summary = "获取用户组树")
    @GetMapping(value = "/getGroupUsersTree")
    public ResultData<List<TreeData<Object>>> getGroupUsersTree(){

        return ResultData.success(groupService.getGroupUsersTree());
    }

}

