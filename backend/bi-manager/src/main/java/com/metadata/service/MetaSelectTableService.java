package com.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.metadata.entity.MetaSelectTable;
import com.metadata.vo.meta.MetaSelectTableVO;

import java.util.List;

/**
 * 数据源选中表 Service
 */
public interface MetaSelectTableService extends IService<MetaSelectTable> {

    MetaSelectTableVO listSelected(Integer datasourceId);

    void saveSelected(Integer datasourceId, List<String> tableNames, Long tenantId);
}
