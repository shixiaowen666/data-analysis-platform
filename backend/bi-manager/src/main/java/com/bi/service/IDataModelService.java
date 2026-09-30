package com.bi.service;

import com.bi.dto.DataModelQueryDTO;
import com.bi.dto.DataModelSaveDTO;
import com.bi.vo.*;

import java.util.List;

/**
 * 数据模型管理 Service
 */
public interface IDataModelService {

    ApiPageResult<DataModelVO> listModels(DataModelQueryDTO query, Long tenantId);

    DataModelDetailVO getDetail(Long id, Long tenantId);

    Long createModel(DataModelSaveDTO dto, Long tenantId);

    void updateModel(Long id, DataModelSaveDTO dto, Long tenantId);

    void deleteModel(Long id, Long tenantId);

    List<CandidateTableVO> listCandidateFactTables(Long sourceId, Long tenantId);

    CandidateDimTablesVO listCandidateDimTables(Long sourceId, Long factTableId, Long tenantId);
}
