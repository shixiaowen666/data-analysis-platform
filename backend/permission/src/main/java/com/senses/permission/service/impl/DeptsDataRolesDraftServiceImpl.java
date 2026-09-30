package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.DeptsDataRolesDraft;
import com.senses.permission.mapper.DeptsDataRolesDraftMapper;
import com.senses.permission.service.DeptsDataRolesDraftService;
 /**
 * 部门和数据角色关系草稿表;(depts_data_roles_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class DeptsDataRolesDraftServiceImpl extends ServiceImpl<DeptsDataRolesDraftMapper, DeptsDataRolesDraft> implements DeptsDataRolesDraftService{
    @Resource
    private DeptsDataRolesDraftMapper deptsDataRolesDraftMapper;
    
}