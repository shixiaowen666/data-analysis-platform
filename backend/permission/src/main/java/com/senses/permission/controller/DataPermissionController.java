package com.senses.permission.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.DataRolesPermission;
import com.senses.permission.entity.User;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.ReturnCode;
import com.senses.permission.model.dataroleVo.CheckPermissionVo;
import com.senses.permission.model.param.UserPersonDataRoleParam;
import com.senses.permission.model.vo.UserRolesIdsVO;
import com.senses.permission.service.DataRolesPermissionService;
import com.senses.permission.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/upc/dataPermission")
@Slf4j
@Tag(name = "数据鉴权 API")
public class DataPermissionController {

    @Resource
    private UserService userService;

    @Resource
    private DataRolesPermissionService dataRolesPermissionService;


    @Operation(summary = "鉴权接口")
    @PostMapping(value = "/checkDataPermission")
    public ResultData checkDataPermission(@RequestBody CheckPermissionVo checkPermissionVo) {
        List<String> requestPrivileges = checkPermissionVo.getRequestPrivileges();
        String username = checkPermissionVo.getUsername();
        log.info("user={} want requestPrivileges={}", checkPermissionVo.getUsername(), requestPrivileges);
        User user = userService.getByUsername(username);
        UserRolesIdsVO userRolesIdsVO = userService.getAllRoles(user);

        List<Long> allDataRoleIds = new ArrayList<>();
        allDataRoleIds.addAll(userRolesIdsVO.getDataRoleIds());
        allDataRoleIds.addAll(userRolesIdsVO.getCannotDataRoleIds());

        List<DataRolesPermission> privileges = dataRolesPermissionService.getPrivileges(allDataRoleIds);
        log.info("user={} has privileges={}", checkPermissionVo.getUsername(), privileges);

        if (privileges.size() == 0 || CollectionUtils.isEmpty(requestPrivileges)) {
            return ResultData.fail("no permission");
        }
        boolean flag = false;
        for (String entry : requestPrivileges) {
            DataRolesPermission requestPrivilege = buildDataPermission(entry);
//            log.info("requestPrivilege==={}", requestPrivilege);
            for (DataRolesPermission privilege : privileges) {
                flag = privilege.implies(requestPrivilege);
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

    private DataRolesPermission buildDataPermission(String entry) {
        String[] ary = entry.split("->");
        DataRolesPermission dp = new DataRolesPermission();
        dp.setEngine(ary[0]);
//        dp.setDatasourceName(ary[0]);
        dp.setDatabaseName(ary[1]);
        dp.setTableName(ary[2]);
        dp.setColumnName(ary[3]);
        dp.setAction(Integer.parseInt(ary[4]));
        return dp;
    }

    @Operation(summary = "获取个人和部门数据权限")
    @PostMapping(value = "/getUserDatarolePermission")
    public ResultData<List<DataRolesPermission>> getUserDatarolePermission(@RequestHeader("username")String username, @RequestBody UserPersonDataRoleParam userPersonDataRoleParam){
        User user = userService.getByUsername(username);
        return ResultData.success(dataRolesPermissionService.getUserDatarolePermission(user,userPersonDataRoleParam));
    }

    @Operation(summary = "根据数据源id删除数据权限")
    @GetMapping(value = "/deleteDataPermissionByDsId")
    public ResultData deleteDataPermissionByDsId(@RequestParam("datasourceId") Long datasourceId){
        return dataRolesPermissionService.deleteDataPermissionByDsId(datasourceId);
    }
}
