package com.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.result.PageResult;
import com.metadata.dto.meta.MetaCollectLogQueryDTO;
import com.metadata.entity.MetaCollectLog;
import com.metadata.vo.meta.MetaCollectLogVO;

/**
 * 采集日志 Service
 */
public interface MetaCollectLogService extends IService<MetaCollectLog> {

    PageResult<MetaCollectLogVO> pageList(MetaCollectLogQueryDTO query);

    MetaCollectLogVO getDetail(Long id);
}
