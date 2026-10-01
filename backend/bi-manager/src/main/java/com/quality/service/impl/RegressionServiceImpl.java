package com.quality.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.common.base.UserThreadLocal;
import com.common.exception.BizException;
import com.common.models.SaasUser;
import com.common.result.PageResult;
import com.quality.dto.PageQuery;
import com.quality.dto.RegressionCaseDTO;
import com.quality.entity.ChatAnalysisTrace;
import com.quality.entity.ChatErrorDiagnosis;
import com.quality.entity.RegressionCase;
import com.quality.mapper.RegressionCaseMapper;
import com.quality.service.RegressionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegressionServiceImpl implements RegressionService {

    private final RegressionCaseMapper mapper;

    @Override
    public PageResult<RegressionCase> page(PageQuery q) {
        LambdaQueryWrapper<RegressionCase> w = new LambdaQueryWrapper<RegressionCase>()
                .eq(StringUtils.isNotBlank(q.getAiBodyCode()), RegressionCase::getAiBodyCode, q.getAiBodyCode())
                .like(StringUtils.isNotBlank(q.getKeyword()), RegressionCase::getQuestion, q.getKeyword())
                .eq(StringUtils.isNotBlank(q.getStatusStr()), RegressionCase::getSource, q.getStatusStr())
                .orderByDesc(RegressionCase::getCreatedAt);
        return PageResult.of(mapper.selectPage(new Page<>(q.getPage(), q.getPageSize()), w));
    }

    @Override
    public RegressionCase save(RegressionCaseDTO dto) {
        RegressionCase c = dto.getId() == null ? new RegressionCase() : mapper.selectById(dto.getId());
        if (c == null) throw new BizException(404, "用例不存在");
        SaasUser u = UserThreadLocal.get();
        c.setAiBodyCode(dto.getAiBodyCode());
        c.setTenantId(u == null ? null : u.getTenantId());
        c.setQuestion(dto.getQuestion());
        c.setExpected(JSON.toJSONString(dto.getExpected() == null ? new HashMap<>() : dto.getExpected()));
        c.setSource(StringUtils.defaultIfBlank(dto.getSource(), "MANUAL"));
        c.setSourceChatId(dto.getSourceChatId());
        c.setTags(JSON.toJSONString(dto.getTags() == null ? new ArrayList<>() : dto.getTags()));
        c.setEnabled(Boolean.FALSE.equals(dto.getEnabled()) ? 0 : 1);
        if (c.getId() == null) {
            c.setCreatedBy(u == null ? "system" : u.getUsername());
            c.setFailCount(0);
            mapper.insert(c);
        } else {
            mapper.updateById(c);
        }
        return c;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void toggle(Long id, boolean enabled) {
        RegressionCase c = mapper.selectById(id);
        if (c == null) throw new BizException(404, "用例不存在");
        c.setEnabled(enabled ? 1 : 0);
        mapper.updateById(c);
    }

    @Override
    public RegressionCase addFromTrace(ChatAnalysisTrace t, String source) {
        if (t == null || StringUtils.isBlank(t.getQuestion())) return null;
        RegressionCase existing = mapper.selectOne(new LambdaQueryWrapper<RegressionCase>()
                .eq(RegressionCase::getSourceChatId, t.getChatId()).last("limit 1"));
        if (existing != null) return existing;
        List<String> tables = ChatQualityServiceImpl.parseArr(t.getUsedTables());
        List<String> metrics = ChatQualityServiceImpl.parseArr(t.getResolvedMetrics());
        List<String> dims = ChatQualityServiceImpl.parseArr(t.getResolvedDims());
        JSONObject expected = new JSONObject();
        expected.put("table", tables.isEmpty() ? null : tables.get(0));
        expected.put("tables", tables);
        expected.put("metrics", metrics);
        expected.put("dims", dims);
        expected.put("answer_keywords", new ArrayList<>());
        expected.put("baseline_answer", ChatQualityServiceImpl.abbreviate(t.getFinalAnswer(), 2000));
        List<String> tags = new ArrayList<>(tables);
        tags.addAll(metrics);
        RegressionCase c = new RegressionCase();
        c.setAiBodyCode(t.getAiBodyCode());
        c.setTenantId(t.getTenantId());
        c.setQuestion(t.getQuestion());
        c.setExpected(expected.toJSONString());
        c.setSource(source);
        c.setSourceChatId(t.getChatId());
        c.setTags(JSON.toJSONString(tags));
        c.setEnabled(1);
        c.setFailCount(0);
        c.setCreatedBy("system");
        mapper.insert(c);
        return c;
    }

    @Override
    public RegressionCase addFromDiagnosis(ChatAnalysisTrace t, ChatErrorDiagnosis d) {
        RegressionCase existing = mapper.selectOne(new LambdaQueryWrapper<RegressionCase>()
                .eq(RegressionCase::getSourceChatId, t.getChatId()).last("limit 1"));
        JSONObject expected = expectedFromDiagnosis(t, d);
        List<String> tags = new ArrayList<>();
        if (StringUtils.isNotBlank(d.getExpectedTable())) tags.add(d.getExpectedTable());
        tags.addAll(expected.getJSONArray("metrics").toJavaList(String.class));
        tags.add(d.getPrimaryErrorType());
        RegressionCase c = existing == null ? new RegressionCase() : existing;
        c.setAiBodyCode(t.getAiBodyCode());
        c.setTenantId(t.getTenantId());
        c.setQuestion(t.getQuestion());
        c.setExpected(expected.toJSONString());
        c.setSource("DIAGNOSIS");
        c.setSourceChatId(t.getChatId());
        c.setTags(JSON.toJSONString(tags));
        c.setEnabled(1);
        if (c.getId() == null) {
            c.setFailCount(0);
            SaasUser u = UserThreadLocal.get();
            c.setCreatedBy(u == null ? "system" : u.getUsername());
            mapper.insert(c);
        } else {
            mapper.updateById(c);
        }
        return c;
    }

    /** 期望：诊断的应选表 / 应为实体 + 期望答案关键词；缺失时回退 trace 基线 */
    static JSONObject expectedFromDiagnosis(ChatAnalysisTrace t, ChatErrorDiagnosis d) {
        JSONObject expected = new JSONObject();
        List<String> metrics = new ArrayList<>();
        List<String> dims = new ArrayList<>();
        try {
            List<Map> ents = JSON.parseArray(StringUtils.defaultIfBlank(d.getExpectedEntities(), "[]"), Map.class);
            for (Map e : ents) {
                String code = String.valueOf(e.getOrDefault("code", ""));
                if (StringUtils.isBlank(code) || "null".equals(code)) continue;
                if ("dim".equalsIgnoreCase(String.valueOf(e.get("type")))) dims.add(code); else metrics.add(code);
            }
        } catch (Exception ignore) {
        }
        String table = StringUtils.isNotBlank(d.getExpectedTable()) ? d.getExpectedTable()
                : (t == null ? null : firstOf(ChatQualityServiceImpl.parseArr(t.getUsedTables())));
        expected.put("table", table);
        expected.put("metrics", metrics);
        expected.put("dims", dims);
        expected.put("answer_keywords", ChatQualityServiceImpl.parseArr(d.getExpectedAnswerKeywords()));
        expected.put("primary_error_type", d.getPrimaryErrorType());
        return expected;
    }

    private static String firstOf(List<String> l) {
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    @Override
    public List<RegressionCase> pick(String aiBodyCode, List<String> tags, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<RegressionCase> all = mapper.selectList(new LambdaQueryWrapper<RegressionCase>()
                .eq(StringUtils.isNotBlank(aiBodyCode), RegressionCase::getAiBodyCode, aiBodyCode)
                .eq(RegressionCase::getEnabled, 1).orderByDesc(RegressionCase::getUpdatedAt));
        Set<String> tagSet = tags == null ? Collections.emptySet() : new HashSet<>(tags);
        // 命中 tags 的优先，其次补足
        List<RegressionCase> hit = all.stream().filter(c -> ChatQualityServiceImpl.parseArr(c.getTags()).stream().anyMatch(tagSet::contains)).collect(Collectors.toList());
        List<RegressionCase> rest = all.stream().filter(c -> !hit.contains(c)).collect(Collectors.toList());
        List<RegressionCase> out = new ArrayList<>(hit);
        out.addAll(rest);
        return out.size() > limit ? out.subList(0, limit) : out;
    }
}
