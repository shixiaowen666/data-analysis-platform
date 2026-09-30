package com.senses.permission.service.impl;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.ResetPasswordHistory;
import com.senses.permission.mapper.ResetPasswordHistoryMapper;
import com.senses.permission.service.ResetPasswordHistoryService;
import org.springframework.stereotype.Service;

/**
 * 重置密码发送邮箱链接记录表;(reset_password_history)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class ResetPasswordHistoryServiceImpl extends ServiceImpl<ResetPasswordHistoryMapper, ResetPasswordHistory> implements ResetPasswordHistoryService{
    @Resource
    private ResetPasswordHistoryMapper resetPasswordHistoryMapper;
    
}