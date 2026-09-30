package com.chatbi.chat.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatbi.chat.entity.BaseEntity;
import com.chatbi.chat.service.IBaseService;

/**
 * 通用 Service 实现基类
 */
public abstract class BaseServiceImpl<M extends BaseMapper<T>, T extends BaseEntity>
        extends ServiceImpl<M, T> implements IBaseService<T, M> {
}
