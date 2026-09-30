package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.UsersRoles;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户与角色表;(users_roles)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface UsersRolesService extends IService<UsersRoles>{
    UsersRoles selectOnly(Long roleId, Long userId);
}