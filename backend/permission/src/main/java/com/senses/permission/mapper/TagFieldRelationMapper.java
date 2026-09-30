package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.TagFieldRelation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TagFieldRelationMapper extends BaseMapper<TagFieldRelation> {

    List<TagFieldRelation> selectByDataResourceId(Long dataResourceId);


}
