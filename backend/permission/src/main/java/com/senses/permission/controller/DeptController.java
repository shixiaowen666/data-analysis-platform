package com.senses.permission.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.DataRole;
import com.senses.permission.entity.Dept;
import com.senses.permission.entity.Role;
import com.senses.permission.entity.User;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.DeptBindParam;
import com.senses.permission.model.param.DeptParam;
import com.senses.permission.model.vo.DeptRolesIdsVO;
import com.senses.permission.model.vo.UserRolesIdsVO;
import com.senses.permission.model.vo.UserVO;
import com.senses.permission.service.DeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;


/**
 * <p>
 * 组织管理
 * </p>
 *
 * @author shinan
 * @since 2022-12-08
 */

@Slf4j
@RestController
@RequestMapping("/upc/dept")
@Tag(name = "组织管理 API")
public class DeptController {

    @Resource
    private DeptService deptService;

    /**
     * list
     *
     * @param deptName
     * @return true or false
     */
    @Operation(summary = "部门树列表")
    @GetMapping(value = "/treeList")
    public ResultData<List<TreeData<Dept>>> treeList(
            @Parameter(description = "部门名称") @RequestParam(required = false) String deptName) {
        return ResultData.success(deptService.treeList(deptName));
    }

    /**
     * deptUserTreeList
     *
     * @param deptName
     * @return true or false
     */
    @Operation(summary = "部门用户树列表")
    @GetMapping(value = "/deptUserTreeList")
    public ResultData<List<TreeData<User>>> deptUserTreeList(
            @Parameter(description = "部门名称") @RequestParam(required = false) String deptName) {
        return ResultData.success(deptService.deptUserTreeList(deptName));
    }

    @Operation(summary = "空间下部门用户树列表")
    @GetMapping(value = "/deptUserTreeListByTenantId")
    public ResultData<List<TreeData<User>>> deptUserTreeListByTenantId() {
        return ResultData.success(deptService.deptUserTreeListByTenantId());
    }

    /**
     * 新增部门
     *
     * @param dept
     * @return true or false
     */
    @Operation(summary = "添加")
    @PostMapping(value = "/saveOrUpdate")
    public ResultData saveOrUpdate(HttpServletRequest request, @RequestBody DeptParam dept) {

        String userName = request.getHeader("username");
        return deptService.saveOrUpdateDept(dept,userName);
    }



    /**
     * 查看详细信息
     *
     * @return true or false
     */
    @Operation(summary = "查看组织部门信息")
    @GetMapping(value = "/getInfo")
    public ResultData<Dept> getInfo(@Parameter(description = "部门id", example = "1212") @RequestParam Long deptId) {
        return ResultData.success(deptService.getInfo(deptId));
    }

    /**
     * 查看详细信息
     *
     * @return true or false
     */
    @Operation(summary = "查看组织部门信息")
    @GetMapping(value = "/getInfoByCode")
    public ResultData<Dept> getInfoByCode(@Parameter(description = "部门编码", example = "PAY") @RequestParam String code) {
        return ResultData.success(deptService.getInfoByCode(code));
    }

    /**
     * 删除组织部门
     *
     * @param deptId
     * @return true or false
     */
    @Operation(summary = "删除组织部门")
    @GetMapping(value = "/delete")
    public ResultData delete(HttpServletRequest request,@Parameter(description = "部门id", example = "1212") @RequestParam Long deptId) {
        String userName = request.getHeader("username");
        return deptService.deleteDept(deptId, userName);
    }

    /**
     * 授权
     * @param deptBindParam
     * @return true or false
     */
    @Operation(summary = "给部门授权")
    @PostMapping(value = "/bind")
    public ResultData bind(@RequestHeader("username") String username,@Parameter(description = "部门授权参数") @RequestBody DeptBindParam deptBindParam) {
        return ResultData.success(deptService.bind(deptBindParam.getDeptId(), deptBindParam.getRoleIds(), deptBindParam.getDataRoleIds(),username));
    }

    /**
     * 查看功能角色
     * @param deptId
     * @return
     */
    @Operation(summary = "查看部门授权功能角色树列表")
    @GetMapping(value = "/roleTreeList")
    public ResultData<List<TreeData<Role>>> roleTreeList(
            @Parameter(description = "部门Id") @RequestParam Long deptId){

        return ResultData.success(deptService.roleTreeList(deptId));
    }

    /**
     * 查看功能角色
     * @param deptId
     * @return
     */
    @Operation(summary = "查看部门授权数据角色树列表")
    @GetMapping(value = "/dataRoleTreeList")
    public ResultData<List<TreeData<DataRole>>> dataRoleTreeList(
            @Parameter(description = "部门Id") @RequestParam Long deptId){

        return ResultData.success(deptService.dataRoleTreeList(deptId));
    }

    /**
     * 查询部门已绑定角色id
     * @return
     */
    @Operation(summary = "获取部门已绑定角色id集合")
    @GetMapping(value = "/getRoles")
    public ResultData<DeptRolesIdsVO> getRoles(@Parameter(description = "部门id",example = "1") @RequestParam(value="deptId") Long deptId){
        return ResultData.success(deptService.getRoles(deptId));
    }

    /**
     * 查询子部门及该部门下的用户
     * @param deptId
     * @return
     */

    @Operation(summary = "获取子部门及该部门的用户")
    @GetMapping(value = "/getDeptsAndUsers")
    public ResultData<Map<String,Object>> getDeptsAndUsers(@Parameter(description = "部门id",example = "1") @RequestParam(value="deptId") Long deptId){
        return ResultData.success(deptService.getDeptsAndUsers(deptId));
    }

    /**
     * 查询该部门下的用户
     * @param deptId
     * @return
     */

    @Operation(summary = "获取该部门的用户")
    @GetMapping(value = "/getDeptUsers")
    public ResultData<List<UserVO>> getDeptUsers(@Parameter(description = "部门id",example = "1") @RequestParam(value="deptId") Long deptId){
        return ResultData.success(deptService.getDeptUsers(deptId));
    }

    @Operation(summary = "通过部门ids，批量获取部门信息")
    @PostMapping(value = "/getDeptsByIds")
    public ResultData<List<Dept>> getDeptsByIds(@RequestBody List<Long> deptIds){
        return ResultData.success(deptService.getDeptsByIds(deptIds));
    }

}

