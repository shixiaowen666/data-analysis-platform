package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.TableTagRelation;
import org.apache.ibatis.annotations.Param;

public interface TableTagRelationMapper extends BaseMapper<TableTagRelation> {


    TableTagRelation selectByTableId(@Param("tableId") Long tableId);


}
