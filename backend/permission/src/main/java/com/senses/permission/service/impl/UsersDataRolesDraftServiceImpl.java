package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.UsersDataRolesDraft;
import com.senses.permission.mapper.UsersDataRolesDraftMapper;
import com.senses.permission.service.UsersDataRolesDraftService;
 /**
 * 用户和数据角色的关系草稿表;(users_data_roles_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class UsersDataRolesDraftServiceImpl extends ServiceImpl<UsersDataRolesDraftMapper, UsersDataRolesDraft> implements UsersDataRolesDraftService{
    @Resource
    private UsersDataRolesDraftMapper usersDataRolesDraftMapper;
    
}