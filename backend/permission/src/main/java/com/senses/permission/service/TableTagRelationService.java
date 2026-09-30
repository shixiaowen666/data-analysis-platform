package com.senses.permission.service;

import com.senses.permission.model.ResultData;
import com.senses.permission.model.vo.TableTagRelationVo;
import org.springframework.transaction.annotation.Transactional;

public interface TableTagRelationService {

    ResultData<TableTagRelationVo> getByTableId(Long tableId);

    @Transactional(rollbackFor = Exception.class)
    Boolean save(TableTagRelationVo tableTagRelationVo);
}
