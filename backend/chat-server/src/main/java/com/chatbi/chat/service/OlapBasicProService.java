package com.chatbi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chatbi.chat.entity.OlapBasicPro;
import com.chatbi.chat.models.DimDTO;

import java.util.List;
import java.util.Set;

public interface OlapBasicProService extends IService<OlapBasicPro> {

    List<OlapBasicPro> getBasicProByName(List<String> englishNames);

    List<OlapBasicPro> selectByIdList(List<Long> ids);

    List<DimDTO> getRequireDimInfo(Set<Long> dimIds);
}
