package com.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.common.result.PageResult;
import com.metadata.dto.meta.MetaDataSourceQueryDTO;
import com.metadata.dto.meta.MetaDataSourceSaveDTO;
import com.metadata.entity.MetaDataSource;
import com.metadata.vo.meta.DataSourceTestResultVO;
import com.metadata.vo.meta.MetaDataSourceVO;

/**
 * 数据源 Service
 */
public interface MetaDataSourceService extends IService<MetaDataSource> {

    PageResult<MetaDataSourceVO> pageList(MetaDataSourceQueryDTO query);

    MetaDataSourceVO getDetail(Long id);

    Long create(MetaDataSourceSaveDTO dto);

    void updateDataSource(MetaDataSourceSaveDTO dto);

    void deleteDataSource(Long id);

    DataSourceTestResultVO testConnection(MetaDataSourceSaveDTO dto);

    String previewJdbcUrl(MetaDataSourceSaveDTO dto);
}
