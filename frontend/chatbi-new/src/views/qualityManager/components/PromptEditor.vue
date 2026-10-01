<template>
  <el-dialog :visible="visible" fullscreen append-to-body :show-close="false" custom-class="pe-dialog" @close="$emit('update:visible', false)">
    <div slot="title" class="pe-head">
      <div>
        <b>提示词编辑器</b>
        <el-select v-model="group" size="mini" style="width: 160px; margin-left: 10px" @change="loadContext">
          <el-option label="prompt-main（拆解）" value="prompt-main" /><el-option label="prompt-summary（总结）" value="prompt-summary" />
        </el-select>
        <span class="qm-muted" style="margin-left: 10px">基线 </span>
        <el-select v-model="baseVersion" size="mini" style="width: 120px" @change="loadBase">
          <el-option v-for="v in versions" :key="v.version" :label="v.version + (v.is_active ? ' ●' : '')" :value="v.version" />
        </el-select>
        <span class="qm-muted" style="margin-left: 10px">+{{ diff.added }} / −{{ diff.removed }} 行 · {{ content.length - base.length >= 0 ? '+' : '' }}{{ content.length - base.length }} 字</span>
      </div>
      <div>
        <el-button size="mini" :loading="quick" @click="quickVerify">快速验证（仅原问题）</el-button>
        <el-button size="mini" @click="$emit('update:visible', false)">关闭</el-button>
      </div>
    </div>

    <div class="pe-body">
      <!-- 左：参考 -->
      <div class="pe-ref">
        <div class="qm-card">
          <div class="qm-card-title">原问题</div>
          <div>{{ ctx.question || question || '—' }}</div>
        </div>
        <div class="qm-card">
          <div class="qm-card-title">根因</div>
          <div>{{ ctx.rootCause || '—' }}</div>
          <div class="qm-card-title" style="margin-top: 8px">规则条目定位</div>
          <div><el-tag v-for="a in ctx.anchors || []" :key="a" size="mini" style="margin: 2px; cursor: pointer" @click="jumpTo(a)">{{ a }}</el-tag><span v-if="!(ctx.anchors || []).length" class="qm-muted">无关键词命中</span></div>
          <el-input v-model="find" size="mini" placeholder="查找…" style="margin-top: 8px" @keyup.enter.native="jumpTo(find)">
            <el-button slot="append" icon="el-icon-search" @click="jumpTo(find)" />
          </el-input>
          <div style="display: flex; gap: 4px; margin-top: 4px">
            <el-input v-model="replace" size="mini" placeholder="替换为…" /><el-button size="mini" @click="doReplace">替换全部</el-button>
          </div>
        </div>
        <div class="qm-card">
          <div class="qm-card-title">失败步骤</div>
          <pre class="qm-mono pe-pre">{{ JSON.stringify(ctx.failedSteps || [], null, 2) }}</pre>
        </div>
        <div class="qm-card" v-if="lint">
          <div class="qm-card-title">校验 <el-tag size="mini" :type="lint.ok ? 'success' : 'danger'">{{ lint.ok ? '通过' : '有错误' }}</el-tag></div>
          <div v-for="e in lint.errors" :key="'e' + e" style="color: #f56c6c; font-size: 12px">✗ {{ e }}</div>
          <div v-for="w in lint.warnings" :key="'w' + w" style="color: #e6a23c; font-size: 12px">! {{ w }}</div>
          <div class="qm-muted" style="font-size: 11px; margin-top: 4px">占位符：{{ (lint.placeholders || []).join(', ') }}</div>
        </div>
        <div class="qm-card" v-if="quickResult">
          <div class="qm-card-title">快速验证结果 <el-tag size="mini" :type="quickResult.ok ? 'success' : 'danger'">{{ quickResult.ok ? '成功' : '失败' }}</el-tag></div>
          <div class="qm-muted">临时版本 {{ quickResult.version }} · {{ quickResult.elapsedMs }}ms</div>
          <pre class="qm-mono pe-pre">{{ JSON.stringify(quickResult.result || quickResult.message, null, 2) }}</pre>
        </div>
      </div>

      <!-- 中：编辑 -->
      <div class="pe-edit">
        <div class="pe-gutter" ref="gutter"><div v-for="n in lineCount" :key="n">{{ n }}</div></div>
        <textarea ref="ta" v-model="content" class="pe-ta qm-mono" spellcheck="false" @scroll="syncScroll" @input="onInput" />
      </div>

      <!-- 右：diff -->
      <div class="pe-diff">
        <div class="qm-card-title">与基线 {{ baseVersion }} 的差异</div>
        <div class="pe-diff-body qm-mono">
          <div v-for="(h, i) in diff.hunks" :key="i" :class="'dl dl-' + h.type">
            <span class="ln">{{ h.lineNo && (h.lineNo.b || h.lineNo.a) || '' }}</span>
            <span class="mk">{{ { add: '+', del: '-', ctx: ' ', skip: '…' }[h.type] }}</span>
            <span class="tx">{{ h.line }}</span>
          </div>
          <div v-if="!diff.hunks.length" class="qm-empty">尚无改动</div>
        </div>
        <div class="pe-save">
          <el-input v-model="version" size="small" placeholder="版本号（自动 +1）" style="width: 130px" />
          <el-input v-model="description" size="small" placeholder="版本说明（必填）" style="flex: 1" />
          <el-button type="primary" size="small" :loading="saving" :disabled="!diff.added && !diff.removed" @click="save">保存为草稿版本</el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { getEditorContextAPI, promptListAPI, promptVersionContentAPI, promptLintAPI, promptVersionCreateAPI, quickVerifyAPI } from '@/api/qualityManager/qualityAPI'
