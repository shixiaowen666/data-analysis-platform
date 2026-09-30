package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.UsersDataRoles;
import com.senses.permission.entity.UsersRoles;
import com.senses.permission.mapper.UsersDataRolesMapper;
import com.senses.permission.service.UsersDataRolesService;
import org.springframework.stereotype.Service;

/**
 * 用户与数据角色表;(users_data_roles)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class UsersDataRolesServiceImpl extends ServiceImpl<UsersDataRolesMapper, UsersDataRoles> implements UsersDataRolesService{
    @Resource
    private UsersDataRolesMapper usersDataRolesMapper;

    @Override
    public UsersDataRoles selectOnly(Long dataRoleId, Long userId) {
        return usersDataRolesMapper.selectOnly(dataRoleId,userId);
    }
}