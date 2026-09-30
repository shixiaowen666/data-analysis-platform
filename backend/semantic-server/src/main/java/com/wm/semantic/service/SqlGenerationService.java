package com.wm.semantic.service;

import com.wm.semantic.dto.QueryDataRequest;
import com.wm.semantic.dto.GetDataSqlResponse;

public interface SqlGenerationService {
    /**
     * 生成并执行SQL查询
     *
     * 流程：选择表 → 构建SQL → 执行查询 → 返回结果
     *
     * @param request 请求参数
     * @return 查询结果
     */
    GetDataSqlResponse generateAndExecute(QueryDataRequest request);
}