package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.UsersRolesDraft;
import com.senses.permission.mapper.UsersRolesDraftMapper;
import com.senses.permission.service.UsersRolesDraftService;
 /**
 * 用户和功能角色关系草稿表;(users_roles_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class UsersRolesDraftServiceImpl extends ServiceImpl<UsersRolesDraftMapper, UsersRolesDraft> implements UsersRolesDraftService{
    @Resource
    private UsersRolesDraftMapper usersRolesDraftMapper;
    
}