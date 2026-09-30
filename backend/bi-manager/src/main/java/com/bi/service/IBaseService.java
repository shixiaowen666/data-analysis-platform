package com.bi.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bi.entity.BaseEntity;

/**
 * 通用 Service 基类
 */
public interface IBaseService<T extends BaseEntity, M extends BaseMapper<T>>
        extends IService<T> {
}
