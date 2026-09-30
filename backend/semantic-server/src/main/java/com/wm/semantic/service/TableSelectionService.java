package com.wm.semantic.service;

import com.wm.semantic.dto.CandidateTable;
import com.wm.semantic.dto.DataSourceInfo;
import com.wm.semantic.dto.QueryDataRequest;

import java.util.List;

/**
 * 数据源选择服务
 */
public interface TableSelectionService {
    /**
     * 根据维度ID和指标ID选择最优数据源
     *
     * @param request 请求参数
     * @return 选中的数据源列表（每个包含维度和指标字段信息）
     */
    List<DataSourceInfo> selectDataSource(QueryDataRequest request);
}