package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.UsersRoles;
import com.senses.permission.mapper.UsersRolesMapper;
import com.senses.permission.service.UsersRolesService;
import org.springframework.stereotype.Service;

/**
 * 用户与角色表;(users_roles)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class UsersRolesServiceImpl extends ServiceImpl<UsersRolesMapper, UsersRoles> implements UsersRolesService{
    @Resource
    private UsersRolesMapper usersRolesMapper;

    @Override
    public UsersRoles selectOnly(Long roleId, Long userId) {
        return usersRolesMapper.selectOnly(roleId,userId);
    }
}