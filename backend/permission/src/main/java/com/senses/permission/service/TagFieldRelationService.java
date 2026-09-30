package com.senses.permission.service;

import com.senses.permission.entity.TagFieldRelation;

import java.util.List;

public interface TagFieldRelationService {

    /**
     * 根据表ID查询行权限配置信息
     */

    boolean saveByTableId(TagFieldRelation tagFieldRelation);

    void deleteByTableId(Long tableId);

    List<TagFieldRelation> selectByTableId(Long tableId);
}
