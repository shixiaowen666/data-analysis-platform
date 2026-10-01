package com.chatbi.chat.service;

import com.alibaba.fastjson.JSONArray;

/**
 * 元数据组装：智能体视角（analyze 用）与全量视角（元数据接口用）共用。
 */
public interface MetadataService {

    /** 智能体可用指标（原子 + 引用校验通过的计算/派生），结构同 database_meta.available_metrics */
    JSONArray buildAgentIndicators(String code);

    /** 智能体可用维度 */
    JSONArray buildAgentDimensions(String code);

    /** 智能体关联表的表结构摘要 */
    JSONArray buildAgentTableSummaries(String code);

    /** 全量已上线指标（原子 + 全部计算/派生，带公式） */
    JSONArray buildAllIndicators();

    /** 全量已上线维度 */
    JSONArray buildAllDimensions();

    /** 全量表结构摘要 */
    JSONArray buildAllTableSummaries();

    /** 全量知识库条目（ai_body_id + knowledge_element） */
    JSONArray buildAllBusinessContexts();

    /** 智能体知识库拼接文本（与 biChat 传给 System B 的 business_context 一致） */
    String buildAgentBusinessContext(String code);
}
