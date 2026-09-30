package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.UserEngineConfig;
import com.senses.permission.mapper.UserEngineConfigMapper;
import com.senses.permission.service.UserEngineConfigService;
import org.springframework.stereotype.Service;

/**
 * 用户查询引擎配置表;(user_engine_config)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class UserEngineConfigServiceImpl extends ServiceImpl<UserEngineConfigMapper, UserEngineConfig> implements UserEngineConfigService{
    @Resource
    private UserEngineConfigMapper userEngineConfigMapper;
    
}