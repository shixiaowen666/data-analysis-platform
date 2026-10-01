<template>
  <div class="ap-root">
    <div class="ap-list">
      <div class="qm-card-title" style="padding: 0 4px">待审批 <span class="qm-muted">{{ rows.length }}</span></div>
      <div v-if="!rows.length" class="qm-empty">暂无待审批任务</div>
      <div v-for="t in rows" :key="t.id" class="ap-item" :class="{ active: sel && sel.id === t.id }" @click="select(t)">
        <div><b>{{ t.taskNo }}</b> <el-tag size="mini" type="warning">待审批</el-tag></div>
        <div class="ap-q">{{ t.question }}</div>
        <div class="qm-muted">{{ t.submittedBy }} · {{ t.submittedAt }}</div>
      </div>
    </div>
    <div class="ap-detail" v-if="sel && detail">
      <div class="qm-card">
        <div class="qm-card-title">审批单 {{ sel.taskNo }}<el-button type="text" size="mini" @click="$emit('open', sel.id)">查看任务全貌</el-button></div>
        <div class="qm-kv">
          <span class="k">来源问题</span><span>{{ detail.question }}</span>
          <span class="k">错误类型</span><span>{{ detail.primaryErrorType }}</span>
          <span class="k">智能体</span><span>{{ detail.aiBodyCode }}</span>
          <span class="k">提交人</span><span>{{ detail.submittedBy }} {{ detail.submittedAt }}</span>
          <span class="k">确认说明</span><span>{{ detail.confirmNote || '—' }}</span>
          <span class="k">影响范围</span><span v-if="impact">资产 {{ (impact.assets || []).join(', ') }} · 近 30 天相关问答 {{ impact.relatedChats30d }} 条<span v-if="impact.promptChange"> · 含提示词变更</span></span>
        </div>
      </div>
      <div class="qm-card" v-if="detail.report">
        <div class="qm-card-title">验证结论 <el-tag size="small" :type="{ PASS: 'success', PASS_WITH_WARN: 'warning', FAIL: 'danger' }[detail.report.conclusion]">{{ detail.report.conclusion }}</el-tag></div>
        <div class="qm-kv">
          <span class="k">原问题</span><span>{{ detail.report.originFixed ? '✅ 已修复' : '❌ 未修复' }}</span>
          <span class="k">回归集</span><span>{{ detail.report.regressionPass }} / {{ detail.report.regressionTotal }} 通过</span>
          <span class="k">退化 / 改善</span><span><b :style="{ color: detail.report.degradedCount ? '#f56c6c' : '' }">{{ detail.report.degradedCount }}</b> / {{ detail.report.improvedCount }}</span>
          <span class="k">耗时</span><span>{{ detail.report.avgElapsedBeforeMs }}ms → {{ detail.report.avgElapsedAfterMs }}ms</span>
        </div>
      </div>
      <div class="qm-card">
        <div class="qm-card-title">变更摘要（{{ changes.length }} 条）</div>
        <suggestion-table :value="changes" readonly />
      </div>
      <div class="qm-card">
        <el-input type="textarea" :rows="2" v-model="comment" placeholder="审批意见（驳回时必填）" />
        <div style="margin-top: 10px; display: flex; justify-content: flex-end; gap: 8px">
          <el-button size="small" type="danger" plain :loading="saving" @click="decide(false)">驳回</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="decide(true)">通过并发布</el-button>
        </div>
      </div>
    </div>
    <div class="ap-detail qm-empty" v-else>选择左侧任务查看审批单</div>
  </div>
</template>

<script>
import { getPendingApprovalsAPI, getTaskDetailAPI, getImpactAPI, approveTaskAPI } from '@/api/qualityManager/qualityAPI'
import SuggestionTable from './SuggestionTable.vue'
import toast from '@/utils/toast'
export default {
  name: 'ApprovalCenter',
  components: { SuggestionTable },
  data() { return { rows: [], sel: null, detail: null, impact: null, comment: '', saving: false } },
  mounted() { this.load() },
  activated() { this.load() },
  methods: {
    async load() { const r = await getPendingApprovalsAPI(); if (r && r.code === 200) { this.rows = r.data || []; if (this.sel && !this.rows.find((x) => x.id === this.sel.id)) { this.sel = null; this.detail = null } } },
    async select(t) {
      this.sel = t; this.comment = ''
      const [d, i] = await Promise.all([getTaskDetailAPI(t.id), getImpactAPI(t.id)])
      if (d && d.code === 200) this.detail = d.data
      if (i && i.code === 200) this.impact = i.data
    },
    async decide(ok) {
      if (!ok && !this.comment.trim()) { toast.warn('驳回请填写意见'); return }
      if (ok && !(await this.$confirm('通过后系统将立即发布变更到线上，确认？', '审批通过', { type: 'warning' }).catch(() => false))) return
      this.saving = true
      try {
        const r = await approveTaskAPI(this.sel.id, ok, this.comment)
        if (r && r.code === 200) { toast.success(ok ? '已通过并发布' : '已驳回'); this.sel = null; this.detail = null; this.load(); this.$emit('changed') }
      } finally { this.saving = false }
    },
  },
  computed: { changes() { return this.detail ? (this.detail.changes || []).filter((c) => c.accepted) : [] } },
}
</script>
<style scoped>
.ap-root { display: flex; gap: 12px; height: calc(100vh - 200px); }
.ap-list { width: 320px; border-right: 1px solid #ebeef5; overflow: auto; padding-right: 8px; }
.ap-item { padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; margin: 8px 4px; cursor: pointer; font-size: 13px; }
.ap-item.active { border-color: #2b5cff; background: #f3f6ff; }
.ap-q { margin: 4px 0; color: #303133; }
.ap-detail { flex: 1; overflow: auto; }
</style>
