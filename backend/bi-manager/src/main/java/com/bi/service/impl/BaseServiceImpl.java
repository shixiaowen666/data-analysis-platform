package com.bi.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bi.entity.BaseEntity;

/**
 * 通用 Service 实现基类
 */
public abstract class BaseServiceImpl<M extends BaseMapper<T>, T extends BaseEntity>
        extends ServiceImpl<M, T> implements com.baomidou.mybatisplus.extension.service.IService<T> {
}
