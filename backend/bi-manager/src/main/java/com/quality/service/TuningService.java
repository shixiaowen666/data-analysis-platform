package com.quality.service;

import com.alibaba.fastjson.JSONObject;
import com.common.result.PageResult;
import com.quality.dto.*;
import com.quality.entity.TuningTask;
import com.quality.entity.TuningVerifyConfig;

import java.util.List;
import java.util.Map;

/** 自动调优：建议 / 任务状态机 / 验证 / 审批 / 发布 / 回退 / 配置 */
public interface TuningService {

    /** 按诊断生成建议（不落库） */
    List<ChangeDTO> suggest(Long diagnosisId);

    TuningTask createTask(TaskCreateDTO dto);

    PageResult<JSONObject> taskPage(PageQuery q);

    JSONObject taskDetail(Long id);

    TuningTask updateChanges(Long id, List<ChangeDTO> changes, Map<String, Object> verifyConfig);

    /** DRAFT → APPLIED（拍快照、冲突检查）→ VERIFYING（异步） */
    TuningTask execute(Long id);

    TuningTask verify(Long id, List<String> extraQuestions);

    void abortVerify(Long id);

    JSONObject report(Long id);

    JSONObject verifyCase(Long caseId);

    TuningTask submitApproval(Long id, String confirmNote);

    TuningTask withdraw(Long id);

    TuningTask approve(Long id, ApproveDTO dto);

    List<JSONObject> pendingApprovals();

    TuningTask rollback(Long id, RollbackDTO dto);

    TuningTask cancel(Long id);

    JSONObject impact(Long id);

    TuningVerifyConfig config(String scope);

    TuningVerifyConfig saveConfig(TuningVerifyConfig cfg);

    List<TuningVerifyConfig> configs();

    JSONObject quickVerify(QuickVerifyDTO dto);

    /** 提示词编辑器参考信息：原问题 / 失败步骤 / 根因 / 当前版本内容 */
    JSONObject promptEditorContext(Long taskId, String groupName);

    List<JSONObject> notifications(boolean unreadOnly);

    void markNotificationRead(Long id);

    /** 观察期巡检：24h 内同智能体同错误类型新增 👎 ≥ 阈值 → 告警 */
    int observeCheck();
}