import toast from '@/utils/toast'

// 本地行 diff（LCS），与 System B diff_lines 语义一致，用于实时预览
function lineDiff(a, b) {
  const A = a.split('\n'), B = b.split('\n')
  const n = A.length, m = B.length
  const dp = Array.from({ length: n + 1 }, () => new Uint16Array(m + 1))
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) dp[i][j] = A[i] === B[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
  const hunks = []; let i = 0, j = 0, added = 0, removed = 0
  while (i < n && j < m) {
    if (A[i] === B[j]) { hunks.push({ type: 'ctx', line: A[i], lineNo: { a: i + 1, b: j + 1 } }); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { hunks.push({ type: 'del', line: A[i], lineNo: { a: i + 1 } }); removed++; i++ }
    else { hunks.push({ type: 'add', line: B[j], lineNo: { b: j + 1 } }); added++; j++ }
  }
  while (i < n) { hunks.push({ type: 'del', line: A[i], lineNo: { a: i + 1 } }); removed++; i++ }
  while (j < m) { hunks.push({ type: 'add', line: B[j], lineNo: { b: j + 1 } }); added++; j++ }
  // 压缩上下文：只保留改动附近 2 行
  const keep = new Set()
  hunks.forEach((h, k) => { if (h.type !== 'ctx') for (let d = -2; d <= 2; d++) keep.add(k + d) })
  const out = []; let skipping = false
  hunks.forEach((h, k) => { if (keep.has(k)) { out.push(h); skipping = false } else if (!skipping) { out.push({ type: 'skip', line: '…', lineNo: {} }); skipping = true } })
  return { added, removed, hunks: added || removed ? out : [] }
}

export default {
  name: 'PromptEditor',
  props: { visible: Boolean, taskId: Number, change: Object, question: String },
  data() {
    return { group: 'prompt-main', groupId: null, versions: [], baseVersion: '', base: '', content: '', ctx: {}, lint: null, diff: { added: 0, removed: 0, hunks: [] }, find: '', replace: '', version: '', description: '', saving: false, quick: false, quickResult: null, lintTimer: null }
  },
  computed: { lineCount() { return this.content.split('\n').length } },
  watch: {
    visible(v) { if (v) { this.group = this.change && this.change.assetType === 'PROMPT_SUMMARY' ? 'prompt-summary' : 'prompt-main'; this.quickResult = null; this.loadContext() } },
  },
  methods: {
    async loadContext() {
      const r = await getEditorContextAPI({ taskId: this.taskId, groupName: this.group })
      if (r && r.code === 200) {
        this.ctx = r.data || {}
        this.groupId = this.ctx.current && this.ctx.current.group_id
      }
      if (this.groupId) {
        const l = await promptListAPI({ group_id: this.groupId, page: 1, page_size: 50 })
        const data = l && l.data; const list = data && (data.list || data.items || data.records || (Array.isArray(data) ? data : []))
        this.versions = list || []
        const active = this.versions.find((v) => v.is_active)
        this.baseVersion = (this.ctx.current && this.ctx.current.version) || (active && active.version) || (this.versions[0] && this.versions[0].version) || ''
      }
      await this.loadBase()
    },
    async loadBase() {
      if (!this.groupId || !this.baseVersion) return
      const r = await promptVersionContentAPI({ group_id: this.groupId, version: this.baseVersion })
      if (r && r.code === 200) { this.base = r.data.content || ''; this.content = this.base; this.diff = { added: 0, removed: 0, hunks: [] }; this.lint = null }
    },
    onInput() {
      this.diff = lineDiff(this.base, this.content)
      clearTimeout(this.lintTimer)
      this.lintTimer = setTimeout(async () => { const r = await promptLintAPI({ group_name: this.group, content: this.content }); if (r && r.code === 200) this.lint = r.data }, 500)
    },
    syncScroll() { this.$refs.gutter.scrollTop = this.$refs.ta.scrollTop },
    jumpTo(kw) {
      if (!kw) return
      const idx = this.content.indexOf(kw)
      if (idx < 0) { toast.warn('未找到「' + kw + '」'); return }
      const ta = this.$refs.ta; ta.focus(); ta.setSelectionRange(idx, idx + kw.length)
      const line = this.content.slice(0, idx).split('\n').length
      ta.scrollTop = Math.max(0, (line - 5) * 18)
    },
    doReplace() { if (!this.find) return; this.content = this.content.split(this.find).join(this.replace); this.onInput() },
    async quickVerify() {
      this.quick = true
      try { const r = await quickVerifyAPI({ taskId: this.taskId, groupName: this.group, content: this.content, question: this.question }); if (r && r.code === 200) this.quickResult = r.data } finally { this.quick = false }
    },
    async save() {
      if (!this.description.trim()) { toast.warn('请填写版本说明'); return }
      this.saving = true
      try {
        const r = await promptVersionCreateAPI({ group_name: this.group, base_version: this.baseVersion, content: this.content, version: this.version || undefined, description: this.description })
        if (r && r.code === 200) { toast.success('草稿版本 ' + r.data.version + ' 已保存（未激活）'); this.$emit('saved', { version: r.data.version, diffSummary: r.data.diff_summary }); this.$emit('update:visible', false) }
        else if (r && r.code === 422) { this.lint = r.data.lint; toast.error('校验未通过') }
      } finally { this.saving = false }
    },
  },
}
</script>

<style lang="scss">
.pe-dialog { .el-dialog__header { padding: 10px 16px; border-bottom: 1px solid #ebeef5; } .el-dialog__body { padding: 0; height: calc(100vh - 56px); } }
.pe-head { display: flex; justify-content: space-between; align-items: center; }
.pe-body { display: flex; height: 100%; }
.pe-ref { width: 22%; overflow: auto; padding: 10px; border-right: 1px solid #ebeef5; }
.pe-pre { white-space: pre-wrap; font-size: 11px; max-height: 220px; overflow: auto; margin: 0; }
.pe-edit { flex: 1; display: flex; min-width: 0; }
.pe-gutter { width: 44px; overflow: hidden; background: #fafafa; color: #c0c4cc; text-align: right; padding: 8px 6px 8px 0; font-size: 12px; line-height: 18px; font-family: Menlo, Consolas, monospace; border-right: 1px solid #ebeef5; }
.pe-ta { flex: 1; border: 0; outline: 0; resize: none; padding: 8px 10px; font-size: 12px; line-height: 18px; }
.pe-diff { width: 30%; display: flex; flex-direction: column; border-left: 1px solid #ebeef5; padding: 10px; }
.pe-diff-body { flex: 1; overflow: auto; font-size: 11px; line-height: 18px; }
.dl { display: flex; white-space: pre-wrap; .ln { width: 36px; color: #c0c4cc; text-align: right; margin-right: 6px; flex-shrink: 0; } .mk { width: 12px; flex-shrink: 0; } .tx { flex: 1; word-break: break-all; } }
.dl-add { background: #e6ffed; } .dl-del { background: #ffeef0; } .dl-skip { color: #c0c4cc; }
.pe-save { display: flex; gap: 6px; margin-top: 8px; }
</style>
