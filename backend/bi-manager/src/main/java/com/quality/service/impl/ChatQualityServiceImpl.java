package com.quality.service.impl;

import com.quality.util.Jsons;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.entity.ChatModelQA;
import com.bi.mapper.ChatModelQAMapper;
import com.common.base.UserThreadLocal;
import com.common.exception.BizException;
import com.common.models.SaasUser;
import com.common.result.PageResult;
import com.quality.client.DownstreamClient;
import com.quality.dto.DiagnosisSaveDTO;
import com.quality.dto.FeedbackSubmitDTO;
import com.quality.dto.PageQuery;
import com.quality.entity.*;
import com.quality.mapper.*;
import com.quality.service.ChatQualityService;
import com.quality.service.RegressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatQualityServiceImpl implements ChatQualityService {

    private final ChatErrorTypeDictMapper dictMapper;
    private final ChatFeedbackMapper feedbackMapper;
    private final ChatErrorDiagnosisMapper diagnosisMapper;
    private final ChatAnalysisTraceMapper traceMapper;
    private final ChatStepTraceMapper stepTraceMapper;
    private final ChatModelQAMapper chatModelQAMapper;
    private final DownstreamClient downstream;
    private final RegressionService regressionService;

    // ------------------------------------------------------------------ dict

    @Override
    public List<ChatErrorTypeDict> errorTypes(String scope) {
        LambdaQueryWrapper<ChatErrorTypeDict> w = new LambdaQueryWrapper<ChatErrorTypeDict>()
                .eq(ChatErrorTypeDict::getEnabled, 1).orderByAsc(ChatErrorTypeDict::getSort);
        if (StringUtils.isNotBlank(scope) && !"both".equals(scope)) {
            w.in(ChatErrorTypeDict::getScope, scope, "both");
        }
        return dictMapper.selectList(w);
    }

    // -------------------------------------------------------------- feedback

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatFeedback submitFeedback(FeedbackSubmitDTO dto) {
        if (dto.getRating() == null || (dto.getRating() != 1 && dto.getRating() != -1)) {
            throw new BizException(400, "rating 仅支持 1（赞）/ -1（踩）");
        }
        if (dto.getRating() < 0 && nvl(dto.getErrorTypes()).isEmpty()) {
            throw new BizException(400, "踩时至少选择一个错误类型");
        }
        SaasUser u = current();
        Long userId = u == null ? 0L : (u.getId() == null ? 0L : u.getId());
        ChatFeedback fb = feedbackMapper.selectOne(new LambdaQueryWrapper<ChatFeedback>()
                .eq(ChatFeedback::getChatId, dto.getChatId()).eq(ChatFeedback::getUserId, userId).last("limit 1"));
        boolean isNew = fb == null;
        if (isNew) {
            fb = new ChatFeedback();
            fb.setChatId(dto.getChatId());
            fb.setChatSessionId(dto.getChatSessionId());
            fb.setUserId(userId);
            fb.setUsername(u == null ? null : u.getUsername());
            fb.setTenantId(u == null ? null : u.getTenantId());
            fb.setStatus(0);
        }
        fb.setRating(dto.getRating());
        fb.setErrorTypes(dto.getRating() != null && dto.getRating() < 0 ? JSON.toJSONString(nvl(dto.getErrorTypes())) : "[]");
        fb.setDescription(dto.getRating() != null && dto.getRating() < 0 ? dto.getDescription() : null);
        // 补全问题/智能体/答案
        ChatAnalysisTrace t = traceByChat(dto.getChatId());
        fb.setAiBodyCode(firstNonBlank(dto.getAiBodyCode(), t == null ? null : t.getAiBodyCode(), fb.getAiBodyCode()));
        fb.setQuestion(firstNonBlank(dto.getQuestion(), t == null ? null : t.getQuestion(), fb.getQuestion()));
        fb.setAnswerSnapshot(firstNonBlank(dto.getAnswerSnapshot(), t == null ? null : abbreviate(t.getFinalAnswer(), 4000), fb.getAnswerSnapshot()));
        if (fb.getQuestion() == null) {
            ChatModelQA qa = chatModelQAMapper.selectOne(new LambdaQueryWrapper<ChatModelQA>()
                    .eq(ChatModelQA::getChatId, dto.getChatId()).orderByAsc(ChatModelQA::getItemId).last("limit 1"));
            if (qa != null) fb.setQuestion(qa.getQuestion());
        }
        if (isNew) feedbackMapper.insert(fb); else feedbackMapper.updateById(fb);

        // 兼容镜像：dcar_chat_model_qa.feedback / reason
        try {
            List<ChatModelQA> qas = chatModelQAMapper.selectList(new LambdaQueryWrapper<ChatModelQA>()
                    .eq(ChatModelQA::getChatId, dto.getChatId()));
            for (ChatModelQA qa : qas) {
                qa.setFeedback(dto.getRating());
                qa.setReason(dto.getRating() < 0 ? abbreviate(String.join(",", nvl(dto.getErrorTypes()))
                        + (StringUtils.isBlank(dto.getDescription()) ? "" : " | " + dto.getDescription()), 500) : null);
                chatModelQAMapper.updateById(qa);
            }
        } catch (Exception e) {
            log.warn("[quality] mirror feedback to dcar_chat_model_qa failed: {}", e.getMessage());
        }
        // 👍 自动沉淀回归集（Phase 2 开关，默认开）
        if (dto.getRating() != null && dto.getRating() > 0 && t != null) {
            try {
                regressionService.addFromTrace(t, "FEEDBACK_UP");
            } catch (Exception e) {
                log.warn("[quality] regression addFromTrace failed: {}", e.getMessage());
            }
        }
        return fb;
    }

    @Override
    public ChatFeedback myFeedback(String chatId) {
        SaasUser u = current();
        Long userId = u == null ? 0L : (u.getId() == null ? 0L : u.getId());
        return feedbackMapper.selectOne(new LambdaQueryWrapper<ChatFeedback>()
                .eq(ChatFeedback::getChatId, chatId).eq(ChatFeedback::getUserId, userId).last("limit 1"));
    }

    @Override
    public PageResult<JSONObject> feedbackPage(PageQuery q) {
        LambdaQueryWrapper<ChatFeedback> w = new LambdaQueryWrapper<ChatFeedback>()
                .eq(StringUtils.isNotBlank(q.getAiBodyCode()), ChatFeedback::getAiBodyCode, q.getAiBodyCode())
                .eq(q.getStatus() != null, ChatFeedback::getStatus, q.getStatus())
                .like(StringUtils.isNotBlank(q.getErrorType()), ChatFeedback::getErrorTypes, q.getErrorType())
                .and(StringUtils.isNotBlank(q.getKeyword()), x -> x.like(ChatFeedback::getQuestion, q.getKeyword())
                        .or().like(ChatFeedback::getDescription, q.getKeyword()).or().like(ChatFeedback::getUsername, q.getKeyword()))
                .ge(StringUtils.isNotBlank(q.getStartTime()), ChatFeedback::getCreatedAt, q.getStartTime())
                .le(StringUtils.isNotBlank(q.getEndTime()), ChatFeedback::getCreatedAt, q.getEndTime())
                .orderByDesc(ChatFeedback::getCreatedAt);
        if ("down".equals(q.getStatusStr())) w.eq(ChatFeedback::getRating, -1);
        if ("up".equals(q.getStatusStr())) w.eq(ChatFeedback::getRating, 1);
        Page<ChatFeedback> page = feedbackMapper.selectPage(new Page<>(q.getPage(), q.getPageSize()), w);
        Map<String, ChatErrorDiagnosis> diag = diagnosisByChatIds(page.getRecords().stream().map(ChatFeedback::getChatId).collect(Collectors.toList()));
        List<JSONObject> rows = page.getRecords().stream().map(f -> {
            JSONObject o = Jsons.obj(f);
            o.put("errorTypes", parseArr(f.getErrorTypes()));
            ChatErrorDiagnosis d = diag.get(f.getChatId());
            o.put("diagnosis", d == null ? null : Jsons.obj(d));
            o.put("adminErrorType", d == null ? null : d.getPrimaryErrorType());
            return o;
        }).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), rows, page.getCurrent(), page.getSize());
    }

    @Override
    public void updateFeedbackStatus(Long id, Integer status) {
        ChatFeedback fb = feedbackMapper.selectById(id);
        if (fb == null) throw new BizException(404, "反馈不存在");
        fb.setStatus(status);
        feedbackMapper.updateById(fb);
    }

    // ------------------------------------------------------------- diagnosis

    @Override
    public PageResult<JSONObject> diagnosisPage(PageQuery q) {
        LambdaQueryWrapper<ChatAnalysisTrace> w = new LambdaQueryWrapper<ChatAnalysisTrace>()
                .eq(StringUtils.isNotBlank(q.getAiBodyCode()), ChatAnalysisTrace::getAiBodyCode, q.getAiBodyCode())
                .like(StringUtils.isNotBlank(q.getKeyword()), ChatAnalysisTrace::getQuestion, q.getKeyword())
                .ge(StringUtils.isNotBlank(q.getStartTime()), ChatAnalysisTrace::getCreatedAt, q.getStartTime())
                .le(StringUtils.isNotBlank(q.getEndTime()), ChatAnalysisTrace::getCreatedAt, q.getEndTime())
                .orderByDesc(ChatAnalysisTrace::getCreatedAt);
        if ("failed".equals(q.getQuick())) w.ne(ChatAnalysisTrace::getStatus, "success");
        if ("feedback".equals(q.getQuick())) {
            List<String> ids = feedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedback>().eq(ChatFeedback::getRating, -1)
                    .select(ChatFeedback::getChatId)).stream().map(ChatFeedback::getChatId).distinct().collect(Collectors.toList());
            if (ids.isEmpty()) return new PageResult<>(0L, Collections.emptyList(), (long) q.getPage(), (long) q.getPageSize());
            w.in(ChatAnalysisTrace::getChatId, ids);
        }
        if ("undiagnosed".equals(q.getQuick())) {
            List<String> ids = diagnosisMapper.selectList(new LambdaQueryWrapper<ChatErrorDiagnosis>().select(ChatErrorDiagnosis::getChatId))
                    .stream().map(ChatErrorDiagnosis::getChatId).collect(Collectors.toList());
            if (!ids.isEmpty()) w.notIn(ChatAnalysisTrace::getChatId, ids);
        }
        if (StringUtils.isNotBlank(q.getErrorType())) {
            List<String> ids = diagnosisMapper.selectList(new LambdaQueryWrapper<ChatErrorDiagnosis>()
                    .eq(ChatErrorDiagnosis::getPrimaryErrorType, q.getErrorType()).select(ChatErrorDiagnosis::getChatId))
                    .stream().map(ChatErrorDiagnosis::getChatId).collect(Collectors.toList());
            if (ids.isEmpty()) return new PageResult<>(0L, Collections.emptyList(), (long) q.getPage(), (long) q.getPageSize());
            w.in(ChatAnalysisTrace::getChatId, ids);
        }
        Page<ChatAnalysisTrace> page = traceMapper.selectPage(new Page<>(q.getPage(), q.getPageSize()), w);
        List<String> chatIds = page.getRecords().stream().map(ChatAnalysisTrace::getChatId).collect(Collectors.toList());
        Map<String, ChatErrorDiagnosis> diag = diagnosisByChatIds(chatIds);
        Map<String, List<ChatFeedback>> fbs = chatIds.isEmpty() ? Collections.emptyMap() :
                feedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedback>().in(ChatFeedback::getChatId, chatIds))
                        .stream().collect(Collectors.groupingBy(ChatFeedback::getChatId));
        Map<String, List<ChatStepTrace>> steps = chatIds.isEmpty() ? Collections.emptyMap() :
                stepTraceMapper.selectList(new LambdaQueryWrapper<ChatStepTrace>().in(ChatStepTrace::getChatId, chatIds))
                        .stream().collect(Collectors.groupingBy(ChatStepTrace::getChatId));
        List<JSONObject> rows = page.getRecords().stream().map(t -> {
            JSONObject o = new JSONObject();
            o.put("id", t.getId());
            o.put("chatId", t.getChatId());
            o.put("chatSessionId", t.getChatSessionId());
            o.put("aiBodyCode", t.getAiBodyCode());
            o.put("username", t.getUsername());
            o.put("question", t.getQuestion());
            o.put("status", t.getStatus());
            o.put("totalElapsedMs", t.getTotalElapsedMs());
            o.put("createdAt", t.getCreatedAt());
            o.put("usedTables", parseArr(t.getUsedTables()));
            o.put("autoHint", autoHint(t, steps.getOrDefault(t.getChatId(), Collections.emptyList()), diag.get(t.getChatId())));
            List<ChatFeedback> f = fbs.getOrDefault(t.getChatId(), Collections.emptyList());
            o.put("feedbackCount", f.size());
            o.put("downCount", f.stream().filter(x -> x.getRating() != null && x.getRating() < 0).count());
            o.put("userErrorTypes", f.stream().filter(x -> x.getRating() != null && x.getRating() < 0)
                    .flatMap(x -> parseArr(x.getErrorTypes()).stream()).distinct().collect(Collectors.toList()));
            ChatErrorDiagnosis d = diag.get(t.getChatId());
            o.put("diagnosed", d != null);
            o.put("primaryErrorType", d == null ? null : d.getPrimaryErrorType());
            o.put("fixStatus", d == null ? null : d.getFixStatus());
            return o;
        }).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), rows, page.getCurrent(), page.getSize());
    }

    @Override
    public JSONObject trace(String chatId) {
        ChatAnalysisTrace t = traceByChat(chatId);
        JSONObject out = new JSONObject();
        List<ChatStepTrace> steps = stepTraceMapper.selectList(new LambdaQueryWrapper<ChatStepTrace>()
                .eq(ChatStepTrace::getChatId, chatId).orderByAsc(ChatStepTrace::getStepId));
        ChatErrorDiagnosis d = diagnosisMapper.selectOne(new LambdaQueryWrapper<ChatErrorDiagnosis>()
                .eq(ChatErrorDiagnosis::getChatId, chatId).last("limit 1"));
        List<ChatFeedback> fbs = feedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedback>().eq(ChatFeedback::getChatId, chatId));

        if (t != null) {
            JSONObject tj = Jsons.obj(t);
            for (String k : Arrays.asList("recallTables", "recallMetricCodes", "recallDimCodes", "decompositionSteps",
                    "executionLog", "usedTables", "resolvedMetrics", "resolvedDims")) {
                tj.put(k, parseJson(tj.getString(k)));
            }
            out.put("trace", tj);
        } else {
            out.put("trace", null);
        }
        out.put("steps", steps.stream().map(s -> {
            JSONObject sj = Jsons.obj(s);
            for (String k : Arrays.asList("expectedColumns", "resolvedIndicatorIds", "resolvedIndicatorNames",
                    "resolvedDimensionIds", "resolvedDimensionNames", "unmatchedColumns", "filters")) {
                sj.put(k, parseJson(sj.getString(k)));
            }
            return sj;
        }).collect(Collectors.toList()));
        // 问答回放：dcar_chat_model_qa 各 item
        List<ChatModelQA> qas = chatModelQAMapper.selectList(new LambdaQueryWrapper<ChatModelQA>()
                .eq(ChatModelQA::getChatId, chatId).orderByAsc(ChatModelQA::getItemId));
        out.put("items", qas.stream().map(qa -> {
            JSONObject o = new JSONObject();
            o.put("itemId", qa.getItemId());
            o.put("type", qa.getType());
            o.put("question", qa.getQuestion());
            o.put("answer", abbreviate(qa.getAnswer(), 8000));
            o.put("status", qa.getStatus());
            o.put("feedback", qa.getFeedback());
            return o;
        }).collect(Collectors.toList()));
        out.put("feedbacks", fbs.stream().map(f -> {
            JSONObject o = Jsons.obj(f);
            o.put("errorTypes", parseArr(f.getErrorTypes()));
            return o;
        }).collect(Collectors.toList()));
        out.put("diagnosis", d == null ? null : diagnosisJson(d));
        out.put("autoHint", autoHint(t, steps, d));
        out.put("stages", buildStages(t, steps));
        return out;
    }

    @Override
    public JSONObject systemBLog(String chatId) {
        ChatAnalysisTrace t = traceByChat(chatId);
        if (t == null) throw new BizException(404, "trace 不存在");
        try {
            JSONObject r = downstream.systemBLogByRequest(t.getChatSessionId() + "@" + chatId);
            if (r == null) return new JSONObject();
            // 下游 System B 返回 {code,data:{content,log_path}}，这里拆包成扁平结构
            JSONObject inner = r.getJSONObject("data");
            return inner != null && (inner.containsKey("content") || inner.containsKey("log_path")) ? inner : r;
        } catch (Exception e) {
            throw new BizException(502, "System B 日志获取失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatErrorDiagnosis saveDiagnosis(DiagnosisSaveDTO dto) {
        ChatErrorDiagnosis d = diagnosisMapper.selectOne(new LambdaQueryWrapper<ChatErrorDiagnosis>()
                .eq(ChatErrorDiagnosis::getChatId, dto.getChatId()).last("limit 1"));
        boolean isNew = d == null;
        if (isNew) {
            d = new ChatErrorDiagnosis();
            d.setChatId(dto.getChatId());
            d.setFixStatus(0);
        }
        ChatAnalysisTrace t = traceByChat(dto.getChatId());
        d.setChatSessionId(firstNonBlank(dto.getChatSessionId(), t == null ? null : t.getChatSessionId(), d.getChatSessionId()));
        d.setAiBodyCode(firstNonBlank(dto.getAiBodyCode(), t == null ? null : t.getAiBodyCode(), d.getAiBodyCode()));
        SaasUser u = current();
        d.setTenantId(u == null ? null : u.getTenantId());
        d.setPrimaryErrorType(dto.getPrimaryErrorType());
        d.setSecondaryErrorTypes(JSON.toJSONString(nvl(dto.getSecondaryErrorTypes())));
        d.setErrorStepId(dto.getErrorStepId());
        d.setExpectedTable(dto.getExpectedTable());
        d.setActualTable(firstNonBlank(dto.getActualTable(), t == null ? null : firstOf(parseArr(t.getUsedTables()))));
        d.setExpectedEntities(JSON.toJSONString(nvl(dto.getExpectedEntities())));
        d.setActualEntities(JSON.toJSONString(nvl(dto.getActualEntities())));
        d.setRootCause(dto.getRootCause());
        d.setFixActionType(dto.getFixActionType());
        d.setFixActionDetail(dto.getFixActionDetail());
        d.setAddRegression(Boolean.TRUE.equals(dto.getAddRegression()) ? 1 : 0);
        d.setExpectedAnswerKeywords(JSON.toJSONString(nvl(dto.getExpectedAnswerKeywords())));
        d.setDiagnosedBy(u == null ? "system" : u.getUsername());
        d.setDiagnosedAt(LocalDateTime.now());
        if (isNew) diagnosisMapper.insert(d); else diagnosisMapper.updateById(d);

        // 关联反馈 → 已定位
        List<ChatFeedback> fbs = feedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedback>().eq(ChatFeedback::getChatId, dto.getChatId()));
        for (ChatFeedback fb : fbs) {
            if (fb.getStatus() == null || fb.getStatus() == 0) {
                fb.setStatus(1);
                fb.setDiagnosisId(d.getId());
                feedbackMapper.updateById(fb);
            }
        }
        if (Boolean.TRUE.equals(dto.getAddRegression()) && t != null) {
            regressionService.addFromDiagnosis(t, d);
        }
        return d;
    }

    @Override
    public void updateFixStatus(String chatId, Integer fixStatus) {
        ChatErrorDiagnosis d = diagnosisMapper.selectOne(new LambdaQueryWrapper<ChatErrorDiagnosis>()
                .eq(ChatErrorDiagnosis::getChatId, chatId).last("limit 1"));
        if (d == null) throw new BizException(404, "诊断不存在");
        d.setFixStatus(fixStatus);
        SaasUser u = current();
        if (fixStatus != null && fixStatus == 2) {
            d.setVerifiedBy(u == null ? "system" : u.getUsername());
            d.setVerifiedAt(LocalDateTime.now());
        }
        diagnosisMapper.updateById(d);
        if (fixStatus != null && fixStatus >= 1) {
            List<ChatFeedback> fbs = feedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedback>().eq(ChatFeedback::getChatId, chatId));
            for (ChatFeedback fb : fbs) {
                fb.setStatus(2);
                feedbackMapper.updateById(fb);
            }
        }
    }

    @Override
    public JSONObject stats(String aiBodyCode, Integer days) {
        int d = days == null ? 7 : days;
        LocalDateTime since = LocalDateTime.now().minusDays(d);
        long total = traceMapper.selectCount(new LambdaQueryWrapper<ChatAnalysisTrace>()
                .eq(StringUtils.isNotBlank(aiBodyCode), ChatAnalysisTrace::getAiBodyCode, aiBodyCode).ge(ChatAnalysisTrace::getCreatedAt, since));
        long failed = traceMapper.selectCount(new LambdaQueryWrapper<ChatAnalysisTrace>()
                .eq(StringUtils.isNotBlank(aiBodyCode), ChatAnalysisTrace::getAiBodyCode, aiBodyCode).ge(ChatAnalysisTrace::getCreatedAt, since)
                .ne(ChatAnalysisTrace::getStatus, "success"));
        List<ChatFeedback> fbs = feedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedback>()
                .eq(StringUtils.isNotBlank(aiBodyCode), ChatFeedback::getAiBodyCode, aiBodyCode).ge(ChatFeedback::getCreatedAt, since));
        long up = fbs.stream().filter(f -> f.getRating() != null && f.getRating() > 0).count();
        long down = fbs.size() - up;
        Map<String, Long> byType = new TreeMap<>();
        fbs.stream().filter(f -> f.getRating() != null && f.getRating() < 0).flatMap(f -> parseArr(f.getErrorTypes()).stream())
                .forEach(x -> byType.merge(x, 1L, Long::sum));
        Map<String, Long> byAdminType = diagnosisMapper.selectList(new LambdaQueryWrapper<ChatErrorDiagnosis>()
                        .eq(StringUtils.isNotBlank(aiBodyCode), ChatErrorDiagnosis::getAiBodyCode, aiBodyCode).ge(ChatErrorDiagnosis::getDiagnosedAt, since))
                .stream().collect(Collectors.groupingBy(ChatErrorDiagnosis::getPrimaryErrorType, TreeMap::new, Collectors.counting()));
        JSONObject o = new JSONObject();
        o.put("days", d);
        o.put("total", total);
        o.put("failed", failed);
        o.put("up", up);
        o.put("down", down);
        o.put("pendingFeedback", fbs.stream().filter(f -> f.getStatus() != null && f.getStatus() == 0 && f.getRating() < 0).count());
        o.put("userErrorTypeDist", byType);
        o.put("adminErrorTypeDist", byAdminType);
        return o;
    }

    // --------------------------------------------------------------- helpers

    /**
     * 自动预判（01 文档 7.2）：failed 步骤→拆分；unmatched_columns→指标维度；
     * expected_table≠实际/UNKNOWN→选表；总结输入 0 行→总结。诊断已存在则直接用诊断结论。
     */
    static String autoHint(ChatAnalysisTrace t, List<ChatStepTrace> steps, ChatErrorDiagnosis d) {
        if (d != null) return d.getPrimaryErrorType();
        if (t == null) return null;
        if ("failure".equals(t.getStatus())) return "DECOMPOSE";
        for (ChatStepTrace s : steps) {
            if (StringUtils.isNotBlank(s.getErrorMessage())) return "DECOMPOSE";
            if (!parseArr(s.getUnmatchedColumns()).isEmpty()) return "METRIC_DIM";
            if (StringUtils.isNotBlank(s.getExpectedTable()) && s.getResolvedTableId() == null) return "TABLE_SELECT";
        }
        if (StringUtils.isNotBlank(t.getAutoErrorHint())) return t.getAutoErrorHint();
        boolean allZero = !steps.isEmpty() && steps.stream().allMatch(s -> s.getRowCount() != null && s.getRowCount() == 0);
        if (allZero && StringUtils.isNotBlank(t.getFinalAnswer()) && t.getFinalAnswer().matches(".*\\d.*")) return "SUMMARY";
        return null;
    }

    /** 链路 5 环节：①召回 ②拆解 ③取数映射 ④执行 ⑤总结 */
    static JSONArray buildStages(ChatAnalysisTrace t, List<ChatStepTrace> steps) {
        JSONArray arr = new JSONArray();
        arr.add(stage("recall", "召回", t == null ? "unknown" : (t.getRecallEnabled() != null && t.getRecallEnabled() == 1 ? "ok" : "skipped"),
                t == null ? null : "表 " + t.getMetaTableCount() + " / 指标 " + t.getMetaMetricCount() + " / 维度 " + t.getMetaDimCount(), "TABLE_SELECT"));
        arr.add(stage("decompose", "拆解", t == null ? "unknown" : ("failure".equals(t.getStatus()) ? "failed" : "ok"),
                t == null ? null : "步骤 " + parseJsonArr(t.getDecompositionSteps()).size() + " 个，提示词 " + t.getPromptVersion(), "DECOMPOSE"));
        boolean unmatched = steps.stream().anyMatch(s -> !parseArr(s.getUnmatchedColumns()).isEmpty());
        arr.add(stage("mapping", "取数映射", steps.isEmpty() ? "unknown" : (unmatched ? "warn" : "ok"),
                unmatched ? "存在未映射列" : "指标/维度均已映射", "METRIC_DIM"));
        boolean execFail = steps.stream().anyMatch(s -> StringUtils.isNotBlank(s.getErrorMessage()));
        arr.add(stage("execute", "执行", steps.isEmpty() ? "unknown" : (execFail ? "failed" : "ok"),
                "取数 " + steps.size() + " 步，失败 " + steps.stream().filter(s -> StringUtils.isNotBlank(s.getErrorMessage())).count(), "DECOMPOSE"));
        arr.add(stage("summary", "总结", t == null ? "unknown" : (StringUtils.isBlank(t.getFinalAnswer()) ? "warn" : "ok"),
                t == null ? null : abbreviate(t.getFinalAnswer(), 120), "SUMMARY"));
        return arr;
    }

    private static JSONObject stage(String key, String name, String status, String summary, String errorType) {
        JSONObject o = new JSONObject();
        o.put("key", key);
        o.put("name", name);
        o.put("status", status);
        o.put("summary", summary);
        o.put("errorType", errorType);
        return o;
    }

    private JSONObject diagnosisJson(ChatErrorDiagnosis d) {
        JSONObject o = Jsons.obj(d);
        for (String k : Arrays.asList("secondaryErrorTypes", "expectedEntities", "actualEntities", "expectedAnswerKeywords")) {
            o.put(k, parseJson(o.getString(k)));
        }
        return o;
    }

    private ChatAnalysisTrace traceByChat(String chatId) {
        return traceMapper.selectOne(new LambdaQueryWrapper<ChatAnalysisTrace>().eq(ChatAnalysisTrace::getChatId, chatId).last("limit 1"));
    }

    private Map<String, ChatErrorDiagnosis> diagnosisByChatIds(List<String> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) return Collections.emptyMap();
        return diagnosisMapper.selectList(new LambdaQueryWrapper<ChatErrorDiagnosis>().in(ChatErrorDiagnosis::getChatId, chatIds))
                .stream().collect(Collectors.toMap(ChatErrorDiagnosis::getChatId, x -> x, (a, b) -> a));
    }

    private static SaasUser current() {
        return UserThreadLocal.get();
    }

    static List<String> parseArr(String json) {
        if (StringUtils.isBlank(json)) return new ArrayList<>();
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static JSONArray parseJsonArr(String json) {
        if (StringUtils.isBlank(json)) return new JSONArray();
        try {
            return JSON.parseArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static Object parseJson(String json) {
        if (StringUtils.isBlank(json)) return null;
        try {
            return JSON.parse(json);
        } catch (Exception e) {
            return json;
        }
    }

    private static <T> List<T> nvl(List<T> l) {
        return l == null ? new ArrayList<>() : l;
    }

    private static String firstNonBlank(String... s) {
        for (String x : s) if (StringUtils.isNotBlank(x)) return x;
        return null;
    }

    private static String firstOf(List<String> l) {
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
