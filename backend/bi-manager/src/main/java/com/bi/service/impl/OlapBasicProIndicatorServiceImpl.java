package com.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bi.entity.OlapBasicProIndicator;
import com.bi.mapper.OlapBasicProIndicatorMapper;
import com.bi.service.IOlapBasicProIndicatorService;
import org.springframework.stereotype.Service;

@Service
public class OlapBasicProIndicatorServiceImpl extends ServiceImpl<OlapBasicProIndicatorMapper, OlapBasicProIndicator>
        implements IOlapBasicProIndicatorService {
    @Override
    public OlapBasicProIndicator selectOne(Long id) {
        return baseMapper.selectOneByBasicId(id);
    }
}
