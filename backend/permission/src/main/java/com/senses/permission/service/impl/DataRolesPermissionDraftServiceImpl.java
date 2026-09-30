package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.DataRolesPermissionDraft;
import com.senses.permission.mapper.DataRolesPermissionDraftMapper;
import com.senses.permission.service.DataRolesPermissionDraftService;
 /**
 * 数据角色权限草稿表;(data_roles_permission_draft)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Service
public class DataRolesPermissionDraftServiceImpl extends ServiceImpl<DataRolesPermissionDraftMapper, DataRolesPermissionDraft> implements DataRolesPermissionDraftService{
    @Resource
    private DataRolesPermissionDraftMapper dataRolesPermissionDraftMapper;
    
}