package com.senses.permission.controller;


import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.ResourceManagement;
import com.senses.permission.entity.User;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.dataroleVo.CheckResourcePermissionVo;
import com.senses.permission.model.dataroleVo.ResourceManagementVo;
import com.senses.permission.model.param.ResourceManagementListQuery;
import com.senses.permission.model.param.ResourceManagementQuery;
import com.senses.permission.model.param.ResourcePermissionRequestDTO;
import com.senses.permission.service.ApplicationService;
import com.senses.permission.service.ResourceManagementService;
import com.senses.permission.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 资源管理表 前端控制器
 * </p>
 *
 * @author cxw
 * @since 2024-09-04
 */
@Tag(name = "资源管理API")
@RestController
@Slf4j
@RequestMapping("/upc/resource/management")
public class ResourceManagementController {

    @Resource
    private ResourceManagementService resourceManagementService;
    @Resource
    private UserService userService;
    @Resource
    private ApplicationService applicationService;


    @Operation(summary = "按组织资源管理分页列表")
    @PostMapping("/listByPage")
    public ResultData listByPage(@RequestBody PageParam<ResourceManagementQuery> query){
        return resourceManagementService.listByPage(query);
    }
    @Operation(summary = "按用户组资源管理分页列表")
    @PostMapping("/listGroupByPage")
    public ResultData listGroupByPage(@RequestBody PageParam<ResourceManagementQuery> query){
        return resourceManagementService.listGroupByPage(query);
    }

    @Operation(summary = "资源管理全量")
    @PostMapping("/listAll")
    public ResultData listAll(@RequestBody ResourceManagementListQuery query){
        return resourceManagementService.listAll(query);
    }

    @Operation(summary = "其他系统批量注册或修改资源权限")
    @PostMapping("/saveOrUpdateBatch")
    public ResultData saveOrUpdateBatch(@RequestBody List<ResourceManagementVo> resourceManagements){
        return resourceManagementService.saveOrUpdateBatch(resourceManagements);
    }

    // @Operation(summary = "编辑资源权限")
    // @GetMapping("/edit")
    // public ResultData edit(@RequestHeader("username")String username,@Parameter(description = "资源id") @RequestParam("id") Long id,
    //                              @Parameter(description = "权限层级(1-查看访问,2-编辑访问,3-完全访问)") @RequestParam("authLevel") Integer authLevel,
    //                              @Parameter(description = "下载权限 1-有 0-无") @RequestParam(required = false,value = "authDownload") Integer authDownload){
    //     User user = userService.getByUsername(username);
    //     return resourceManagementService.edit(user.getId(),id,authLevel,authDownload);
    // }

    @Operation(summary = "批量删除资源权限，其他平台删除资源时，同步删除对应的资源权限记录")
    @GetMapping("/deleteBatchByObjectId")
    public ResultData deleteBatch(@Parameter(description = "资源类型(0-报表 1-指标 2-仪表盘)") @RequestParam("resourceType") Integer resourceType,
                                  @Parameter(description = "源系统对象id") @RequestParam("objectId") Long objectId,
                                  @Parameter(description = "关联应用code") @RequestParam("applicationCode") String applicationCode,
                                  @Parameter(description = "操作人") @RequestParam("userId") Long userId){
        return resourceManagementService.deleteBatch(resourceType,objectId,applicationCode,userId);
    }

    @Operation(summary = "删除资源权限")
    @GetMapping("/delete")
    public ResultData delete(@RequestHeader("username")String username,@Parameter(description = "资源id") @RequestParam("id") Long id){
        User user = userService.getByUsername(username);
        return resourceManagementService.delete(user.getId(),id);
    }

    @Operation(summary = "查询用户的资源权限,授权给自身及从用户组继承的")
    @GetMapping("/getResourcePermissionByUserId")
    public List<Map<String,Object>> getResourcePermissionByUserId(String applicationCode, Integer resourceType, Long userId){
        return resourceManagementService.getResourcePermissionByUserId(applicationCode,resourceType,userId);
    }

    @Operation(summary = "通过id、类型、用户id等获取权限列表")
    @PostMapping("/getResourcePermissionByParam")
    public List<Map<String,Object>> getResourcePermissionByParam(@RequestBody ResourcePermissionRequestDTO resourcePermissionRequestDTO){
        return resourceManagementService.getResourcePermissionByParam(resourcePermissionRequestDTO);
    }

    @Operation(summary = "鉴权接口")
    @PostMapping(value = "/checkPermission")
    public ResultData checkPermission(@RequestBody CheckResourcePermissionVo resourcePermissionVo) {
        List<String> requestPrivileges = resourcePermissionVo.getRequestPrivileges();
        String username = resourcePermissionVo.getUserName();
        log.info("user={} want requestPrivileges={}", username, requestPrivileges);
        User user = userService.getByUsername(username);


        List<ResourceManagement> privileges = resourceManagementService.getPrivilegesByUserId(user.getId());
        log.info("user={} has privileges={}", username, privileges);

        if (privileges.size() == 0 || CollectionUtils.isEmpty(requestPrivileges)) {
            return ResultData.fail("no permission");
        }
        boolean flag = false;
        for (String entry : requestPrivileges) {
            ResourceManagement requestPrivilege = buildPermission(entry);
//            log.info("requestPrivilege==={}", requestPrivilege);
            for (ResourceManagement management : privileges) {
                flag = management.implies(requestPrivilege);
                if (flag) {
                    break;
                }
            }
            if (!flag) {
                String message = "no permission of " + entry;
                return ResultData.fail(message);
            }
        }
        return ResultData.success();
    }

    private ResourceManagement buildPermission(String entry) {
//        @Schema(description = "请求鉴权参数列表(系统编码->资源类型->对象id->访问权限->下载权限)")
        String[] ary = entry.split("->");
        ResourceManagement dp = new ResourceManagement();
        dp.setApplicationCode(ary[0]);
        dp.setResourceType(Integer.parseInt(ary[1]));
        dp.setObjectId(Long.parseLong(ary[2]));
        // dp.setAuthLevel(Integer.parseInt(ary[3]));
        dp.setAuthDownload(Integer.parseInt(ary[4]));
        return dp;
    }


}

