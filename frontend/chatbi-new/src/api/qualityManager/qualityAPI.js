import request from '@/utils/request'

const CHAT = '/api/v1/chat'
const TUNING = '/api/v1/tuning'

// ---------------- 字典 / 反馈 ----------------
export const getErrorTypesAPI = (scope) => request({ url: `${CHAT}/error-types`, method: 'get', params: { scope } })
export const submitFeedbackAPI = (data) => request({ url: `${CHAT}/feedback/submit`, method: 'post', data })
export const getMyFeedbackAPI = (chatId) => request({ url: `${CHAT}/feedback/mine`, method: 'get', params: { chatId } })
export const getFeedbackPageAPI = (params) => request({ url: `${CHAT}/feedback/page`, method: 'get', params })
export const updateFeedbackStatusAPI = (id, status) => request({ url: `${CHAT}/feedback/${id}/status`, method: 'put', data: { status } })

// ---------------- 诊断 / 链路 ----------------
export const getDiagnosisPageAPI = (params) => request({ url: `${CHAT}/diagnosis/page`, method: 'get', params })
export const getTraceAPI = (chatId) => request({ url: `${CHAT}/trace/${chatId}`, method: 'get' })
export const getSystemBLogAPI = (chatId) => request({ url: `${CHAT}/trace/${chatId}/system-b-log`, method: 'get' })
export const saveDiagnosisAPI = (data) => request({ url: `${CHAT}/diagnosis/save`, method: 'post', data })
export const updateFixStatusAPI = (chatId, fixStatus) => request({ url: `${CHAT}/diagnosis/${chatId}/fix-status`, method: 'put', data: { fixStatus } })
export const getQualityStatsAPI = (params) => request({ url: `${CHAT}/quality/stats`, method: 'get', params })

// ---------------- 调优 ----------------
export const suggestAPI = (diagnosisId) => request({ url: `${TUNING}/suggest`, method: 'post', data: { diagnosisId } })
export const createTaskAPI = (data) => request({ url: `${TUNING}/task`, method: 'post', data })
export const getTaskPageAPI = (params) => request({ url: `${TUNING}/task/page`, method: 'get', params })
export const getTaskDetailAPI = (id) => request({ url: `${TUNING}/task/${id}`, method: 'get' })
export const updateTaskChangesAPI = (id, data) => request({ url: `${TUNING}/task/${id}/changes`, method: 'put', data })
export const executeTaskAPI = (id) => request({ url: `${TUNING}/task/${id}/execute`, method: 'post' })
export const verifyTaskAPI = (id, extraCases) => request({ url: `${TUNING}/task/${id}/verify`, method: 'post', data: { extraCases } })
export const abortVerifyAPI = (id) => request({ url: `${TUNING}/task/${id}/verify/abort`, method: 'post' })
export const getReportAPI = (id) => request({ url: `${TUNING}/task/${id}/report`, method: 'get' })
export const getVerifyCaseAPI = (caseId) => request({ url: `${TUNING}/verify/case/${caseId}`, method: 'get' })
export const submitApprovalAPI = (id, confirmNote) => request({ url: `${TUNING}/task/${id}/submit-approval`, method: 'post', data: { confirmNote } })
export const withdrawTaskAPI = (id) => request({ url: `${TUNING}/task/${id}/withdraw`, method: 'post' })
export const approveTaskAPI = (id, approved, comment) => request({ url: `${TUNING}/task/${id}/approve`, method: 'post', data: { approved, comment } })
export const getPendingApprovalsAPI = () => request({ url: `${TUNING}/approval/pending`, method: 'get' })
export const rollbackTaskAPI = (id, reason, mode = 'FORCE') => request({ url: `${TUNING}/task/${id}/rollback`, method: 'post', data: { reason, mode } })
export const cancelTaskAPI = (id) => request({ url: `${TUNING}/task/${id}/cancel`, method: 'post' })
export const getImpactAPI = (id) => request({ url: `${TUNING}/task/${id}/impact`, method: 'get' })

