package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.UsersDataRoles;
import com.senses.permission.entity.UsersRoles;

/**
 * 用户与数据角色表;(users_data_roles)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface UsersDataRolesService extends IService<UsersDataRoles>{
     UsersDataRoles selectOnly(Long dataRoleId, Long userId);
 }