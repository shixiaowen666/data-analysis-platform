<template>
  <el-drawer :visible="visible" size="88%" :with-header="false" append-to-body destroy-on-close @close="close">
    <div class="td-root quality-manager" v-loading="loading" v-if="t">
      <div class="td-head">
        <div>
          <span class="td-title">{{ t.taskNo }}</span>
          <el-tag size="small" :type="TASK_STATUS[t.status].type" style="margin-left: 8px">{{ TASK_STATUS[t.status].label }}</el-tag>
          <span class="qm-muted" style="margin-left: 10px">第 {{ t.round }} 轮 · {{ t.aiBodyCode }} · {{ t.primaryErrorType }}</span>
        </div>
        <div>
          <el-button size="small" icon="el-icon-refresh" @click="load" />
          <el-button size="small" icon="el-icon-close" @click="close">关闭</el-button>
        </div>
      </div>

      <el-steps :active="stepIndex" finish-status="success" simple style="margin: 8px 0 12px">
        <el-step title="生成建议" /><el-step title="执行草稿" /><el-step title="验证" /><el-step title="提交审批" /><el-step title="超级管理员审批" /><el-step title="发布" /><el-step title="观察期" />
      </el-steps>

      <el-alert v-if="t.observeAlert" type="error" show-icon :closable="false" style="margin-bottom: 10px"
        :title="'观察期告警：' + t.observeAlert.message" description="请评估是否回退本任务（回退无需审批）。" />
      <el-alert v-if="t.status === 'ROLLED_BACK'" type="warning" show-icon :closable="false" style="margin-bottom: 10px"
        :title="'已回退 · ' + t.rolledBackBy + ' ' + t.rolledBackAt" :description="t.rollbackReason" />

      <div class="td-cols">
        <div class="td-main">
          <div class="qm-card">
            <div class="qm-card-title">来源问题</div>
            <div>{{ t.question }}</div>
            <div class="qm-muted" v-if="t.diagnosis" style="margin-top: 4px">根因：{{ t.diagnosis.rootCause || '—' }} · 应选表：{{ t.diagnosis.expectedTable || '—' }}</div>
          </div>

          <div class="qm-card">
            <div class="qm-card-title">
              变更列表（{{ changes.length }} 条，已采纳 {{ changes.filter((c) => c.accepted).length }}）
              <el-button v-if="editable" type="text" size="mini" :loading="saving" @click="saveChanges">保存变更</el-button>
            </div>
            <suggestion-table v-model="changes" :readonly="!editable" :showApply="t.status !== 'DRAFT'" @edit-prompt="openEditor" />
          </div>

          <div class="qm-card" v-if="report">
            <div class="qm-card-title">
              验证报告 · 第 {{ report.round }} 轮
              <span>
                <el-tag v-if="report.conclusion" size="small" :type="{ PASS: 'success', PASS_WITH_WARN: 'warning', FAIL: 'danger' }[report.conclusion]">{{ report.conclusion }}</el-tag>
                <el-tag v-else size="small" type="warning">{{ report.status }} {{ report.caseDone }}/{{ report.caseTotal }}</el-tag>
              </span>
            </div>
            <el-progress v-if="report.status === 'RUNNING'" :percentage="report.caseTotal ? Math.round((report.caseDone / report.caseTotal) * 100) : 0" style="margin-bottom: 8px" />
            <div class="td-metrics" v-if="report.status !== 'RUNNING'">
              <div class="td-metric"><div class="v">{{ report.originFixed ? '✅' : '❌' }}</div><div class="k">原问题修复</div></div>
              <div class="td-metric"><div class="v">{{ report.regressionPass }}/{{ report.regressionTotal }}</div><div class="k">回归通过</div></div>
              <div class="td-metric"><div class="v" :style="{ color: report.degradedCount ? '#f56c6c' : '' }">{{ report.degradedCount }}</div><div class="k">退化</div></div>
              <div class="td-metric"><div class="v">{{ report.improvedCount }}</div><div class="k">改善</div></div>
              <div class="td-metric"><div class="v">{{ report.avgElapsedBeforeMs || '-' }}→{{ report.avgElapsedAfterMs || '-' }}</div><div class="k">平均耗时 ms</div></div>
            </div>
            <el-table :data="cases" size="mini" border style="margin-top: 8px" @row-click="openCase">
              <el-table-column label="类型" prop="caseType" width="90" />
              <el-table-column label="问题" prop="question" min-width="220" show-overflow-tooltip />
              <el-table-column label="before" width="70" align="center"><template slot-scope="{ row }">{{ row.beforePass == null ? '—' : row.beforePass ? '✓' : '✗' }}</template></el-table-column>
              <el-table-column label="after" width="70" align="center"><template slot-scope="{ row }">{{ row.afterPass == null ? '—' : row.afterPass ? '✓' : '✗' }}</template></el-table-column>
              <el-table-column label="判定" width="90" align="center"><template slot-scope="{ row }"><el-tag v-if="row.verdict" size="mini" :type="(VERDICT[row.verdict] || {}).type">{{ (VERDICT[row.verdict] || {}).label || row.verdict }}</el-tag><span v-else class="qm-muted">{{ row.status }}</span></template></el-table-column>
              <el-table-column label="判定明细" min-width="200"><template slot-scope="{ row }"><span class="qm-mono qm-muted">{{ row.judgeDetail ? Object.entries(row.judgeDetail).map(([k, v]) => k + ':' + v).join(' ') : row.errorMessage || '' }}</span></template></el-table-column>
              <el-table-column label="耗时" width="90" align="right"><template slot-scope="{ row }">{{ row.elapsedAfterMs ? row.elapsedAfterMs + 'ms' : '' }}</template></el-table-column>
            </el-table>
          </div>

          <div class="qm-card" v-if="t.onlineRecheck">
            <div class="qm-card-title">发布后线上复验</div>
            <div class="qm-kv">
              <span class="k">状态</span><span><el-tag size="mini" :type="t.onlineRecheck.status === 'success' ? 'success' : 'danger'">{{ t.onlineRecheck.status }}</el-tag></span>
              <span class="k">用表</span><span class="qm-mono">{{ (t.onlineRecheck.used_tables || []).join(', ') }}</span>
              <span class="k">答案</span><span>{{ t.onlineRecheck.answer }}</span>
            </div>
          </div>

          <div class="qm-card">
            <div class="qm-card-title">审计日志</div>
            <el-timeline>
              <el-timeline-item v-for="a in t.audit" :key="a.id" :timestamp="a.createdAt" size="small">
                <b>{{ a.action }}</b> <span class="qm-muted">{{ a.operator }} {{ a.beforeStatus ? a.beforeStatus + ' → ' : '' }}{{ a.afterStatus }}</span>
                <div class="qm-muted" v-if="a.detail">{{ a.detail }}</div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>

        <div class="td-side">
          <div class="qm-card">
            <div class="qm-card-title">验证集配置 <el-tooltip content="默认值来自「设置」页，此处仅对本任务生效"><i class="el-icon-info qm-muted" /></el-tooltip></div>
            <div class="td-cfg">
              <label>总上限</label><el-input-number v-model="cfg.maxCases" :min="1" :max="200" size="mini" :disabled="!editable" />
              <label>同类问题</label><el-input-number v-model="cfg.similarLimit" :min="0" :max="50" size="mini" :disabled="!editable" />
              <label>回归集</label><el-input-number v-model="cfg.regressionLimit" :min="0" :max="100" size="mini" :disabled="!editable" />
              <label>并发</label><el-input-number v-model="cfg.concurrency" :min="1" :max="10" size="mini" :disabled="!editable" />
            </div>
            <div class="qm-muted" style="margin-top: 6px">预计 1 + {{ cfg.similarLimit }} + {{ cfg.regressionLimit }} = {{ 1 + cfg.similarLimit + cfg.regressionLimit }} 题<span v-if="1 + cfg.similarLimit + cfg.regressionLimit > cfg.maxCases" style="color: #e6a23c">（超上限，将裁剪）</span></div>
            <el-input v-model="extra" type="textarea" :rows="2" size="mini" placeholder="临时追加验证问题（每行一个）" style="margin-top: 8px" v-if="['APPLIED', 'VERIFIED'].includes(t.status)" />
          </div>

          <div class="qm-card">
            <div class="qm-card-title">操作</div>
            <div class="td-actions">
              <template v-if="t.status === 'DRAFT'">
                <el-button type="primary" size="small" :loading="acting" @click="act(executeTaskAPI, '执行草稿并开始验证？')">执行并验证</el-button>
                <el-button size="small" :loading="acting" @click="act(cancelTaskAPI, '取消任务？')">取消任务</el-button>
              </template>
              <template v-else-if="t.status === 'VERIFYING'">
                <el-button size="small" type="danger" plain :loading="acting" @click="act(abortVerifyAPI, '中止验证？')">中止验证</el-button>
                <span class="qm-muted">验证进行中，每 3 秒刷新</span>
              </template>
              <template v-else-if="t.status === 'APPLIED'">
                <el-button type="primary" size="small" :loading="acting" @click="reverify">开始验证</el-button>
                <el-button size="small" :loading="acting" @click="rollback">回退</el-button>
              </template>
              <template v-else-if="t.status === 'VERIFIED'">
                <el-button type="primary" size="small" :disabled="!report || report.conclusion === 'FAIL'" @click="approvalVisible = true">提交超级管理员审批</el-button>
                <el-button size="small" :loading="acting" @click="reverify">重新验证</el-button>
                <el-button size="small" :loading="acting" @click="rollback">回退</el-button>
                <div class="qm-muted" v-if="report && report.conclusion === 'FAIL'">验证 FAIL：原问题未修复，请修改建议后重跑</div>
                <div class="qm-muted" v-if="report && report.conclusion === 'PASS_WITH_WARN'">存在退化，提交审批需填写确认说明</div>
              </template>
              <template v-else-if="t.status === 'PENDING_APPROVAL'">
                <el-alert type="warning" :closable="false" show-icon title="等待超级管理员审批" :description="'提交人 ' + t.submittedBy + ' · ' + t.submittedAt" style="margin-bottom: 8px" />
                <el-button size="small" :loading="acting" @click="act(withdrawTaskAPI, '撤回审批？')">撤回</el-button>
                <el-button size="small" :loading="acting" @click="rollback">回退</el-button>
                <template v-if="superAdmin">
                  <el-divider content-position="left"><span class="qm-muted">超级管理员</span></el-divider>
                  <el-input v-model="comment" size="mini" placeholder="审批意见" style="margin-bottom: 6px" />
                  <el-button type="primary" size="small" :loading="acting" @click="approve(true)">通过并发布</el-button>
                  <el-button type="danger" plain size="small" :loading="acting" @click="approve(false)">驳回</el-button>
                </template>
              </template>
              <template v-else-if="t.status === 'PUBLISHED'">
                <div class="qm-kv" style="margin-bottom: 8px">
                  <span class="k">审批</span><span>{{ t.approvedBy }} {{ t.approvedAt }}<div class="qm-muted">{{ t.approvalComment }}</div></span>
                  <span class="k">发布</span><span>{{ t.publishedAt }}</span>
                  <span class="k">观察期至</span><span>{{ t.observeUntil }}</span>
                </div>
                <el-button type="danger" plain size="small" :loading="acting" @click="rollback">回退（无需审批）</el-button>
              </template>
              <template v-else>
                <span class="qm-muted">任务已结束</span>
              </template>
            </div>
          </div>

          <div class="qm-card" v-if="impact">
            <div class="qm-card-title">影响范围</div>
            <div class="qm-kv">
              <span class="k">资产</span><span class="qm-mono">{{ (impact.assets || []).join(', ') || '—' }}</span>
              <span class="k">智能体</span><span>{{ (impact.agents || []).join(', ') }}</span>
              <span class="k">近 30 天</span><span>{{ impact.relatedChats30d }} 条相关问答</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 提交审批 -->
    <el-dialog title="提交超级管理员审批" :visible.sync="approvalVisible" width="520px" append-to-body>
      <div class="qm-kv" v-if="report">
        <span class="k">验证结论</span><span>{{ report.conclusion }}</span>
        <span class="k">变更</span><span>{{ changes.filter((c) => c.accepted).length }} 条</span>
      </div>
      <el-input type="textarea" :rows="3" v-model="confirmNote" style="margin-top: 10px" :placeholder="report && report.conclusion === 'PASS_WITH_WARN' ? '存在退化，请说明确认理由（必填）' : '确认说明（选填）'" />
      <div slot="footer"><el-button @click="approvalVisible = false">取消</el-button><el-button type="primary" :loading="acting" @click="submitApproval">提交</el-button></div>
    </el-dialog>

    <!-- 用例对比 -->
    <el-dialog title="用例 before / after 对比" :visible.sync="caseVisible" width="80%" append-to-body>
      <div v-if="caseSel" class="td-case">
        <div class="qm-card-title">{{ caseSel.question }} <el-tag size="mini">{{ caseSel.caseType }}</el-tag></div>
        <div class="td-case-cols">
          <div class="qm-card"><div class="qm-card-title">期望</div><pre class="qm-mono">{{ fmt(caseSel.expected) }}</pre></div>
          <div class="qm-card"><div class="qm-card-title">调优前</div><pre class="qm-mono">{{ fmt(caseSel.beforeResult) }}</pre></div>
          <div class="qm-card"><div class="qm-card-title">调优后 <span class="qm-muted qm-mono">{{ caseSel.afterRequestId }}</span></div><pre class="qm-mono">{{ fmt(caseSel.afterResult) }}</pre></div>
        </div>
        <div class="qm-card"><div class="qm-card-title">判定</div><pre class="qm-mono">{{ fmt(caseSel.judgeDetail) }}</pre></div>
      </div>
    </el-dialog>

    <prompt-editor :visible.sync="editorVisible" :taskId="taskId" :change="editorChange" :question="t && t.question" @saved="onPromptSaved" />
  </el-drawer>
