package com.senses.permission.service;

import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.UserTagValueParam;


public interface UserTagValueService {

    ResultData saveUserTagValue(UserTagValueParam userTagValueParam);

    void deleteByTagId(Long id);

}
