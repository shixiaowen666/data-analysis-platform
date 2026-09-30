package com.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.metadata.entity.MetaColumn;
import com.metadata.vo.meta.MetaColumnVO;

import java.util.List;
import java.util.Map;

/**
 * 元数据-字段 Service
 */
public interface MetaColumnService extends IService<MetaColumn> {

    List<MetaColumnVO> listByTableId(Long tableId);

    Map<Long, Integer> countByTableIds(List<Long> tableIds);
}
