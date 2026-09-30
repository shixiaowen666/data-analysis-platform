package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.RolesPermission;
import com.senses.permission.mapper.RolesPermissionMapper;
import com.senses.permission.service.RolesPermissionService;
import org.springframework.stereotype.Service;

/**
 * 角色与权限管理关联表;(roles_permission)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class RolesPermissionServiceImpl extends ServiceImpl<RolesPermissionMapper, RolesPermission> implements RolesPermissionService{
    @Resource
    private RolesPermissionMapper rolesPermissionMapper;
    
}