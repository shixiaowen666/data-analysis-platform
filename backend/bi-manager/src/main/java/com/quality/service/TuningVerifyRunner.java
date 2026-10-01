package com.quality.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.base.UserThreadLocal;
import com.common.models.SaasUser;
import com.quality.client.DownstreamClient;
import com.quality.client.QualityProperties;
import com.quality.engine.VerifyJudge;
import com.quality.entity.*;
import com.quality.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证执行器（§6.4）：异步、按配置并发度逐题调 System B，before 复用历史 trace，写进度与 verdict。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TuningVerifyRunner {

    private final TuningTaskMapper taskMapper;
    private final TuningVerifyReportMapper reportMapper;
    private final TuningVerifyCaseMapper caseMapper;
    private final TuningAuditLogMapper auditMapper;
    private final RegressionCaseMapper regressionCaseMapper;
    private final DownstreamClient downstream;
    private final QualityProperties props;

    private final ExecutorService scheduler = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tuning-verify");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, AtomicBoolean> aborted = new ConcurrentHashMap<>();

    public void run(Long taskId, Long reportId, JSONObject draftMeta, Map<String, String> promptOverrides,
                    TuningVerifyConfig cfg, SaasUser user) {
        AtomicBoolean abortFlag = new AtomicBoolean(false);
        aborted.put(taskId, abortFlag);
        scheduler.submit(() -> {
            if (user != null) UserThreadLocal.set(user);
            try {
                runSync(taskId, reportId, draftMeta, promptOverrides, cfg, abortFlag);
            } catch (Throwable e) {
                log.error("[quality] verify task {} crashed", taskId, e);
                TuningVerifyReport r = reportMapper.selectById(reportId);
                if (r != null) {
                    r.setStatus("FAILED");
                    r.setErrorMessage(StringUtils.abbreviate(e.getMessage(), 1000));
                    r.setFinishedAt(LocalDateTime.now());
                    reportMapper.updateById(r);
                }
                TuningTask t = taskMapper.selectById(taskId);
                if (t != null) {
                    t.setStatus("APPLIED");
                    taskMapper.updateById(t);
                }
            } finally {
                UserThreadLocal.remove();
                aborted.remove(taskId);
            }
        });
    }

    public void abort(Long taskId) {
        AtomicBoolean f = aborted.get(taskId);
        if (f != null) f.set(true);
    }

    void runSync(Long taskId, Long reportId, JSONObject draftMeta, Map<String, String> promptOverrides,
                 TuningVerifyConfig cfg, AtomicBoolean abortFlag) throws InterruptedException {
        List<TuningVerifyCase> cases = caseMapper.selectList(new LambdaQueryWrapper<TuningVerifyCase>()
                .eq(TuningVerifyCase::getReportId, reportId).orderByAsc(TuningVerifyCase::getId));
        int concurrency = Math.max(1, cfg.getConcurrency() == null ? props.getVerifyPoolSize() : cfg.getConcurrency());
        int caseTimeout = cfg.getCaseTimeoutS() == null ? 120 : cfg.getCaseTimeoutS();
        long deadline = System.currentTimeMillis() + (cfg.getTotalTimeoutMin() == null ? 30 : cfg.getTotalTimeoutMin()) * 60_000L;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        AtomicInteger done = new AtomicInteger();
        SaasUser user = UserThreadLocal.get();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (TuningVerifyCase c : cases) {
                futures.add(pool.submit(() -> {
                    if (user != null) UserThreadLocal.set(user);
                    try {
                        if (abortFlag.get() || System.currentTimeMillis() > deadline) {
                            c.setStatus("ERROR");
                            c.setVerdict("ERROR");
                            c.setErrorMessage(abortFlag.get() ? "已中止" : "整体超时");
                            caseMapper.updateById(c);
                            return;
                        }
                        runCase(taskId, c, draftMeta, promptOverrides, caseTimeout);
                    } finally {
                        UserThreadLocal.remove();
                        TuningVerifyReport r = reportMapper.selectById(reportId);
                        if (r != null) {
                            r.setCaseDone(done.incrementAndGet());
                            reportMapper.updateById(r);
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.warn("[quality] verify case error: {}", e.getMessage());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        finish(taskId, reportId, abortFlag.get());
    }

    void runCase(Long taskId, TuningVerifyCase c, JSONObject draftMeta, Map<String, String> promptOverrides, int timeoutSeconds) {
        long t0 = System.currentTimeMillis();
        c.setStatus("RUNNING");
        caseMapper.updateById(c);
        JSONObject expected = StringUtils.isBlank(c.getExpected()) ? null : JSON.parseObject(c.getExpected());
        String requestId = "tuning-" + taskId + "-" + c.getId();
        c.setAfterRequestId(requestId);
        try {
            JSONObject resp = downstream.analyze(requestId, c.getQuestion(), draftMeta, promptOverrides, taskId, timeoutSeconds);
            JSONObject after = summarize(resp);
            c.setAfterResult(after.toJSONString());
            c.setElapsedAfterMs((int) (System.currentTimeMillis() - t0));
            VerifyJudge.Judgement j = VerifyJudge.judge(expected, after);
            Boolean beforePass = c.getBeforePass() == null ? null : c.getBeforePass() == 1;
            if (expected == null && c.getBeforeResult() != null) {
                // 无期望（SIMILAR 无诊断）：变化检测
                JSONObject before = JSON.parseObject(c.getBeforeResult());
                boolean changed = !StringUtils.equals(String.valueOf(before.get("used_tables")), String.valueOf(after.get("used_tables")))
                        || !StringUtils.equals(String.valueOf(before.get("resolved_metrics")), String.valueOf(after.get("resolved_metrics")));
                j.detail.put("changed", changed);
                c.setAfterPass(j.pass ? 1 : 0);
                c.setVerdict(!j.pass ? "FAIL" : (changed ? "CHANGED" : "UNCHANGED"));
            } else {
                c.setAfterPass(j.pass ? 1 : 0);
                c.setVerdict(VerifyJudge.verdict(c.getCaseType(), beforePass, j.pass));
            }
            c.setJudgeDetail(j.detail.toJSONString());
            c.setStatus("DONE");
        } catch (Exception e) {
            c.setStatus("ERROR");
            c.setVerdict("ERROR");
            c.setErrorMessage(StringUtils.abbreviate(e.getMessage(), 1000));
            c.setElapsedAfterMs((int) (System.currentTimeMillis() - t0));
        }
        caseMapper.updateById(c);
    }

    /** System B analyze 响应 → 判定摘要 */
    public static JSONObject summarize(JSONObject resp) {
        JSONObject o = new JSONObject();
        if (resp == null) {
            o.put("status", "failure");
            return o;
        }
        o.put("status", resp.getString("status"));
        o.put("used_tables", resp.get("used_tables"));
        o.put("resolved_metrics", resp.get("resolved_metrics"));
        o.put("resolved_dims", resp.get("resolved_dims"));
        o.put("failed_steps", resp.get("failed_steps"));
        o.put("answer", StringUtils.abbreviate(resp.getString("answer"), 2000));
        o.put("step_count", resp.getJSONArray("steps") == null ? null : resp.getJSONArray("steps").size());
        o.put("prompt_version", resp.getString("prompt_version"));
        o.put("elapsed_ms", resp.get("elapsed_ms"));
        o.put("request_id", resp.getString("request_id"));
        return o;
    }

    void finish(Long taskId, Long reportId, boolean wasAborted) {
        List<TuningVerifyCase> cases = caseMapper.selectList(new LambdaQueryWrapper<TuningVerifyCase>().eq(TuningVerifyCase::getReportId, reportId));
        TuningVerifyReport r = reportMapper.selectById(reportId);
        if (r == null) return;
        Boolean originFixed = null;
        boolean hasOrigin = false;
        int regTotal = 0, regPass = 0, regFail = 0, degraded = 0, improved = 0, unchanged = 0;
        long sumBefore = 0, nBefore = 0, sumAfter = 0, nAfter = 0;
        for (TuningVerifyCase c : cases) {
            String v = StringUtils.defaultString(c.getVerdict());
            if ("ORIGIN".equals(c.getCaseType())) {
                hasOrigin = true;
                originFixed = "FIXED".equals(v);
            }
            if ("REGRESSION".equals(c.getCaseType())) {
                regTotal++;
                if ("PASS".equals(v) || "IMPROVED".equals(v)) regPass++; else regFail++;
                if (c.getRegressionCaseId() != null) {
                    RegressionCase rc = regressionCaseMapper.selectById(c.getRegressionCaseId());
                    if (rc != null) {
                        if ("PASS".equals(v)) rc.setLastPassAt(LocalDateTime.now());
                        else if ("DEGRADED".equals(v) || "FAIL".equals(v)) rc.setFailCount((rc.getFailCount() == null ? 0 : rc.getFailCount()) + 1);
                        regressionCaseMapper.updateById(rc);
                    }
                }
            }
            if ("DEGRADED".equals(v)) degraded++;
            if ("IMPROVED".equals(v) || "FIXED".equals(v)) improved++;
            if ("PASS".equals(v) || "UNCHANGED".equals(v)) unchanged++;
            if (c.getElapsedBeforeMs() != null) {
                sumBefore += c.getElapsedBeforeMs();
                nBefore++;
            }
            if (c.getElapsedAfterMs() != null) {
                sumAfter += c.getElapsedAfterMs();
                nAfter++;
            }
        }
        r.setOriginFixed(originFixed == null ? null : (originFixed ? 1 : 0));
        r.setRegressionTotal(regTotal);
        r.setRegressionPass(regPass);
        r.setRegressionFail(regFail);
        r.setDegradedCount(degraded);
        r.setImprovedCount(improved);
        r.setUnchangedCount(unchanged);
        r.setAvgElapsedBeforeMs(nBefore == 0 ? null : (int) (sumBefore / nBefore));
        r.setAvgElapsedAfterMs(nAfter == 0 ? null : (int) (sumAfter / nAfter));
        r.setConclusion(VerifyJudge.conclusion(originFixed, degraded, hasOrigin));
        r.setStatus(wasAborted ? "ABORTED" : "DONE");
        r.setFinishedAt(LocalDateTime.now());
        reportMapper.updateById(r);

        TuningTask t = taskMapper.selectById(taskId);
        if (t == null) return;
        JSONObject summary = new JSONObject();
        summary.put("fixed", Boolean.TRUE.equals(originFixed));
        summary.put("regression_pass", regPass);
        summary.put("regression_total", regTotal);
        summary.put("degraded", degraded);
        summary.put("improved", improved);
        summary.put("conclusion", r.getConclusion());
        summary.put("case_total", cases.size());
        t.setVerifySummary(summary.toJSONString());
        t.setStatus(wasAborted ? "APPLIED" : "VERIFIED");
        taskMapper.updateById(t);

        TuningAuditLog a = new TuningAuditLog();
        a.setTaskId(taskId);
        a.setAction(wasAborted ? "VERIFY_ABORTED" : "VERIFY_DONE");
        a.setOperator("system");
        a.setBeforeStatus("VERIFYING");
        a.setAfterStatus(t.getStatus());
        a.setDetail("结论 " + r.getConclusion() + "，原问题修复=" + originFixed + "，回归 " + regPass + "/" + regTotal + "，退化 " + degraded);
        auditMapper.insert(a);
        log.info("[quality] verify task {} finished: {}", taskId, a.getDetail());
    }

    /** 发布后线上复验：不带 X-Tuning-Task、不带 meta 覆盖，走 System B 完整链路（含 recall）。 */
    public void onlineRecheck(Long taskId, String question, String aiBodyCode, SaasUser user) {
        scheduler.submit(() -> {
            if (user != null) UserThreadLocal.set(user);
            try {
                JSONObject meta;
                try {
                    meta = downstream.agentMeta(aiBodyCode);
                } catch (Exception e) {
                    meta = new JSONObject();
                }
                long t0 = System.currentTimeMillis();
                JSONObject resp = downstream.analyze("recheck-" + taskId + "-" + System.currentTimeMillis(), question, meta, null, null, 180);
                JSONObject summary = summarize(resp);
                summary.put("elapsed_total_ms", System.currentTimeMillis() - t0);
                summary.put("at", LocalDateTime.now().toString());
                TuningTask t = taskMapper.selectById(taskId);
                if (t != null) {
                    t.setOnlineRecheck(summary.toJSONString());
                    taskMapper.updateById(t);
                }
            } catch (Exception e) {
                log.warn("[quality] online recheck task {} failed: {}", taskId, e.getMessage());
                TuningTask t = taskMapper.selectById(taskId);
                if (t != null) {
                    JSONObject s = new JSONObject();
                    s.put("status", "error");
                    s.put("message", e.getMessage());
                    t.setOnlineRecheck(s.toJSONString());
                    taskMapper.updateById(t);
                }
            } finally {
                UserThreadLocal.remove();
            }
        });
    }
}
