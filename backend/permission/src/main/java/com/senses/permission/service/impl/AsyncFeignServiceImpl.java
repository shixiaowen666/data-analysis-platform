package com.senses.permission.service.impl;

import com.alibaba.fastjson.JSON;
import com.senses.permission.constant.ApproveStatusEnum;
import com.senses.permission.entity.*;
import com.senses.permission.mapper.*;
import com.senses.permission.model.Result;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.DsfAuditLog;
import com.senses.permission.service.AsyncFeignService;
import com.senses.permission.service.RoleService;
import com.senses.permission.service.client.FileHubService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@Service
public class AsyncFeignServiceImpl implements AsyncFeignService {
    @Resource
    private RoleMapper roleMapper;

    @Resource
    private DataRoleMapper dataRoleMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private GroupMapper groupMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PermissionMapper permissionMapper;
    // @Resource
    // private DsfService dsfService;

    @Resource
    private FileHubService fileHubService;

    @Override
    @Async
    public void addDeptBindRoleAuditLog(Dept dept, List<Long> roleIds, ApproveRecord approveRecord) {
        if(CollectionUtils.isEmpty(roleIds)){
            log.info("无部门绑定角色需要发送，deptId："+dept.getId());
            return;
        }
        log.info("发送部门绑定功能角色安全审计，deptId："+dept.getId()+" roleIds："+String.join("，",roleIds.stream().map(e->e.toString()).collect(Collectors.toList())));
        List<Role> roles = roleMapper.appRoleListByIds(roleIds);
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(roles.size());
        for(Role role:roles){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newDeptRoleAuditLog(dept,role,approveRecord);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveRoleLog(dsfAuditLogs,"发送部门绑定功能角色安全审计结果：");
    }

    @Override
    @Async
    public void addGroupBindRoleAuditLog(Group group, List<Long> roleIds, ApproveRecord approveRecord) {
        if(CollectionUtils.isEmpty(roleIds)){
            log.info("无分组绑定角色需要发送，groupId："+group.getId());
            return;
        }
        log.info("发送分组绑定功能角色安全审计，groupId："+group.getId()+" roleIds："+String.join("，",roleIds.stream().map(e->e.toString()).collect(Collectors.toList())));
        List<Role> roles = roleMapper.appRoleListByIds(roleIds);
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(roles.size());
        for(Role role:roles){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newGroupRoleAuditLog(group,role,approveRecord);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveRoleLog(dsfAuditLogs,"发送分组绑定功能角色安全审计结果：");
    }

    @Override
    @Async
    public void addPersonBindRoleAuditLog(User user, List<Long> roleIds, ApproveRecord approveRecord) {
        if(CollectionUtils.isEmpty(roleIds)){
            log.info("无个人绑定角色需要发送，userId："+user.getId());
            return;
        }
        log.info("发送个人绑定功能角色安全审计，userId："+ user.getId()+" roleIds："+String.join("，",roleIds.stream().map(e->e.toString()).collect(Collectors.toList())));
        List<Role> roles = roleMapper.appRoleListByIds(roleIds);
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(roles.size());
        for(Role role:roles){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newPersonRoleAuditLog(user,role,approveRecord);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveRoleLog(dsfAuditLogs,"发送个人绑定功能角色安全审计结果：");
    }

    @Override
    @Async
    public void addDeptBindDataRoleAuditLog(Dept dept, List<Long> dataRoleIds, ApproveRecord approveRecord) {
        if(CollectionUtils.isEmpty(dataRoleIds)){
            log.info("无部门绑定数据角色需要发送，deptId："+dept.getId());
            return;
        }
        log.info("发送部门绑定数据角色安全审计，deptId："+dept.getId()+" roleIds："+String.join("，",dataRoleIds.stream().map(e->e.toString()).collect(Collectors.toList())));
        List<DataRole> roles = dataRoleMapper.deptDataRoleListByIds(dataRoleIds);
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(roles.size());
        for(DataRole role:roles){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newDeptDataRoleAuditLog(dept,role,approveRecord);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveRoleLog(dsfAuditLogs,"发送部门绑定数据角色安全审计结果：");
    }

    @Override
    @Async
    public void addGroupBindDataRoleAuditLog(Group group,List<Long> dataRoleIds, ApproveRecord approveRecord) {
        if(CollectionUtils.isEmpty(dataRoleIds)){
            log.info("无分组绑定数据角色需要发送，groupId："+group.getId());
            return;
        }
        log.info("发送分组绑定数据角色安全审计，groupId："+group.getId()+" roleIds："+String.join("，",dataRoleIds.stream().map(e->e.toString()).collect(Collectors.toList())));
        List<DataRole> roles = dataRoleMapper.deptDataRoleListByIds(dataRoleIds);
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(roles.size());
        for(DataRole role:roles){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newGroupDataRoleAuditLog(group,role,approveRecord);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveRoleLog(dsfAuditLogs,"发送分组绑定数据角色安全审计结果：");
    }

    @Override
    @Async
    public void addPersonBindDataRoleAuditLog(User user, List<Long> dataRoleIds, ApproveRecord approveRecord) {
        if(CollectionUtils.isEmpty(dataRoleIds)){
            log.info("无个人绑定数据角色需要发送，userId："+user.getId());
            return;
        }
        log.info("发送个人绑定数据角色安全审计，userId："+ user.getId()+" roleIds："+String.join("，",dataRoleIds.stream().map(e->e.toString()).collect(Collectors.toList())));
        List<DataRole> roles = dataRoleMapper.deptDataRoleListByIds(dataRoleIds);
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(roles.size());
        for(DataRole role:roles){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newPersonDataRoleAuditLog(user,role,approveRecord);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveRoleLog(dsfAuditLogs,"发送个人绑定数据角色安全审计结果：");
    }

    @Override
    @Async
    public void addDataRolePermissionAuditLog(DataRole dataRole,List<DataRolesPermission> dataRolesPermissions, ApproveRecord approveRecord,Integer authRoleType) {
        if(CollectionUtils.isEmpty(dataRolesPermissions)){
            log.info("无数据角色绑定权限需要发送，dataRoleId："+dataRole.getId());
            return;
        }
        log.info("发送数据角色绑定权限安全审计，dataRoleId："+ dataRole.getId());
        List<DsfAuditLog> dsfAuditLogs = new ArrayList<>(dataRolesPermissions.size());
        for(DataRolesPermission dataRolesPermission:dataRolesPermissions){
            DsfAuditLog dsfAuditLog = DsfAuditLog.newDataRolesPermissionAuditLog(dataRolesPermission,approveRecord,authRoleType);
            dsfAuditLogs.add(dsfAuditLog);
        }
        // saveSelfRoleLog(dsfAuditLogs,"发送数据角色绑定权限安全审计结果：");
    }

    @Override
    @Async
    public void saveShareDirectory(String fileName) {
        try{
            Result result = fileHubService.saveShareDirectory(fileName);
            log.info("result = "+JSON.toJSONString(result));
        }catch (Exception e){
            log.error("",e);
        }
    }

    // private void saveRoleLog(List<DsfAuditLog> dsfAuditLogs,String resultFlag){
    //     log.info("发送信息："+JSON.toJSONString(dsfAuditLogs));
    //     ResultData resultData = dsfService.saveRoleLog(dsfAuditLogs);
    //     log.info(resultFlag+ JSON.toJSONString(resultData));
    // }

    // private void saveSelfRoleLog(List<DsfAuditLog> dsfAuditLogs,String resultFlag){
    //     log.info("发送信息："+JSON.toJSONString(dsfAuditLogs));
    //     ResultData resultData = dsfService.saveSelfRoleLog(dsfAuditLogs);
    //     log.info(resultFlag+ JSON.toJSONString(resultData));
    // }
}
