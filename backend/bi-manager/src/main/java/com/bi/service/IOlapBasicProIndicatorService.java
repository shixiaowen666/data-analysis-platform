package com.bi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bi.entity.OlapBasicProIndicator;

public interface IOlapBasicProIndicatorService extends IService<OlapBasicProIndicator> {
    OlapBasicProIndicator selectOne(Long id);
}
