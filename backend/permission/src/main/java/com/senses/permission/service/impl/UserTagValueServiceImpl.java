package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.User;
import com.senses.permission.entity.UserTagValue;
import com.senses.permission.mapper.UserTagValueMapper;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.UserTagValueParam;
import com.senses.permission.service.UserService;
import com.senses.permission.service.UserTagValueService;
import com.senses.permission.service.client.OlapqueryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户标签值
 *
 * @author wanjie
 * @date 2025-08-26 15:07
 * @version 1.0
 */
@Service
@Slf4j
public class UserTagValueServiceImpl extends ServiceImpl<UserTagValueMapper, UserTagValue> implements UserTagValueService {

    @Autowired
    private UserTagValueMapper userTagValueMapper;


    @Autowired
    private UserService userService;

    @Autowired
    private OlapqueryClient olapqueryClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultData saveUserTagValue(UserTagValueParam userTagValueParam) {
        try {
            UserTagValue userTagValue = new UserTagValue();
            BeanUtils.copyProperties(userTagValueParam,userTagValue);
            baseMapper.deleteByTagIdAndUsername(userTagValue.getTagId(),userTagValue.getUsername());
            if(userTagValue.getTagValue() == null || userTagValue.getTagValue().isEmpty()){
                return ResultData.success("保存成功");
            }
            User byUsername = userService.getByUsername(userTagValue.getUsername());
            olapqueryClient.deleteDecisionByUserId(byUsername.getId());
            userTagValueMapper.insert(userTagValue);
        } catch (Exception e) {
            log.error("保存用户标签值失败", e);
            return ResultData.fail("保存失败:" + e.getMessage());
        }
        return ResultData.success("保存成功");
    }

    @Override
    public void deleteByTagId(Long id) {
        userTagValueMapper.deleteByTagId(id);
    }
}
