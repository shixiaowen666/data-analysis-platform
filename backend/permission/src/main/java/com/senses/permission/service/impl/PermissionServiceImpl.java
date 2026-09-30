package com.senses.permission.service.impl;

import jakarta.annotation.Resource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.senses.permission.constant.CommonStatusEnum;
import com.senses.permission.constant.MenuTypeEnum;
import com.senses.permission.constant.PermissionTypeEnum;
import com.senses.permission.entity.Application;
import com.senses.permission.entity.Permission;
import com.senses.permission.entity.Role;
import com.senses.permission.entity.RolesPermission;
import com.senses.permission.mapper.ApplicationMapper;
import com.senses.permission.mapper.PermissionMapper;
import com.senses.permission.mapper.RolesPermissionMapper;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.PermissionPageParam;
import com.senses.permission.model.param.PermissionParam;
import com.senses.permission.model.param.RolePageParam;
import com.senses.permission.model.param.RolePermissionPageParam;
import com.senses.permission.service.PermissionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限表;(permission)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService{
    @Resource
    private RolesPermissionMapper rolesPermissionMapper;

    @Resource
    private ApplicationMapper applicationMapper;

    @Override
    public Page<Permission> listByPage(PageParam<PermissionPageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        PermissionPageParam param = pageParam.getQueryParam();
        List<Permission> list = baseMapper.listByPage(param.getAppId(),null,param.getPermissionType(),param.getFilterVal());
        page.setRecords(list);
        Long total = baseMapper.count(param.getAppId(),null,param.getPermissionType(),param.getFilterVal());
        page.setTotal(total);
        return page;
    }

    @Override
    public ResultData logicDelete(Long permissionId, String userName) {
        Permission p = baseMapper.selectById(permissionId);
        List<Long> pids = new LinkedList<>();
        pids.add(p.getId());
        List<Long> allPermissionids = new LinkedList<>();
        allPermissionids.add(p.getId());
        while(true){
            List<Permission> permissions = baseMapper.listByPids(pids);
            if(!CollectionUtils.isEmpty(permissions)){
                List<Long> permissionIds = permissions.stream().map(e->e.getId()).collect(Collectors.toList());
                allPermissionids.addAll(permissionIds);
                pids = permissionIds;
            }else{
                break;
            }
        }
        List<RolesPermission> list = rolesPermissionMapper.listByPermissionIds(allPermissionids);
        if (!CollectionUtils.isEmpty(list)){
            return ResultData.fail("当前权限或其下级已存在角色绑定，不能删除");
        }
        UpdateWrapper<Permission> uw = new UpdateWrapper<>();
        uw.set("status",CommonStatusEnum.DELETE.getId());
        uw.set("modify_user",userName);
        uw.set("modify_time",new Date());
        uw.in("id",allPermissionids);
        this.update(uw);
        return ResultData.success();
    }

    @Override
    public Page<Permission> listSelectedPageByRoleId(PageParam<RolePermissionPageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        RolePermissionPageParam param = pageParam.getQueryParam();
        List<Permission> list = baseMapper.listSelectedPageByRoleId(param.getRoleId());
        page.setRecords(list);
        Long total = baseMapper.countSelectedPageByRoleId(param.getRoleId());
        page.setTotal(total);
        return page;
    }

    @Override
    public ResultData saveOrUpdatePermission(PermissionParam permissionParam,String userName) {
        if(StringUtils.isBlank(permissionParam.getName())){
            return ResultData.fail("权限名称不能为空");
        }
        if(permissionParam.getAppId()==null){
            return ResultData.fail("权限所属应用不能为空");
        }
        if(permissionParam.getType()==null){
            return ResultData.fail("权限类型不能为空");
        }else if(permissionParam.getType() == PermissionTypeEnum.MENU.getId()){
            if(permissionParam.getMenuType() == null){
                return ResultData.fail("菜单类型不能为空");
            }
            if(permissionParam.getMenuType() == MenuTypeEnum.OUT_MENU.getId() && StringUtils.isBlank(permissionParam.getExternalUrl())){
                return ResultData.fail("菜单地址不能为空");
            }
            if(permissionParam.getOrderNum() == null){
                return ResultData.fail("序号不能为空");
            }
        }else if(permissionParam.getType() == PermissionTypeEnum.BUTTON.getId()) {
            if (permissionParam.getButtonType() == null) {
                return ResultData.fail("按钮类型不能为空");
            }
        }

        Application application = applicationMapper.selectById(permissionParam.getAppId());

        Long pid;
        Permission permission = null;
        String sign = null;
        if(permissionParam.getId()!=null){
            permission = baseMapper.selectById(permissionParam.getId());
            pid = permission.getPid();
        }else{
            if(StringUtils.isBlank(permissionParam.getSign())){
                return ResultData.fail("权限标识不能为空");
            }
            pid = permissionParam.getPid()==null?0L:permissionParam.getPid();
        }
        Permission exist = baseMapper.selectExist(permissionParam.getAppId(),permissionParam.getType(),pid,permissionParam.getName());
        if(exist!=null
                && (permissionParam.getId()==null || !exist.getId().equals(permissionParam.getId()))){
            return ResultData.fail("同一个上级下，不能存在相同类型和名称的权限");
        }

        if(permissionParam.getId() ==null ){
            sign = generateSign(application,permissionParam);
            exist = baseMapper.selectExsitSign(permissionParam.getAppId(),permissionParam.getType(),pid,sign);
            if(exist!=null
                    && (permissionParam.getId()==null || !exist.getId().equals(permissionParam.getId()))){
                return ResultData.fail("同级下已存在相同标志");
            }
        }

        List<Long> allChangeAppPermissionIds = null;
        if(permission!=null){
            if(!permission.getAppId().equals(permissionParam.getAppId())){
                if(permission.getPid()!=0){
                    return ResultData.fail("非一级权限不能修改所属应用");
                }else{
                    //修改此权限下所有权限的所属应用
                    allChangeAppPermissionIds = new LinkedList<>();
                    List<Long> pids = new LinkedList<>();
                    pids.add(permission.getId());
                    while (true){
                        List<Permission> permissions = baseMapper.listByPids(pids);
                        if(CollectionUtils.isEmpty(permissions)){
                            break;
                        }else {
                            pids = permissions.stream().map(e->e.getId()).collect(Collectors.toList());
                            allChangeAppPermissionIds.addAll(pids);
                        }
                    }
                }
            }
        }else{
            permission = new Permission();
            permission.setCreatedUser(userName);
            permission.setCreatedTime(new Date());
            permission.setPid(pid);
        }
        permission.setStatus(CommonStatusEnum.TRUE.getId());
        permission.setAppId(permissionParam.getAppId());
        permission.setName(permissionParam.getName());
        if(permissionParam.getId()==null){
            permission.setSign(sign);
        }
        permission.setType(permissionParam.getType());
        permission.setModifyUser(userName);
        permission.setModifyTime(new Date());
        permission.setMenuType(permissionParam.getMenuType());
        permission.setButtonType(permissionParam.getButtonType());
        permission.setExternalUrl(permissionParam.getExternalUrl());
        permission.setOrderNum(permissionParam.getOrderNum());
        this.saveOrUpdate(permission);

        if(!CollectionUtils.isEmpty(allChangeAppPermissionIds)){
            UpdateWrapper<Permission> updateWrapper = new UpdateWrapper<>();
            updateWrapper.set("app_id",permissionParam.getAppId());
            updateWrapper.in("id",allChangeAppPermissionIds);
            baseMapper.update(null,updateWrapper);
        }
        permission.setAppName(application.getName());
        return ResultData.success(permission);
    }

    private String generateSign(Application application,PermissionParam permissionParam) {
        StringBuilder signSb = new StringBuilder();
        signSb.append(application.getCode());
        signSb.append("_");
        signSb.append(PermissionTypeEnum.valueOfId(permissionParam.getType()).name());
        Long pid = permissionParam.getPid()==null?0L:permissionParam.getPid();
        if(pid != 0L){
            Permission parentPermission = baseMapper.selectById(pid);
            String  parentSignChain = parentPermission.getSign();
            String[] parentSigns = parentSignChain.split("_");
            for(int i=2;i<parentSigns.length;i++){
                signSb.append("_");
                signSb.append(parentSigns[i]);
            }
        }
        signSb.append("_");
        signSb.append(permissionParam.getSign());
        return signSb.toString();
    }

    @Override
    public Permission getInfo(Long permissionId) {
        Permission permission = baseMapper.getInfoById(permissionId);
        return permission;
    }

    @Override
    public List<Permission> listByPid(Long pid,Long roleId) {
        List<Permission> list = baseMapper.trueListByPid(pid,roleId);
        return list;
    }

    @Override
    public List<Permission> getRootMenus(Long userId, Long appId) {
        return baseMapper.getRootMenus(userId,appId);
    }

    @Override
    public ResultData initPermission() {
        String filePath = "/Users/liaojinlei/Desktop/权限.txt";
        try{
            BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
            List<String> lines = bufferedReader.lines().collect(Collectors.toList());
            Map<String,Long> applicationIdMap = new HashMap<>();
            Map<String,Long> permissionIdMap = new HashMap<>();
            Map<String,Integer> permissionMaxOrderNumber = new HashMap<>();
            for(String line:lines){
                String[] lineStrArr = line.split(" ");
                String name = lineStrArr[0];
                String permissionChains = lineStrArr[1];
                String[] permissionChainsArr = permissionChains.split("_");
                String appCode = permissionChainsArr[0];
                String permissionType = permissionChainsArr[1];
                StringBuilder signSB = new StringBuilder();
                for(int i=2;i<permissionChainsArr.length;i++){
                    if (signSB.length()!=0){
                        signSB.append("_");
                    }
                    signSB.append(permissionChainsArr[i]);
                }
                Long appId = applicationIdMap.get(appCode);
                if(appId == null){
                    Application application = applicationMapper.selectByCode(appCode);
                    applicationIdMap.put(appCode,application.getId());
                    appId = application.getId();
                }
                Date date = new Date();
                Permission permission = new Permission();
                permission.setName(name);
                permission.setAppId(appId);
                permission.setSign(permissionChains);
                if(permissionType.equals("MENU")){
                    permission.setType(0);
                    permission.setMenuType(1);
                }else {
                    permission.setType(1);
                    permission.setButtonType(2);
                }

                permission.setStatus(1);
                permission.setCreatedUser("admin");
                permission.setModifyUser("admin");
                permission.setCreatedTime(date);
                permission.setModifyTime(date);
//                private Integer orderNum;
                Long pid = null;
                String parentName = null;
                if(permissionChainsArr.length == 3){
                    pid = 0L;
                    parentName = appCode;
                }else{
                    StringBuilder parentSign = new StringBuilder();
                    for(int i=2;i<permissionChainsArr.length-1;i++){
                        if (parentSign.length()!=0){
                            parentSign.append("_");
                        }
                        parentSign.append(permissionChainsArr[i]);
                    }
                    parentName = parentSign.toString();
                    pid = permissionIdMap.get(parentName);
                }
                permission.setPid(pid);
                Integer orderNum = permissionMaxOrderNumber.get(parentName);
                if(orderNum == null){
                    orderNum = 0;
                }
                permission.setOrderNum(orderNum);
                orderNum ++ ;
                permissionMaxOrderNumber.put(parentName,orderNum);
                baseMapper.insert(permission);
                Long id = permission.getId();
                permissionIdMap.put(signSB.toString(),id);

            }
        }catch (Exception e){
            log.error("",e);
            return ResultData.fail(e.getMessage());
        }

        return ResultData.success();
    }


}