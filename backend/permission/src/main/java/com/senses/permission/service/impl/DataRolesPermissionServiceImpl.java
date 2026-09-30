package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.DataRolesPermission;
import com.senses.permission.entity.DataRolesPermissionDraft;
import com.senses.permission.entity.User;
import com.senses.permission.mapper.DataRolesPermissionDraftMapper;
import com.senses.permission.mapper.DataRolesPermissionMapper;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.UserPersonDataRoleParam;
import com.senses.permission.service.DataRolesPermissionService;
import com.senses.permission.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 数据角色与权限关系表;(data_roles_permission)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class DataRolesPermissionServiceImpl extends ServiceImpl<DataRolesPermissionMapper, DataRolesPermission> implements DataRolesPermissionService{
    @Resource
    private DataRolesPermissionMapper dataRolesPermissionMapper;

    @Resource
    private DataRolesPermissionDraftMapper dataRolesPermissionDraftMapper;


    @Lazy
    @Resource
    private UserService userService;

    public List<DataRolesPermission> getPrivileges(List<Long> dataRoleIds){
        if(CollectionUtils.isEmpty(dataRoleIds)){
            return new LinkedList<>();
        }
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.in("data_role_id", dataRoleIds);
        return dataRolesPermissionMapper.selectList(wrapper);
    }

    @Override
    public List<DataRolesPermission> getUserDatarolePermission(User user, UserPersonDataRoleParam userPersonDataRoleParam) {
        List<Long> allDeptIds = userService.getAllUserDept(user);
        List<DataRolesPermission> list = baseMapper.getUserDatarolePermission(user.getId(),userPersonDataRoleParam,allDeptIds);
        return list;
    }

    @Override
    public ResultData deleteDataPermissionByDsId(Long datasourceId) {
        UpdateWrapper<DataRolesPermission> deleteDataPermission = new UpdateWrapper<>();
        deleteDataPermission.eq("datasource_id",datasourceId);
        baseMapper.delete(deleteDataPermission);

        UpdateWrapper<DataRolesPermissionDraft> deleteDataPermissionDraft = new UpdateWrapper<>();
        deleteDataPermissionDraft.eq("datasource_id",datasourceId);
        dataRolesPermissionDraftMapper.delete(deleteDataPermissionDraft);
        return ResultData.success();
    }

}