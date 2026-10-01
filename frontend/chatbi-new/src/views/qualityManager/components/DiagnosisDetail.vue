<template>
  <el-drawer :visible="visible" size="92%" :with-header="false" append-to-body destroy-on-close @close="$emit('update:visible', false)">
    <div class="dg-root quality-manager" v-loading="loading">
      <div class="dg-head">
        <div>
          <span class="dg-title">问答诊断</span>
          <span class="qm-mono qm-muted" style="margin-left: 10px">{{ chatId }}</span>
          <el-tag v-if="data.autoHint" size="mini" type="warning" effect="plain" style="margin-left: 10px">自动预判：{{ typeName(data.autoHint) }}</el-tag>
        </div>
        <div>
          <el-button size="small" @click="showLog">System B 日志</el-button>
          <el-button size="small" icon="el-icon-close" @click="$emit('update:visible', false)">关闭</el-button>
        </div>
      </div>

      <div class="dg-cols">
        <!-- 左：问答回放 -->
        <div class="dg-col dg-left">
          <div class="qm-card">
            <div class="qm-card-title">用户问题</div>
            <div class="dg-q">{{ trace.question }}</div>
            <div class="qm-kv" style="margin-top: 8px">
              <span class="k">用户</span><span>{{ trace.username }}</span>
              <span class="k">智能体</span><span>{{ trace.aiBodyCode }}</span>
              <span class="k">时间</span><span>{{ trace.createdAt }}</span>
              <span class="k">状态</span><span><el-tag size="mini" :type="trace.status === 'success' ? 'success' : 'danger'">{{ trace.status }}</el-tag> {{ trace.totalElapsedMs ? (trace.totalElapsedMs / 1000).toFixed(1) + 's' : '' }}</span>
              <span class="k">提示词</span><span class="qm-mono">{{ trace.promptVersion }}</span>
            </div>
          </div>
          <div class="qm-card">
            <div class="qm-card-title">最终回答</div>
            <div class="dg-answer">{{ trace.finalAnswer || '（无）' }}</div>
          </div>
          <div class="qm-card" v-if="data.feedbacks && data.feedbacks.length">
            <div class="qm-card-title">用户反馈</div>
            <div v-for="f in data.feedbacks" :key="f.id" class="dg-fb">
              <span>{{ f.rating > 0 ? '👍' : '👎' }}</span> <b>{{ f.username }}</b>
              <el-tag v-for="t in f.errorTypes" :key="t" size="mini" type="info" style="margin-left: 4px">{{ typeName(t) }}</el-tag>
              <div class="qm-muted" style="margin-top: 4px">{{ f.description }}</div>
            </div>
          </div>
        </div>

        <!-- 中：链路追踪 -->
        <div class="dg-col dg-mid">
          <div class="qm-stage">
            <div v-for="s in data.stages" :key="s.key" class="qm-stage-item" :class="[s.status, { active: stage === s.key }]" @click="stage = s.key">
              <div class="qm-stage-name">{{ s.name }}</div>
              <div class="qm-stage-sum">{{ s.summary || '—' }}</div>
              <el-button type="text" size="mini" style="padding: 2px 0" @click.stop="judge(s.errorType, s.key)">判为{{ typeName(s.errorType) }}</el-button>
            </div>
          </div>

          <div class="qm-card" v-if="stage === 'recall'">
            <div class="qm-card-title">① 召回结果 <span class="qm-muted">recall {{ trace.recallEnabled ? '生效' : '未启用' }} {{ trace.recallElapsedMs ? trace.recallElapsedMs + 'ms' : '' }}</span></div>
            <div class="qm-kv">
              <span class="k">表</span><span><el-tag v-for="t in arr(trace.recallTables)" :key="t" size="mini" :type="t === form.expectedTable ? 'success' : ''" style="margin: 2px">{{ t }}</el-tag><span v-if="!arr(trace.recallTables).length" class="qm-muted">全量 {{ trace.metaTableCount }} 张</span></span>
              <span class="k">指标</span><span class="qm-mono">{{ arr(trace.recallMetricCodes).join(', ') || ('全量 ' + trace.metaMetricCount) }}</span>
              <span class="k">维度</span><span class="qm-mono">{{ arr(trace.recallDimCodes).join(', ') || ('全量 ' + trace.metaDimCount) }}</span>
            </div>
            <div v-if="form.expectedTable && !arr(trace.recallTables).includes(form.expectedTable) && arr(trace.recallTables).length" class="dg-warn">应选表 {{ form.expectedTable }} 不在召回结果中 → 规则 R1</div>
          </div>

          <div class="qm-card" v-if="stage === 'decompose'">
            <div class="qm-card-title">② 拆解步骤 <span class="qm-muted">{{ steps.length }} 步</span></div>
            <div v-for="s in steps" :key="s.step_id" class="dg-step" :class="{ failed: failedSteps.includes(s.step_id), picked: form.errorStepId === s.step_id }" @click="form.errorStepId = s.step_id">
              <div><b>{{ s.step_id }}</b> <el-tag size="mini" type="info">{{ s.step_type }}</el-tag> {{ s.description }}</div>
              <div class="qm-mono qm-muted" v-if="s.params && s.params.expected_table">表 {{ s.params.expected_table }} · 列 {{ (s.params.expected_columns || []).map((c) => c.name).join(', ') }}</div>
              <div class="qm-mono qm-muted" v-if="s.params && s.params.function">fn {{ s.params.function }}</div>
            </div>
            <div class="qm-muted" v-if="!steps.length">无拆解步骤（拆解失败或未落库）</div>
          </div>

          <div class="qm-card" v-if="stage === 'mapping' || stage === 'execute'">
            <div class="qm-card-title">③④ 取数映射 / 执行</div>
            <div v-for="s in data.steps" :key="s.stepId" class="dg-step" :class="{ failed: !!s.errorMessage, picked: form.errorStepId === s.stepId }" @click="form.errorStepId = s.stepId">
              <div><b>{{ s.stepId }}</b> {{ s.queryDescription }}</div>
              <div class="qm-kv" style="margin-top: 4px">
                <span class="k">应选表</span><span class="qm-mono">{{ s.expectedTable || '—' }} <el-tag v-if="s.resolvedTableId" size="mini" type="success">命中 #{{ s.resolvedTableId }}</el-tag><el-tag v-else-if="s.expectedTable" size="mini" type="danger">未命中→降级</el-tag></span>
                <span class="k">指标</span><span>{{ (s.resolvedIndicatorNames || []).join(', ') || '—' }}</span>
                <span class="k">维度</span><span>{{ (s.resolvedDimensionNames || []).join(', ') || '—' }}</span>
                <span class="k">未映射列</span><span><el-tag v-for="u in s.unmatchedColumns || []" :key="u" size="mini" type="danger" style="margin-right: 4px">{{ u }}</el-tag><span v-if="!(s.unmatchedColumns || []).length" class="qm-muted">无</span></span>
                <span class="k">过滤</span><span class="qm-mono">{{ (s.filters || []).map((f) => f.field + ' ' + f.operator + ' ' + (f.values || []).join('/')).join('; ') || '—' }}</span>
                <span class="k">结果</span><span>{{ s.errorMessage ? '❌ ' + s.errorMessage : (s.rowCount == null ? '—' : s.rowCount + ' 行') }} · {{ s.elapsedMs }}ms</span>
              </div>
              <el-collapse v-if="s.sqlText" class="dg-sql"><el-collapse-item title="SQL"><pre class="qm-mono">{{ s.sqlText }}</pre></el-collapse-item></el-collapse>
            </div>
            <div class="qm-muted" v-if="!data.steps || !data.steps.length">无取数步骤</div>
          </div>

          <div class="qm-card" v-if="stage === 'summary'">
            <div class="qm-card-title">⑤ 总结</div>
            <div class="qm-kv">
              <span class="k">输入行数</span><span>{{ totalRows }}</span>
              <span class="k">输出</span><span>{{ trace.finalAnswer }}</span>
            </div>
            <div v-if="totalRows === 0 && /\d/.test(trace.finalAnswer || '')" class="dg-warn">输入 0 行但答案含数值 → 疑似总结编造</div>
          </div>
        </div>

        <!-- 右：定位表单 -->
        <div class="dg-col dg-right">
          <el-form label-position="top" size="small" class="dg-form">
            <el-form-item label="主要错误类型 *">
              <el-radio-group v-model="form.primaryErrorType" class="dg-radio">
                <el-radio v-for="t in topTypes" :key="t.code" :label="t.code">{{ t.name }}</el-radio>
              </el-radio-group>
              <el-radio-group v-if="form.primaryErrorType && form.primaryErrorType.startsWith('METRIC_DIM')" v-model="form.primaryErrorType" size="mini" style="margin-top: 6px">
                <el-radio-button label="METRIC_DIM">指标+维度</el-radio-button>
                <el-radio-button label="METRIC_DIM.METRIC">指标</el-radio-button>
                <el-radio-button label="METRIC_DIM.DIMENSION">维度</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="次要错误类型">
              <el-checkbox-group v-model="form.secondaryErrorTypes">
                <el-checkbox v-for="t in topTypes.filter((x) => !form.primaryErrorType || !form.primaryErrorType.startsWith(x.code))" :key="t.code" :label="t.code">{{ t.name }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="出错步骤">
              <el-select v-model="form.errorStepId" clearable placeholder="点击中间步骤可选中" style="width: 100%">
                <el-option v-for="s in steps" :key="s.step_id" :label="s.step_id + ' ' + (s.description || '')" :value="s.step_id" />
              </el-select>
            </el-form-item>
            <el-form-item label="应选表（选表错误）">
              <el-input v-model="form.expectedTable" placeholder="如 view_line_loss_by_voltage" />
              <div class="qm-muted" style="margin-top: 2px">实际：{{ actualTable || '—' }}</div>
            </el-form-item>
            <el-form-item label="应为实体（指标/维度错误）">
              <div v-for="(e, i) in form.expectedEntities" :key="i" class="dg-ent">
                <el-select v-model="e.type" style="width: 80px"><el-option label="指标" value="metric" /><el-option label="维度" value="dim" /></el-select>
                <el-input v-model="e.code" placeholder="code" style="flex: 1" />
                <el-input v-model="e.alias" placeholder="别名（问题中的说法）" style="flex: 1" />
                <el-button type="text" icon="el-icon-delete" @click="form.expectedEntities.splice(i, 1)" />
              </div>
              <el-button type="text" icon="el-icon-plus" @click="form.expectedEntities.push({ type: 'metric', code: '', alias: '' })">添加</el-button>
            </el-form-item>
            <el-form-item label="根因说明">
              <el-input type="textarea" :rows="3" v-model="form.rootCause" placeholder="如：月累计指标被 sum；「分压」未映射到电压等级维度" />
            </el-form-item>
            <el-form-item label="修复方式">
              <el-select v-model="form.fixActionType" clearable style="width: 100%">
                <el-option v-for="(v, k) in ASSET_TYPE" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item label="期望答案关键词（验证用，逗号分隔）">
              <el-input v-model="keywords" placeholder="如：电压等级, 3.2%" />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="form.addRegression">加入回归集</el-checkbox>
            </el-form-item>
            <div v-if="data.diagnosis" class="qm-muted" style="margin-bottom: 8px">
              上次定位：{{ data.diagnosis.diagnosedBy }} {{ data.diagnosis.diagnosedAt }} · 修复状态 {{ FIX_STATUS[data.diagnosis.fixStatus] }}
            </div>
            <div class="dg-actions">
              <el-button size="small" :loading="saving" @click="save(false)">保存定位</el-button>
              <el-button size="small" type="primary" :loading="saving" @click="save(true)">保存并生成调优建议</el-button>
            </div>
          </el-form>
        </div>
      </div>
    </div>

    <el-dialog title="System B 日志" :visible.sync="logVisible" width="70%" append-to-body>
      <pre class="qm-mono dg-log">{{ logText }}</pre>
    </el-dialog>

    <!-- 建议 → 创建任务 -->
    <el-dialog title="调优建议" :visible.sync="sugVisible" width="860px" append-to-body>
      <suggestion-table v-model="suggestions" />
      <div style="margin-top: 10px" class="qm-muted">勾选的建议将作为任务变更；低置信（提示词类）默认不勾选，需在任务内打开编辑器完成。</div>
      <div slot="footer">
        <el-button @click="sugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTask">创建调优任务</el-button>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script>
import { getTraceAPI, getSystemBLogAPI, saveDiagnosisAPI, getErrorTypesAPI, createTaskAPI, ASSET_TYPE, FIX_STATUS } from '@/api/qualityManager/qualityAPI'
import SuggestionTable from './SuggestionTable.vue'
import toast from '@/utils/toast'

export default {
  name: 'DiagnosisDetail',
  components: { SuggestionTable },
  props: { visible: Boolean, chatId: String },
  data() {
    return {
      ASSET_TYPE, FIX_STATUS, loading: false, saving: false, data: {}, types: [], stage: 'decompose',
      form: this.emptyForm(), keywords: '', logVisible: false, logText: '', sugVisible: false, suggestions: [], diagnosisId: null,
    }
  },
  computed: {
    trace() { return this.data.trace || {} },
    steps() { return Array.isArray(this.trace.decompositionSteps) ? this.trace.decompositionSteps : [] },
    failedSteps() { return (Array.isArray(this.trace.executionLog) ? this.trace.executionLog : []).filter((e) => e.status !== 'success').map((e) => e.step_id) },
    topTypes() { return this.types.filter((t) => !t.parentCode && t.code !== 'RESULT') },
    actualTable() { return this.arr(this.trace.usedTables)[0] || '' },
    totalRows() { return (this.data.steps || []).reduce((a, s) => a + (s.rowCount || 0), 0) },
  },
  watch: {
    visible(v) { if (v) this.load() },
  },
  methods: {
    emptyForm() {
      return { primaryErrorType: '', secondaryErrorTypes: [], errorStepId: '', expectedTable: '', expectedEntities: [], rootCause: '', fixActionType: '', addRegression: true }
    },
    arr(v) { return Array.isArray(v) ? v : [] },
    typeName(code) { const t = this.types.find((x) => x.code === code); return t ? t.name : code },
    async load() {
      this.loading = true
      try {
        if (!this.types.length) { const t = await getErrorTypesAPI('both'); if (t && t.code === 200) this.types = t.data || [] }
        const r = await getTraceAPI(this.chatId)
        if (r && r.code === 200) {
          this.data = r.data
          const d = r.data.diagnosis
          this.form = d ? {
            primaryErrorType: d.primaryErrorType, secondaryErrorTypes: this.arr(d.secondaryErrorTypes), errorStepId: d.errorStepId || '',
            expectedTable: d.expectedTable || '', expectedEntities: this.arr(d.expectedEntities), rootCause: d.rootCause || '',
            fixActionType: d.fixActionType || '', addRegression: d.addRegression === 1,
          } : { ...this.emptyForm(), primaryErrorType: r.data.autoHint || '' }
          this.keywords = d ? this.arr(d.expectedAnswerKeywords).join(', ') : ''
          this.diagnosisId = d ? d.id : null
          this.stage = { TABLE_SELECT: 'recall', METRIC_DIM: 'mapping', DECOMPOSE: 'decompose', SUMMARY: 'summary', INTENT: 'decompose' }[(r.data.autoHint || '').split('.')[0]] || 'decompose'
        }
      } finally { this.loading = false }
    },
    judge(type, stageKey) { this.form.primaryErrorType = type; this.stage = stageKey },
    async showLog() {
      this.logVisible = true; this.logText = '加载中…'
      const r = await getSystemBLogAPI(this.chatId)
      this.logText = r && r.code === 200 ? (r.data.data ? r.data.data.content : r.data.content) || JSON.stringify(r.data, null, 2) : (r && r.message) || '获取失败'
    },
    payload(gen) {
      return {
        chatId: this.chatId, chatSessionId: this.trace.chatSessionId, aiBodyCode: this.trace.aiBodyCode,
        ...this.form, actualTable: this.actualTable,
        expectedEntities: this.form.expectedEntities.filter((e) => e.code),
        expectedAnswerKeywords: this.keywords.split(/[,，]/).map((s) => s.trim()).filter(Boolean),
        generateSuggestions: gen,
      }
    },
    async save(gen) {
      if (!this.form.primaryErrorType) { toast.warn('请选择主要错误类型'); return }
      this.saving = true
      try {
        const r = await saveDiagnosisAPI(this.payload(gen))
        if (r && r.code === 200) {
          toast.success('定位已保存')
          this.diagnosisId = r.data.diagnosis.id
          if (gen) { this.suggestions = r.data.suggestions || []; this.sugVisible = true } else { this.load() }
        }
      } finally { this.saving = false }
    },
    async createTask() {
      const changes = this.suggestions.filter((s) => s.accepted)
      if (!changes.length) { toast.warn('请至少勾选一条建议'); return }
      this.saving = true
      try {
        const r = await createTaskAPI({ diagnosisId: this.diagnosisId, changes })
        if (r && r.code === 200) { toast.success('任务 ' + r.data.taskNo + ' 已创建'); this.sugVisible = false; this.$emit('task-created', r.data.id) }
      } finally { this.saving = false }
    },
  },
}
</script>

<style lang="scss" scoped>
.dg-root { height: 100%; display: flex; flex-direction: column; padding: 12px 16px; box-sizing: border-box; }
.dg-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.dg-title { font-weight: 600; font-size: 15px; }
.dg-cols { flex: 1; display: flex; gap: 12px; min-height: 0; }
.dg-col { overflow: auto; min-height: 0; }
.dg-left { width: 24%; } .dg-mid { flex: 1; } .dg-right { width: 28%; border-left: 1px solid #ebeef5; padding-left: 12px; }
.dg-q { font-size: 14px; font-weight: 500; }
.dg-answer { white-space: pre-wrap; font-size: 13px; line-height: 1.6; max-height: 320px; overflow: auto; }
.dg-fb { font-size: 13px; padding: 6px 0; border-bottom: 1px dashed #ebeef5; }
.dg-step { border: 1px solid #ebeef5; border-radius: 6px; padding: 8px; margin-top: 8px; font-size: 13px; cursor: pointer;
  &.failed { border-color: #fbc4c4; background: #fff5f5; } &.picked { border-color: #2b5cff; box-shadow: 0 0 0 1px #2b5cff inset; } }
.dg-warn { margin-top: 8px; padding: 6px 10px; border-radius: 4px; background: #fdf6ec; color: #e6a23c; font-size: 12px; }
.dg-sql pre { white-space: pre-wrap; margin: 0; font-size: 11px; }
.dg-form ::v-deep .el-form-item { margin-bottom: 12px; }
.dg-radio ::v-deep .el-radio { margin-right: 12px; margin-bottom: 6px; }
.dg-ent { display: flex; gap: 6px; margin-bottom: 6px; }
.dg-actions { display: flex; gap: 8px; justify-content: flex-end; }
.dg-log { max-height: 60vh; overflow: auto; white-space: pre-wrap; font-size: 12px; background: #fafafa; padding: 10px; }
</style>
