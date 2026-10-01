package com.chatbi.chat.controller;

import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.response.CommonVo;
import com.chatbi.chat.service.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;

/**
 * 元数据查询接口：全量已上线指标/维度/表结构，结构与 analyzer 的 database_meta 对齐。
 */
@RestController
@RequestMapping({"/api/chat-server"})
@Tag(name = "元数据查询")
public class MetadataController {

    @Resource
    private MetadataService metadataService;

    @Operation(summary = "查询全量元数据", description = "返回全部已上线指标（含计算/派生+公式）、维度、表结构摘要")
    @RequestMapping(value = "/metadata", method = RequestMethod.GET)
    public CommonVo metadata() {
        JSONObject data = new JSONObject(new LinkedHashMap<>());
        data.put("available_metrics", metadataService.buildAllIndicators());
        data.put("available_dimensions", metadataService.buildAllDimensions());
        data.put("table_summaries", metadataService.buildAllTableSummaries());
        data.put("business_contexts", metadataService.buildAllBusinessContexts());
        return CommonVo.Builder.SUCC().initSuccData(data);
    }

    /**
     * 问答质量管理 · 调优：返回某智能体当前线上的 database_meta（与 biChat 传给 System B 的结构一致）。
     * bi-manager 在草稿验证时取该 meta，按 tuning_change 做内存覆盖后传给 System B。
     */
    @Operation(summary = "查询智能体元数据", description = "与 analyze 请求体 database_meta 结构一致（available_metrics/available_dimensions/table_summaries/business_context）")
    @RequestMapping(value = "/metadata/agent", method = RequestMethod.GET)
    public CommonVo agentMetadata(@RequestParam("code") String code,
                                  @RequestParam(value = "withIds", required = false, defaultValue = "false") boolean withIds) {
        JSONObject data = new JSONObject(new LinkedHashMap<>());
        data.put("available_metrics", metadataService.buildAgentIndicators(code));
        data.put("available_dimensions", metadataService.buildAgentDimensions(code));
        data.put("table_summaries", metadataService.buildAgentTableSummaries(code));
        data.put("business_context", metadataService.buildAgentBusinessContext(code));
        return CommonVo.Builder.SUCC().initSuccData(data);
    }
}