</template>

<script>
import {
  getTaskDetailAPI, getReportAPI, getImpactAPI, updateTaskChangesAPI, executeTaskAPI, verifyTaskAPI, abortVerifyAPI,
  submitApprovalAPI, withdrawTaskAPI, approveTaskAPI, rollbackTaskAPI, cancelTaskAPI, getVerifyCaseAPI, TASK_STATUS, VERDICT,
} from '@/api/qualityManager/qualityAPI'
import SuggestionTable from './SuggestionTable.vue'
import PromptEditor from './PromptEditor.vue'
import toast from '@/utils/toast'

const STEP = { DRAFT: 1, APPLIED: 2, VERIFYING: 2, VERIFIED: 3, PENDING_APPROVAL: 4, PUBLISHED: 6, ROLLED_BACK: 6, CANCELLED: 1 }

export default {
  name: 'TuningDetail',
  components: { SuggestionTable, PromptEditor },
  props: { visible: Boolean, taskId: Number, superAdmin: Boolean },
  data() {
    return {
      TASK_STATUS, VERDICT, executeTaskAPI, cancelTaskAPI, abortVerifyAPI, withdrawTaskAPI,
      t: null, changes: [], report: null, cases: [], impact: null, cfg: { maxCases: 30, similarLimit: 10, regressionLimit: 15, concurrency: 3 },
      extra: '', loading: false, saving: false, acting: false, timer: null,
      approvalVisible: false, confirmNote: '', comment: '', caseVisible: false, caseSel: null, editorVisible: false, editorChange: null,
    }
  },
  computed: {
    stepIndex() { return this.t ? STEP[this.t.status] || 0 : 0 },
    editable() { return this.t && ['DRAFT', 'VERIFIED'].includes(this.t.status) },
  },
  watch: {
    visible(v) { if (v) this.load(); else this.stopPoll() },
  },
  beforeDestroy() { this.stopPoll() },
  methods: {
    close() { this.stopPoll(); this.$emit('update:visible', false) },
    async load() {
      if (!this.taskId) return
      this.loading = true
      try {
        const [d, r, i] = await Promise.all([getTaskDetailAPI(this.taskId), getReportAPI(this.taskId), getImpactAPI(this.taskId)])
        if (d && d.code === 200) {
          this.t = d.data
          this.changes = (d.data.changes || []).map((c) => ({ ...c, accepted: c.accepted === 1 || c.accepted === true }))
          const ec = d.data.effectiveConfig || {}
          this.cfg = { maxCases: ec.maxCases || 30, similarLimit: ec.similarLimit ?? 10, regressionLimit: ec.regressionLimit ?? 15, concurrency: ec.concurrency || 3 }
        }
        if (r && r.code === 200) { this.report = r.data.report; this.cases = r.data.cases || [] }
        if (i && i.code === 200) this.impact = i.data
        if (this.t && this.t.status === 'VERIFYING') this.startPoll(); else this.stopPoll()
      } finally { this.loading = false }
    },
    startPoll() { if (!this.timer) this.timer = setInterval(() => this.load(), 3000) },
    stopPoll() { if (this.timer) { clearInterval(this.timer); this.timer = null } },
    async saveChanges() {
      this.saving = true
      try {
        const r = await updateTaskChangesAPI(this.taskId, { changes: this.changes, verifyConfig: this.cfg })
        if (r && r.code === 200) { toast.success('已保存'); this.load() }
      } finally { this.saving = false }
    },
    async act(api, confirmText) {
      if (confirmText && !(await this.$confirm(confirmText, '确认', { type: 'warning' }).catch(() => false))) return
      this.acting = true
      try {
        if (this.editable) await updateTaskChangesAPI(this.taskId, { changes: this.changes, verifyConfig: this.cfg })
        const r = await api(this.taskId)
        if (r && r.code === 200) { toast.success('操作成功'); this.load(); this.$emit('changed') }
      } finally { this.acting = false }
    },
    async reverify() {
      this.acting = true
      try {
        const r = await verifyTaskAPI(this.taskId, this.extra.split('\n').map((s) => s.trim()).filter(Boolean))
        if (r && r.code === 200) { toast.success('验证已开始'); this.extra = ''; this.load() }
      } finally { this.acting = false }
    },
    async submitApproval() {
      this.acting = true
      try {
        const r = await submitApprovalAPI(this.taskId, this.confirmNote)
        if (r && r.code === 200) { toast.success(r.data.status === 'PUBLISHED' ? '已发布（配置为无需审批）' : '已提交审批'); this.approvalVisible = false; this.load(); this.$emit('changed') }
      } finally { this.acting = false }
    },
    async approve(ok) {
      if (!ok && !this.comment.trim()) { toast.warn('驳回请填写意见'); return }
      this.acting = true
      try {
        const r = await approveTaskAPI(this.taskId, ok, this.comment)
        if (r && r.code === 200) { toast.success(ok ? '已通过并发布' : '已驳回'); this.load(); this.$emit('changed') }
      } finally { this.acting = false }
    },
    async rollback() {
      const published = this.t.status === 'PUBLISHED'
      const { value } = await this.$prompt(published ? '回退已发布任务：将恢复快照并通知超级管理员。请填写原因' : '回退草稿/验证中的变更？可填写原因', '回退', {
        inputPlaceholder: '回退原因', inputValidator: (v) => (published && !v ? '必填' : true),
      }).catch(() => ({ value: null }))
      if (value === null) return
      this.acting = true
      try {
        const r = await rollbackTaskAPI(this.taskId, value, 'FORCE')
        if (r && r.code === 200) { toast.success('已回退'); this.load(); this.$emit('changed') }
      } finally { this.acting = false }
    },
    async openCase(row) { const r = await getVerifyCaseAPI(row.id); if (r && r.code === 200) { this.caseSel = r.data; this.caseVisible = true } },
    fmt(v) { if (v == null) return '—'; return typeof v === 'string' ? v : JSON.stringify(v, null, 2) },
    openEditor(change) { this.editorChange = change; this.editorVisible = true },
    onPromptSaved({ version, diffSummary }) {
      if (this.editorChange) { this.editorChange.afterValue = version; this.editorChange.diffSummary = diffSummary; this.editorChange.accepted = true }
      this.saveChanges()
    },
  },
}
</script>

<style lang="scss" scoped>
.td-root { height: 100%; display: flex; flex-direction: column; padding: 12px 16px; box-sizing: border-box; overflow: auto; }
.td-head { display: flex; justify-content: space-between; align-items: center; }
.td-title { font-weight: 600; font-size: 15px; }
.td-cols { display: flex; gap: 12px; align-items: flex-start; }
.td-main { flex: 1; min-width: 0; }
.td-side { width: 320px; flex-shrink: 0; }
.td-metrics { display: flex; gap: 8px; }
.td-metric { flex: 1; text-align: center; border: 1px solid #ebeef5; border-radius: 6px; padding: 8px 4px; .v { font-size: 18px; font-weight: 600; } .k { font-size: 11px; color: #909399; } }
.td-cfg { display: grid; grid-template-columns: 70px 1fr; gap: 6px; align-items: center; font-size: 12px; label { color: #606266; } }
.td-actions { display: flex; flex-direction: column; gap: 6px; .el-button { margin-left: 0; } }
.td-case-cols { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; pre { white-space: pre-wrap; font-size: 11px; max-height: 300px; overflow: auto; margin: 0; } }
</style>
