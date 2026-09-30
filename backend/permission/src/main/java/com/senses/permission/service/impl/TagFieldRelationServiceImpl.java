package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.TagFieldRelation;
import com.senses.permission.mapper.TagFieldRelationMapper;
import com.senses.permission.service.TagFieldRelationService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class TagFieldRelationServiceImpl extends ServiceImpl<TagFieldRelationMapper, TagFieldRelation> implements TagFieldRelationService {

    @Autowired
    private TagFieldRelationMapper tagFieldRelationMapper;



    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveByTableId(TagFieldRelation tagFieldRelation) {
        //校验重复: 一个标签只能绑定一个字段
        LambdaQueryWrapper<TagFieldRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagFieldRelation::getTableId,tagFieldRelation.getTableId());
        queryWrapper.eq(TagFieldRelation::getColumnName,tagFieldRelation.getColumnName());
        List<TagFieldRelation> list = tagFieldRelationMapper.selectList(queryWrapper);
        if(CollectionUtils.isNotEmpty(list)){
            return false;
        }
        //更新
        tagFieldRelation.setUpdatedTime(new Date());
        if(Objects.nonNull(tagFieldRelation.getId())){
            tagFieldRelationMapper.updateById(tagFieldRelation);
            return true;
        }
        //新增
        tagFieldRelation.setCreatedTime(new Date());
        tagFieldRelationMapper.insert(tagFieldRelation);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByTableId(Long tableId) {
        LambdaQueryWrapper<TagFieldRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagFieldRelation::getTableId,tableId);
        tagFieldRelationMapper.delete(queryWrapper);
    }

    @Override
    public List<TagFieldRelation> selectByTableId(Long tableId) {
        LambdaQueryWrapper<TagFieldRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagFieldRelation::getTableId,tableId);
        return tagFieldRelationMapper.selectList(queryWrapper);
    }

}
