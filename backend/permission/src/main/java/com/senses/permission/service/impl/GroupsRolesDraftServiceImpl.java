package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.GroupsRolesDraft;
import com.senses.permission.mapper.GroupsRolesDraftMapper;
import com.senses.permission.service.GroupsRolesDraftService;
 /**
 * 角色与部门关联草稿表;(groups_roles_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class GroupsRolesDraftServiceImpl extends ServiceImpl<GroupsRolesDraftMapper, GroupsRolesDraft> implements GroupsRolesDraftService{
    @Resource
    private GroupsRolesDraftMapper groupsRolesDraftMapper;
    
}