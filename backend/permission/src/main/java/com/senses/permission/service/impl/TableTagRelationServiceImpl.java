package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.TableTagRelation;
import com.senses.permission.entity.TagFieldRelation;
import com.senses.permission.mapper.TableTagRelationMapper;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.TagParam;
import com.senses.permission.model.vo.TableTagRelationVo;
import com.senses.permission.model.vo.TagFieldRelationVo;
import com.senses.permission.service.TableTagRelationService;
import com.senses.permission.service.TagFieldRelationService;
import com.senses.permission.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author wanjie
 */
@Service
@Slf4j
public class TableTagRelationServiceImpl extends ServiceImpl<TableTagRelationMapper, TableTagRelation> implements TableTagRelationService {

    @Autowired
    private TagFieldRelationService tagFieldRelationService;

    @Autowired
    private TagService tagService;


    @Override
    public ResultData<TableTagRelationVo> getByTableId(Long tableId) {

        TableTagRelation tableTagRelation = getBaseMapper().selectByTableId(tableId);
        if (tableTagRelation == null) {
            return ResultData.success();
        }
        TableTagRelationVo tableTagRelationVo = new TableTagRelationVo();
        tableTagRelationVo.setId(tableTagRelation.getId());
        tableTagRelationVo.setRelationType(tableTagRelation.getRelationType());
        tableTagRelationVo.setTableId(tableTagRelation.getTableId());
        List<TagFieldRelation> fieldRelations = tagFieldRelationService.selectByTableId(tableId);
        List<TagFieldRelationVo> fieldRelationVos=new ArrayList<>();
        if(CollectionUtils.isNotEmpty(fieldRelations)){
            ResultData<List<TagParam>> allTags = tagService.getAllTags();
            List<TagParam> tags = allTags.getData();
            //转化为map
            Map<Long, String> tagMap = tags.stream().collect(Collectors.toMap(TagParam::getId, TagParam::getTagName));
            fieldRelations.forEach(item -> {
                TagFieldRelationVo tagFieldRelationVo = new TagFieldRelationVo();
                tagFieldRelationVo.setTagId(item.getTagId());
                tagFieldRelationVo.setTagName(tagMap.get(item.getTagId()));
                tagFieldRelationVo.setTableName(item.getTableName());
                tagFieldRelationVo.setColumnName(item.getColumnName());
                fieldRelationVos.add(tagFieldRelationVo);
            });
        }
        tableTagRelationVo.setTagFieldRelations(fieldRelationVos);
        return ResultData.success(tableTagRelationVo);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean save(TableTagRelationVo tableTagRelationVo) {
        //基本参数校验
        if (tableTagRelationVo == null || tableTagRelationVo.getTableId() == null || StringUtils.isEmpty(tableTagRelationVo.getRelationType())) {
            throw new RuntimeException("参数错误");
        }
        //TODO 分布式锁限制同一个表标签关系只能由一个线程来操作

        //删除旧数据
        tagFieldRelationService.deleteByTableId(tableTagRelationVo.getTableId());

        List<TagFieldRelationVo> tagFieldRelations = tableTagRelationVo.getTagFieldRelations();
        if (CollectionUtils.isNotEmpty(tagFieldRelations)) {
            tagFieldRelations.forEach(item -> {
                TagFieldRelation tagFieldRelation = new TagFieldRelation();
                tagFieldRelation.setTagId(item.getTagId());
                tagFieldRelation.setTableId(tableTagRelationVo.getTableId());
                tagFieldRelation.setTableName(item.getTableName());
                tagFieldRelation.setColumnName(item.getColumnName());

                boolean saveByTableId = tagFieldRelationService.saveByTableId(tagFieldRelation);
                if (!saveByTableId) {
                    throw new RuntimeException(String.format("%s.%s 字段已存在绑定关系", item.getTableName(),item.getColumnName()));
                }
            });
        }
        //编辑
        if (tableTagRelationVo.getId() != null) {
            TableTagRelation tableTagRelation = new TableTagRelation();
            tableTagRelation.setId(tableTagRelationVo.getId());
            tableTagRelation.setRelationType(tableTagRelationVo.getRelationType());
            return this.updateById(tableTagRelation);
        }
        //新增
        TableTagRelation tableTagRelation = new TableTagRelation();
        tableTagRelation.setTableId(tableTagRelationVo.getTableId());
        tableTagRelation.setRelationType(tableTagRelationVo.getRelationType());
        try {
            return this.save(tableTagRelation);
        }catch (DuplicateKeyException e){
            throw new RuntimeException("该表已存在标签配置信息");
        }catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }


}
