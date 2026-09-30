package com.senses.permission.service;

import com.senses.permission.entity.Tag;
import com.senses.permission.entity.UserTagValue;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.TagParam;
import com.senses.permission.model.vo.UserTagValueTableVO;

import java.util.List;

public interface TagService {
    ResultData<List<TagParam>> getAllTags();

    ResultData addOrModifyTag(TagParam tagParam, String username);

    ResultData<UserTagValueTableVO> getUserTagValuesTable(String keyword, Integer pageNo, Integer pageSize);

    Tag getById(Long id);

    ResultData<List<UserTagValue>> getUserTagValuesByUserId(Long userId);
}
