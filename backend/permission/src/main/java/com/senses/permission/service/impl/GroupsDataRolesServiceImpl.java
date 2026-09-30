package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.GroupsDataRoles;
import com.senses.permission.mapper.GroupsDataRolesMapper;
import com.senses.permission.service.GroupsDataRolesService;
import org.springframework.stereotype.Service;

/**
 * 数据角色与部门关联表;(groups_data_roles)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class GroupsDataRolesServiceImpl extends ServiceImpl<GroupsDataRolesMapper, GroupsDataRoles> implements GroupsDataRolesService{
    @Resource
    private GroupsDataRolesMapper groupsDataRolesMapper;
    
}