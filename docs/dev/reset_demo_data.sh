#!/usr/bin/env bash
# 重置沙箱演示数据：保留 2 条问答 trace(C1/C2) + 2 条 👎 反馈，清空诊断/任务/回归/通知，便于从头走验收流程
M="mysql -uchatbi -pchatbi123 --default-character-set=utf8mb4 new_bi"
for s in "truncate chat_error_diagnosis" "truncate tuning_task" "truncate tuning_change" "truncate asset_snapshot" \
         "truncate tuning_verify_report" "truncate tuning_verify_case" "truncate regression_case" \
         "truncate tuning_audit_log" "truncate tuning_notification" \
         "update chat_feedback set status=0, diagnosis_id=null" \
         "update chat_analysis_trace set auto_error_hint='METRIC_DIM' where chat_id='C1'" \
         "update chat_analysis_trace set auto_error_hint='DECOMPOSE' where chat_id='C2'"; do
  $M -e "$s" 2>&1 | grep -v "^$" | grep ERROR || true
done
$M -e "select chat_id, question, auto_error_hint from chat_analysis_trace; select chat_id, rating, error_types, status from chat_feedback"
