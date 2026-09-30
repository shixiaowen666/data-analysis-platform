package com.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.result.PageResult;
import com.metadata.dto.meta.MetaTableQueryDTO;
import com.metadata.entity.MetaTable;
import com.metadata.vo.meta.MetaTableVO;

/**
 * 元数据-表 Service
 */
public interface MetaTableService extends IService<MetaTable> {

    PageResult<MetaTableVO> pageList(MetaTableQueryDTO query);
}
