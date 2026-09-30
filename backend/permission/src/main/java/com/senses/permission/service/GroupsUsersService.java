package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.GroupsUsers;
import com.senses.permission.entity.User;
import com.senses.permission.model.vo.GroupRolesIdsVO;

import java.util.List;

/**
 * 用户组与用户关系表;(groups_users)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface GroupsUsersService extends IService<GroupsUsers>{
    List<User> getBindUserIds(Long groupId);
}