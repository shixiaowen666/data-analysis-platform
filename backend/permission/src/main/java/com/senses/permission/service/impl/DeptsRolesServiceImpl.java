package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.DeptsRoles;
import com.senses.permission.mapper.DeptsRolesMapper;
import com.senses.permission.service.DeptsRolesService;
import org.springframework.stereotype.Service;

/**
 * 角色与部门关联表;(depts_roles)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class DeptsRolesServiceImpl extends ServiceImpl<DeptsRolesMapper, DeptsRoles> implements DeptsRolesService{
    @Resource
    private DeptsRolesMapper deptsRolesMapper;
    
}