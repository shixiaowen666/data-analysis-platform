package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.DeptsRolesDraft;
import com.senses.permission.mapper.DeptsRolesDraftMapper;
import com.senses.permission.service.DeptsRolesDraftService;
 /**
 * 角色与部门关联草稿表;(depts_roles_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class DeptsRolesDraftServiceImpl extends ServiceImpl<DeptsRolesDraftMapper, DeptsRolesDraft> implements DeptsRolesDraftService{
    @Resource
    private DeptsRolesDraftMapper deptsRolesDraftMapper;
    
}