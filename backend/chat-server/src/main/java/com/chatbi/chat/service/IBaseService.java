package com.chatbi.chat.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chatbi.chat.entity.BaseEntity;

/**
 * 通用 Service 基类
 */
public interface IBaseService<T extends BaseEntity, M extends BaseMapper<T>>
        extends IService<T> {
}
