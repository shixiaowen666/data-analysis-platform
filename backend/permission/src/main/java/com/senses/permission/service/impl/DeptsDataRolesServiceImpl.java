package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.DeptsDataRoles;
import com.senses.permission.mapper.DeptsDataRolesMapper;
import com.senses.permission.service.DeptsDataRolesService;
import org.springframework.stereotype.Service;

/**
 * 数据角色与部门关联表;(depts_data_roles)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class DeptsDataRolesServiceImpl extends ServiceImpl<DeptsDataRolesMapper, DeptsDataRoles> implements DeptsDataRolesService{
    @Resource
    private DeptsDataRolesMapper deptsDataRolesMapper;
    
}