// ---------------- 配置 / 编辑器 / 回归集 / 通知 ----------------
export const getConfigAPI = (scope) => request({ url: `${TUNING}/config`, method: 'get', params: { scope } })
export const getAllConfigsAPI = () => request({ url: `${TUNING}/config/all`, method: 'get' })
export const saveConfigAPI = (data) => request({ url: `${TUNING}/config`, method: 'put', data })
export const quickVerifyAPI = (data) => request({ url: `${TUNING}/prompt/quick-verify`, method: 'post', data })
export const getEditorContextAPI = (params) => request({ url: `${TUNING}/prompt/editor-context`, method: 'get', params })
export const getRegressionPageAPI = (params) => request({ url: `${TUNING}/regression/page`, method: 'get', params })
export const saveRegressionAPI = (data) => request({ url: `${TUNING}/regression`, method: 'post', data })
export const deleteRegressionAPI = (id) => request({ url: `${TUNING}/regression/${id}`, method: 'delete' })
export const toggleRegressionAPI = (id, enabled) => request({ url: `${TUNING}/regression/${id}/toggle`, method: 'put', data: { enabled } })
export const getNotificationsAPI = (unreadOnly) => request({ url: `${TUNING}/notifications`, method: 'get', params: { unreadOnly } })
export const readNotificationAPI = (id) => request({ url: `${TUNING}/notifications/${id}/read`, method: 'put' })

// ---------------- System B 提示词（经网关 /webapp 代理） ----------------
export const promptVersionContentAPI = (data) => request({ url: '/webapp/api/v1/prompts/version/content', method: 'post', data })
export const promptLintAPI = (data) => request({ url: '/webapp/api/v1/prompts/lint', method: 'post', data })
export const promptDiffAPI = (data) => request({ url: '/webapp/api/v1/prompts/version/diff', method: 'post', data })
export const promptVersionCreateAPI = (data) => request({ url: '/webapp/api/v1/prompts/version/create', method: 'post', data })
export const promptGroupsAPI = () => request({ url: '/webapp/api/v1/prompts/groups', method: 'post', data: {} })
export const promptListAPI = (data) => request({ url: '/webapp/api/v1/prompts/list', method: 'post', data })

// ---------------- 常量 ----------------
export const TASK_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  APPLIED: { label: '已应用', type: '' },
  VERIFYING: { label: '验证中', type: 'warning' },
  VERIFIED: { label: '已验证', type: 'success' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  ROLLED_BACK: { label: '已回退', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}
export const VERDICT = {
  FIXED: { label: '已修复', type: 'success' }, IMPROVED: { label: '改善', type: 'success' }, PASS: { label: '通过', type: 'success' },
  STILL_FAIL: { label: '仍失败', type: 'danger' }, DEGRADED: { label: '退化', type: 'danger' }, FAIL: { label: '失败', type: 'danger' },
  CHANGED: { label: '有变化', type: 'warning' }, UNCHANGED: { label: '无变化', type: 'info' }, ERROR: { label: '异常', type: 'info' },
}
export const FEEDBACK_STATUS = ['待处理', '已定位', '已修复', '已忽略']
export const FIX_STATUS = ['待修复', '已修复', '已验证']
export const ASSET_TYPE = {
  TABLE_DESC: '表描述', ENTITY_ALIAS: '实体别名', DIM_VALUES: '维度枚举值', AGG_TYPE: '聚合方式', KNOWLEDGE: '业务知识',
  AGENT_TABLE: '智能体绑定表', PROMPT_DECOMPOSE: '拆解提示词', PROMPT_SUMMARY: '总结提示词', RECALL_CONFIG: '召回配置',
  RECALL_DICT: '召回词典', SYSTEM_B_CONFIG: 'System B 配置',
}
