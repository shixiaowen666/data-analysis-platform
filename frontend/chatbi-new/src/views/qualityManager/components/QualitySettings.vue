<template>
  <div v-loading="loading" style="max-width: 980px">
    <div class="qm-toolbar">
      <span>作用域</span>
      <el-select v-model="scope" size="small" style="width: 220px" @change="load" allow-create filterable default-first-option placeholder="GLOBAL 或 AGENT:code">
        <el-option v-for="c in scopes" :key="c" :label="c" :value="c" />
      </el-select>
      <span class="qm-muted">智能体级（AGENT:code）覆盖全局；输入新作用域回车可新建</span>
    </div>
    <el-form :model="cfg" label-width="170px" size="small" class="set-form" v-if="cfg">
      <div class="qm-card">
        <div class="qm-card-title">验证集</div>
        <el-form-item label="验证集总上限"><el-input-number v-model="cfg.maxCases" :min="1" :max="200" /> <span class="qm-muted">默认 30；任务页可临时调整</span></el-form-item>
        <el-form-item label="同类历史问题上限"><el-input-number v-model="cfg.similarLimit" :min="0" :max="100" /></el-form-item>
        <el-form-item label="回归集上限"><el-input-number v-model="cfg.regressionLimit" :min="0" :max="100" /></el-form-item>
        <el-form-item label="并发度"><el-input-number v-model="cfg.concurrency" :min="1" :max="10" /></el-form-item>
        <el-form-item label="单题 / 整体超时"><el-input-number v-model="cfg.caseTimeoutS" :min="30" :max="600" /> 秒 &nbsp; <el-input-number v-model="cfg.totalTimeoutMin" :min="5" :max="180" /> 分钟</el-form-item>
        <el-form-item label="判定开关"><el-checkbox :value="cfg.judgeAnswer === 1" @change="(v) => (cfg.judgeAnswer = v ? 1 : 0)">答案关键词/数值</el-checkbox> <el-checkbox :value="cfg.judgeLlm === 1" @change="(v) => (cfg.judgeLlm = v ? 1 : 0)">LLM 辅助判定</el-checkbox></el-form-item>
      </div>
      <div class="qm-card">
        <div class="qm-card-title">发布与审批</div>
        <el-form-item label="发布需超级管理员审批"><el-switch :value="cfg.requireApproval === 1" @change="(v) => (cfg.requireApproval = v ? 1 : 0)" /> <span class="qm-muted">关闭后提交即发布（不建议）</span></el-form-item>
        <el-form-item label="有退化时必须填写说明"><el-switch :value="cfg.warnNeedNote === 1" @change="(v) => (cfg.warnNeedNote = v ? 1 : 0)" /></el-form-item>
        <el-form-item label="发布后线上复验"><el-switch :value="cfg.onlineRecheck === 1" @change="(v) => (cfg.onlineRecheck = v ? 1 : 0)" /></el-form-item>
      </div>
      <div class="qm-card">
        <div class="qm-card-title">观察期告警</div>
        <el-form-item label="观察期"><el-input-number v-model="cfg.observeDays" :min="1" :max="30" /> 天</el-form-item>
        <el-form-item label="告警规则"><el-input-number v-model="cfg.alertWindowHours" :min="1" :max="168" /> 小时内同类 👎 ≥ <el-input-number v-model="cfg.alertThreshold" :min="1" :max="50" /> 条 → 站内告警</el-form-item>
      </div>
      <div class="qm-card">
        <div class="qm-card-title">数据保留</div>
        <el-form-item label="trace 保留"><el-input-number v-model="cfg.retainTraceDays" :min="7" :max="3650" /> 天</el-form-item>
        <el-form-item label="资产快照保留"><el-input-number v-model="cfg.retainSnapshotDays" :min="30" :max="3650" /> 天</el-form-item>
      </div>
      <div style="display: flex; justify-content: flex-end; gap: 8px">
        <span class="qm-muted" v-if="cfg.updatedBy">上次修改 {{ cfg.updatedBy }} {{ cfg.updatedAt }}</span>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </div>
    </el-form>
  </div>
</template>

<script>
import { getConfigAPI, getAllConfigsAPI, saveConfigAPI } from '@/api/qualityManager/qualityAPI'
import toast from '@/utils/toast'
export default {
  name: 'QualitySettings',
  data() { return { scope: 'GLOBAL', scopes: ['GLOBAL'], cfg: null, loading: false, saving: false } },
  mounted() { this.loadScopes(); this.load() },
  methods: {
    async loadScopes() { const r = await getAllConfigsAPI(); if (r && r.code === 200) this.scopes = (r.data || []).map((c) => c.scope) },
    async load() {
      this.loading = true
      try { const r = await getConfigAPI(this.scope); if (r && r.code === 200) { this.cfg = { ...r.data, scope: this.scope }; if (r.data.scope !== this.scope) { delete this.cfg.id; this.cfg.updatedBy = null } } } finally { this.loading = false }
    },
    async save() { this.saving = true; try { const r = await saveConfigAPI(this.cfg); if (r && r.code === 200) { toast.success('已保存'); this.loadScopes(); this.load() } } finally { this.saving = false } },
  },
}
</script>
<style scoped>.set-form ::v-deep .el-form-item { margin-bottom: 10px; }</style>
