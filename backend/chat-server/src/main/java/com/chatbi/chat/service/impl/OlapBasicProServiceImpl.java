package com.chatbi.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatbi.chat.entity.OlapBasicPro;
import com.chatbi.chat.mapper.OlapBasicProMapper;
import com.chatbi.chat.models.DimDTO;
import com.chatbi.chat.service.OlapBasicProService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class OlapBasicProServiceImpl extends ServiceImpl<OlapBasicProMapper, OlapBasicPro> implements OlapBasicProService {

    @Override
    public List<OlapBasicPro> getBasicProByName(List<String> englishNames) {
        if (englishNames == null || englishNames.isEmpty()) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<OlapBasicPro>().in(OlapBasicPro::getEnglishName, englishNames));
    }

    @Override
    public List<OlapBasicPro> selectByIdList(List<Long> ids) {
        return listByIds(ids);
    }

    @Override
    public List<DimDTO> getRequireDimInfo(Set<Long> dimIds) {
        return baseMapper.getRequireDimInfo(dimIds);
    }
}
