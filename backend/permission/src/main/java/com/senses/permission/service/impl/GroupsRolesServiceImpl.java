package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.GroupsRoles;
import com.senses.permission.mapper.GroupsRolesMapper;
import com.senses.permission.service.GroupsRolesService;
import org.springframework.stereotype.Service;

/**
 * 角色与部门关联表;(groups_roles)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class GroupsRolesServiceImpl extends ServiceImpl<GroupsRolesMapper, GroupsRoles> implements GroupsRolesService{
    @Resource
    private GroupsRolesMapper groupsRolesMapper;
    
}