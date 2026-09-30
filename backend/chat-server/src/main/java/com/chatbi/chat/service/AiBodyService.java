package com.chatbi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chatbi.chat.entity.AiBody;

import java.util.List;
import java.util.Map;

public interface AiBodyService extends IService<AiBody> {

    AiBody getAiBodyByCode(String code);

    List<Long> getPreferredTableIds(String code);

    List<Long> getPreferredModelIds(String code);

    List<Map<String, Object>> getResolverTableSummaries(String code);

    List<Map<String, Object>> getResolverDimensions(String code);

    List<Map<String, Object>> getResolverIndicators(String code);

    List<Map<String, Object>> getResolverCalcIndicators(List<Long> ids);

    List<Map<String, Object>> getAllResolverIndicators();

    List<Map<String, Object>> getAllResolverCalcIndicators();

    List<Map<String, Object>> getAllResolverDimensions();

    List<Map<String, Object>> getAllResolverTableSummaries();

    List<Map<String, Object>> getAllKnowledgeElements();
}
