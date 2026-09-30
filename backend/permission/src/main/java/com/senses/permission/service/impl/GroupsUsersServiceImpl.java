package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.GroupsUsers;
import com.senses.permission.entity.User;
import com.senses.permission.mapper.GroupsUsersMapper;
import com.senses.permission.service.GroupsUsersService;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 用户组与用户关系表;(groups_users)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class GroupsUsersServiceImpl extends ServiceImpl<GroupsUsersMapper, GroupsUsers> implements GroupsUsersService{

    @Override
    public List<User> getBindUserIds(Long groupId) {
        List<User> users = baseMapper.truelistByGroupId(groupId);
        return users;
    }

}