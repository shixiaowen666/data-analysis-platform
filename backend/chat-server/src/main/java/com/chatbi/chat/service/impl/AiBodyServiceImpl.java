package com.chatbi.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatbi.chat.entity.AiBody;
import com.chatbi.chat.mapper.AiBodyMapper;
import com.chatbi.chat.service.AiBodyService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiBodyServiceImpl extends ServiceImpl<AiBodyMapper, AiBody> implements AiBodyService {

    @Override
    public AiBody getAiBodyByCode(String code) {
        return baseMapper.getAiBodyByCode(code);
    }

    @Override
    public List<Long> getPreferredTableIds(String code) {
        return baseMapper.getPreferredTableIds(code);
    }

    @Override
    public List<Long> getPreferredModelIds(String code) {
        return baseMapper.getPreferredModelIds(code);
    }

    @Override
    public List<Map<String, Object>> getResolverTableSummaries(String code) {
        return baseMapper.getResolverTableSummaries(code);
    }

    @Override
    public List<Map<String, Object>> getResolverDimensions(String code) {
        return baseMapper.getResolverDimensions(code);
    }

    @Override
    public List<Map<String, Object>> getResolverIndicators(String code) {
        return baseMapper.getResolverIndicators(code);
    }

    @Override
    public List<Map<String, Object>> getResolverCalcIndicators(List<Long> ids) {
        return baseMapper.getResolverCalcIndicators(ids);
    }

    @Override
    public List<Map<String, Object>> getAllResolverIndicators() {
        return baseMapper.getAllResolverIndicators();
    }

    @Override
    public List<Map<String, Object>> getAllResolverCalcIndicators() {
        return baseMapper.getAllResolverCalcIndicators();
    }

    @Override
    public List<Map<String, Object>> getAllResolverDimensions() {
        return baseMapper.getAllResolverDimensions();
    }

    @Override
    public List<Map<String, Object>> getAllResolverTableSummaries() {
        return baseMapper.getAllResolverTableSummaries();
    }

    @Override
    public List<Map<String, Object>> getAllKnowledgeElements() {
        return baseMapper.getAllKnowledgeElements();
    }
}
