package com.chatbi.chat.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.chat.entity.ChatAnalysisTrace;
import com.chatbi.chat.entity.ChatStepTrace;
import com.chatbi.chat.mapper.ChatAnalysisTraceMapper;
import com.chatbi.chat.mapper.ChatStepTraceMapper;
import com.chatbi.chat.service.ChatTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTraceServiceImpl implements ChatTraceService {

    private final ChatAnalysisTraceMapper traceMapper;
    private final ChatStepTraceMapper stepTraceMapper;

    @Override
    public void saveAnalysisTrace(String chatSessionId, String chatId, String aiBodyCode,
                                  Long tenantId, Long userId, String username,
                                  String question, JSONObject databaseMeta,
                                  JSONObject resolverResult, long elapsedMs) {
        try {
            ChatAnalysisTrace t = new ChatAnalysisTrace();
            t.setChatSessionId(chatSessionId);
            t.setChatId(chatId);
            t.setAiBodyCode(aiBodyCode);
            t.setTenantId(tenantId);
            t.setUserId(userId);
            t.setUsername(username);
            t.setQuestion(question);
            t.setTotalElapsedMs((int) elapsedMs);
            if (databaseMeta != null) {
                t.setMetaMetricCount(sizeOf(databaseMeta.getJSONArray("available_metrics")));
                t.setMetaDimCount(sizeOf(databaseMeta.getJSONArray("available_dimensions")));
                t.setMetaTableCount(sizeOf(databaseMeta.getJSONArray("table_summaries")));
            }
            JSONObject body = resolverResult;
            if (body != null) {
                t.setStatus(body.getString("status"));
                t.setFinalAnswer(truncate(body.getString("answer"), 60000));
                t.setExecutionLog(jsonOf(body.get("execution_log")));
                t.setDecompositionSteps(jsonOf(body.get("steps")));
                t.setUsedTables(jsonOf(body.get("used_tables")));
                t.setResolvedMetrics(jsonOf(body.get("resolved_metrics")));
                t.setResolvedDims(jsonOf(body.get("resolved_dims")));
                t.setSystemBLogPath(body.getString("log_path"));
                t.setPromptVersion(body.getString("prompt_version"));
                JSONObject recall = body.getJSONObject("recall");
                if (recall != null) {
                    t.setRecallEnabled(Boolean.TRUE.equals(recall.getBoolean("used")) ? 1 : 0);
                    Double el = recall.getDouble("elapsed");
                    if (el != null) t.setRecallElapsedMs((int) (el * 1000));
                    JSONObject rmeta = recall.getJSONObject("database_meta");
                    if (rmeta != null) {
                        t.setRecallTables(codes(rmeta.getJSONArray("table_summaries"), "table_name"));
                        t.setRecallMetricCodes(codes(rmeta.getJSONArray("available_metrics"), "metric_code"));
                        t.setRecallDimCodes(codes(rmeta.getJSONArray("available_dimensions"), "dimension_code"));
                        // recall 生效时 meta 口径以缩圈后为准
                        t.setMetaMetricCount(sizeOf(rmeta.getJSONArray("available_metrics")));
                        t.setMetaDimCount(sizeOf(rmeta.getJSONArray("available_dimensions")));
                        t.setMetaTableCount(sizeOf(rmeta.getJSONArray("table_summaries")));
                    }
                }
                t.setAutoErrorHint(autoHint(body));
            }
            ChatAnalysisTrace existing = traceMapper.selectOne(
                    new LambdaQueryWrapper<ChatAnalysisTrace>().eq(ChatAnalysisTrace::getChatId, chatId).last("limit 1"));
            if (existing == null) {
                traceMapper.insert(t);
            } else {
                t.setId(existing.getId());
                traceMapper.updateById(t);
            }
            log.info("[trace] chat_analysis_trace saved chatId={} status={} hint={}", chatId, t.getStatus(), t.getAutoErrorHint());
        } catch (Exception e) {
            log.warn("[trace] saveAnalysisTrace failed (non-fatal) chatId={}: {}", chatId, e.getMessage());
        }
    }

    @Override
    public void saveStepTrace(ChatStepTrace trace) {
        try {
            if (trace == null || StringUtils.isBlank(trace.getChatId()) || StringUtils.isBlank(trace.getStepId())) {
                return;
            }
            ChatStepTrace existing = stepTraceMapper.selectOne(new LambdaQueryWrapper<ChatStepTrace>()
                    .eq(ChatStepTrace::getChatId, trace.getChatId())
                    .eq(ChatStepTrace::getStepId, trace.getStepId()).last("limit 1"));
            if (existing == null) {
                stepTraceMapper.insert(trace);
            } else {
                trace.setId(existing.getId());
                stepTraceMapper.updateById(trace);
            }
        } catch (Exception e) {
            log.warn("[trace] saveStepTrace failed (non-fatal) chatId={} step={}: {}",
                    trace.getChatId(), trace.getStepId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * 自动预判（01 文档 7.2）：failed 步骤→DECOMPOSE；全部失败→DECOMPOSE；
     * 总结输入 0 行但有数值→SUMMARY；其余为空。unmatched/选表预判在 step trace 维度由 bi-manager 聚合时补充。
     */
    static String autoHint(JSONObject body) {
        String status = body.getString("status");
        JSONArray failed = body.getJSONArray("failed_steps");
        if ("failure".equals(status)) return "DECOMPOSE";
        if (failed != null && !failed.isEmpty()) {
            for (int i = 0; i < failed.size(); i++) {
                String s = failed.getString(i);
                if (s != null && s.contains("summar")) return "SUMMARY";
            }
            return "DECOMPOSE";
        }
        return null;
    }

    private static String jsonOf(Object o) {
        return o == null ? null : JSON.toJSONString(o);
    }

    private static Integer sizeOf(JSONArray a) {
        return a == null ? null : a.size();
    }

    private static String codes(JSONArray arr, String key) {
        if (arr == null) return null;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o != null && o.getString(key) != null) out.add(o.getString(key));
        }
        return JSON.toJSONString(out);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
