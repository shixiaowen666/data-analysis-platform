package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.RolesPermissionDraft;
import com.senses.permission.mapper.RolesPermissionDraftMapper;
import com.senses.permission.service.RolesPermissionDraftService;
 /**
 * 角色与权限管理关联草稿表;(roles_permission_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class RolesPermissionDraftServiceImpl extends ServiceImpl<RolesPermissionDraftMapper, RolesPermissionDraft> implements RolesPermissionDraftService{
    @Resource
    private RolesPermissionDraftMapper rolesPermissionDraftMapper;
    
}