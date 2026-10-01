package com.quality.service;

import com.common.result.PageResult;
import com.quality.dto.PageQuery;
import com.quality.dto.RegressionCaseDTO;
import com.quality.entity.ChatAnalysisTrace;
import com.quality.entity.ChatErrorDiagnosis;
import com.quality.entity.RegressionCase;

import java.util.List;

/** 回归测试集（长期资产） */
public interface RegressionService {
    PageResult<RegressionCase> page(PageQuery q);
    RegressionCase save(RegressionCaseDTO dto);
    void delete(Long id);
    void toggle(Long id, boolean enabled);
    /** 👍 自动沉淀：以 trace 的表/指标/维度/答案为基线 */
    RegressionCase addFromTrace(ChatAnalysisTrace t, String source);
    /** 定位时勾选加入：以诊断的应选表/应为实体为期望 */
    RegressionCase addFromDiagnosis(ChatAnalysisTrace t, ChatErrorDiagnosis d);
    /** 按智能体 + tags 命中（表名/指标 code）挑选，上限 limit */
    List<RegressionCase> pick(String aiBodyCode, List<String> tags, int limit);
}
