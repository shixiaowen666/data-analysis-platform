package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.GroupsDataRolesDraft;
import com.senses.permission.mapper.GroupsDataRolesDraftMapper;
import com.senses.permission.service.GroupsDataRolesDraftService;
 /**
 * 数据角色与部门关联草稿表;(groups_data_roles_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class GroupsDataRolesDraftServiceImpl extends ServiceImpl<GroupsDataRolesDraftMapper, GroupsDataRolesDraft> implements GroupsDataRolesDraftService{
    @Resource
    private GroupsDataRolesDraftMapper groupsDataRolesDraftMapper;
    
}