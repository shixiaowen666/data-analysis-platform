package com.wm.semantic.controller;

import com.alibaba.fastjson.JSON;
import com.wm.semantic.common.response.QueryDataResponse;
import com.wm.semantic.dto.QueryDataRequest;
import com.wm.semantic.dto.GetDataSqlResponse;
import com.wm.semantic.service.SqlGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/data-server")
@Slf4j
public class GetDataController {
    @Resource
    private SqlGenerationService sqlGenerationService;

    /**
     * 生成SQL测试接口（只生成SQL，不执行）
     *
     * 路径: POST /api/getdata/generate
     */
    @PostMapping("/generate")
    public QueryDataResponse<List<String>> generate(@Valid @RequestBody QueryDataRequest request) {
        log.info("Generate SQL test: dimensionIds={}, indicatorIds={}",
                request.getDimensionIds(), request.getIndicatorIds());

        // 测试接口仅保留入口，正式SQL生成走 /api/getdata/sql
        return QueryDataResponse.success(new ArrayList<String>());
    }

    /**
     * 测试Model SQL生成
     *
     * 路径: POST /api/getdata/generate-model
     */
    @PostMapping("/generate-model")
    public QueryDataResponse<List<String>> generateModel(@Valid @RequestBody QueryDataRequest request) {
        // 测试接口仅保留入口，正式SQL生成走 /api/getdata/sql
        return QueryDataResponse.success(new ArrayList<String>());
    }

    /**
     * 执行数据查询接口
     */
    @PostMapping("/getdata")
    public QueryDataResponse<GetDataSqlResponse> execute(@Valid @RequestBody QueryDataRequest request) {
        log.info("Received request: {}", JSON.toJSONString(request));
        GetDataSqlResponse response = sqlGenerationService.generateAndExecute(request);
        return QueryDataResponse.success(response);
    }

}
