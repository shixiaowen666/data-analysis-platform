package com.quality.controller;

import jakarta.annotation.Resource;

import com.alibaba.fastjson.JSONObject;
import com.common.result.PageResult;
import com.common.result.R;
import com.quality.dto.*;
import com.quality.entity.RegressionCase;
import com.quality.entity.TuningTask;
import com.quality.entity.TuningVerifyConfig;
import com.quality.service.RegressionService;
import com.quality.service.TuningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自动调优（03 文档 §7）  Base: /v1/tuning
 */
@Validated
@RestController
@RequestMapping("/v1/tuning")
@RequiredArgsConstructor
public class TuningController {

    private final TuningService service;
    private final RegressionService regressionService;

    @PostMapping("/suggest")
    public R<List<ChangeDTO>> suggest(@RequestBody Map<String, Long> body) {
        return R.ok(service.suggest(body.get("diagnosisId")));
    }

    @PostMapping("/task")
    public R<TuningTask> create(@Valid @RequestBody TaskCreateDTO dto) {
        return R.ok(service.createTask(dto));
    }

    @GetMapping("/task/page")
    public R<PageResult<JSONObject>> page(PageQuery q) {
        return R.ok(service.taskPage(q));
    }

    @GetMapping("/task/{id}")
    public R<JSONObject> detail(@PathVariable Long id) {
        return R.ok(service.taskDetail(id));
    }

    @PutMapping("/task/{id}/changes")
    public R<TuningTask> changes(@PathVariable Long id, @RequestBody TaskCreateDTO dto) {
        return R.ok(service.updateChanges(id, dto.getChanges(), dto.getVerifyConfig()));
    }

    @PostMapping("/task/{id}/execute")
    public R<TuningTask> execute(@PathVariable Long id) {
        return R.ok(service.execute(id));
    }

    @PostMapping("/task/{id}/verify")
    public R<TuningTask> verify(@PathVariable Long id, @RequestBody(required = false) Map<String, List<String>> body) {
        return R.ok(service.verify(id, body == null ? null : body.get("extraCases")));
    }

    @PostMapping("/task/{id}/verify/abort")
    public R<Void> abort(@PathVariable Long id) {
        service.abortVerify(id);
        return R.ok();
    }

    @GetMapping("/task/{id}/report")
    public R<JSONObject> report(@PathVariable Long id) {
        return R.ok(service.report(id));
    }

    @GetMapping("/verify/case/{caseId}")
    public R<JSONObject> verifyCase(@PathVariable Long caseId) {
        return R.ok(service.verifyCase(caseId));
    }

    @PostMapping("/task/{id}/submit-approval")
    public R<TuningTask> submitApproval(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(service.submitApproval(id, body == null ? null : body.get("confirmNote")));
    }

    @PostMapping("/task/{id}/withdraw")
    public R<TuningTask> withdraw(@PathVariable Long id) {
        return R.ok(service.withdraw(id));
    }

    @PostMapping("/task/{id}/approve")
    public R<TuningTask> approve(@PathVariable Long id, @Valid @RequestBody ApproveDTO dto) {
        requireSuperAdmin();
        return R.ok(service.approve(id, dto));
    }

    @GetMapping("/approval/pending")
    public R<List<JSONObject>> pending() {
        return R.ok(service.pendingApprovals());
    }

    @PostMapping("/task/{id}/rollback")
    public R<TuningTask> rollback(@PathVariable Long id, @RequestBody(required = false) RollbackDTO dto) {
        requireSuperAdmin();
        return R.ok(service.rollback(id, dto == null ? new RollbackDTO() : dto));
    }

    @PostMapping("/task/{id}/cancel")
    public R<TuningTask> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }

    @GetMapping("/task/{id}/impact")
    public R<JSONObject> impact(@PathVariable Long id) {
        return R.ok(service.impact(id));
    }

    // ---- config
    @GetMapping("/config")
    public R<TuningVerifyConfig> config(@RequestParam(required = false) String scope) {
        return R.ok(service.config(scope));
    }

    @GetMapping("/config/all")
    public R<List<TuningVerifyConfig>> configs() {
        return R.ok(service.configs());
    }

    @PutMapping("/config")
    public R<TuningVerifyConfig> saveConfig(@RequestBody TuningVerifyConfig cfg) {
        requireSuperAdmin();
        return R.ok(service.saveConfig(cfg));
    }

    // ---- prompt editor
    @PostMapping("/prompt/quick-verify")
    public R<JSONObject> quickVerify(@RequestBody QuickVerifyDTO dto) {
        return R.ok(service.quickVerify(dto));
    }

    @GetMapping("/prompt/editor-context")
    public R<JSONObject> editorContext(@RequestParam(required = false) Long taskId, @RequestParam(required = false) String groupName) {
        return R.ok(service.promptEditorContext(taskId, groupName));
    }

    // ---- regression set
    @GetMapping("/regression/page")
    public R<PageResult<RegressionCase>> regressionPage(PageQuery q) {
        return R.ok(regressionService.page(q));
    }

    @PostMapping("/regression")
    public R<RegressionCase> regressionSave(@Valid @RequestBody RegressionCaseDTO dto) {
        return R.ok(regressionService.save(dto));
    }

    @DeleteMapping("/regression/{id}")
    public R<Void> regressionDelete(@PathVariable Long id) {
        regressionService.delete(id);
        return R.ok();
    }

    @PutMapping("/regression/{id}/toggle")
    public R<Void> regressionToggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        regressionService.toggle(id, Boolean.TRUE.equals(body.get("enabled")));
        return R.ok();
    }

    // ---- notifications / observe
    @GetMapping("/notifications")
    public R<List<JSONObject>> notifications(@RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        return R.ok(service.notifications(unreadOnly));
    }

    @PutMapping("/notifications/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        service.markNotificationRead(id);
        return R.ok();
    }

    @PostMapping("/observe/check")
    public R<Integer> observe() {
        return R.ok(service.observeCheck());
    }

    // ---- 权限：超级管理员（审批 / 回滚 / 验证配置）
    @Resource
    private com.quality.client.QualityProperties qualityProperties;

    private void requireSuperAdmin() {
        com.common.models.SaasUser u = com.common.base.UserThreadLocal.get();
        String name = u == null ? null : u.getUsername();
        java.util.Set<String> supers = java.util.Arrays.stream(
                        org.apache.commons.lang3.StringUtils.defaultString(qualityProperties.getSuperAdmins()).split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).collect(java.util.stream.Collectors.toSet());
        if (name == null || !supers.contains(name)) {
            throw new com.common.exception.BizException(403, "仅超级管理员可执行该操作");
        }
    }
}
