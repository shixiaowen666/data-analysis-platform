<template>
  <div>
    <div class="qm-toolbar">
      <el-input v-model="q.keyword" placeholder="搜索问题" clearable @keyup.enter.native="load" />
      <el-input v-model="q.aiBodyCode" placeholder="智能体 code" clearable style="width: 140px" @keyup.enter.native="load" />
      <el-select v-model="q.statusStr" placeholder="来源" clearable @change="load">
        <el-option label="定位时加入" value="DIAGNOSIS" /><el-option label="👍 自动沉淀" value="FEEDBACK_UP" /><el-option label="手动" value="MANUAL" />
      </el-select>
      <el-button type="primary" size="small" @click="load">查询</el-button>
      <el-button size="small" icon="el-icon-plus" @click="edit(null)">新增用例</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" border stripe height="calc(100vh - 260px)">
      <el-table-column label="问题" prop="question" min-width="260" show-overflow-tooltip />
      <el-table-column label="智能体" prop="aiBodyCode" width="110" />
      <el-table-column label="期望" min-width="220"><template slot-scope="{ row }"><span class="qm-mono qm-muted">{{ brief(row.expected) }}</span></template></el-table-column>
      <el-table-column label="标签" min-width="140"><template slot-scope="{ row }"><el-tag v-for="t in parse(row.tags)" :key="t" size="mini" type="info" style="margin: 1px">{{ t }}</el-tag></template></el-table-column>
      <el-table-column label="来源" prop="source" width="110" />
      <el-table-column label="最近通过" prop="lastPassAt" width="150" />
      <el-table-column label="失败次数" prop="failCount" width="80" align="center" />
      <el-table-column label="启用" width="70" align="center"><template slot-scope="{ row }"><el-switch :value="row.enabled === 1" @change="(v) => toggle(row, v)" /></template></el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template slot-scope="{ row }"><el-button type="text" size="mini" @click="edit(row)">编辑</el-button><el-button type="text" size="mini" style="color: #f56c6c" @click="del(row)">删除</el-button></template>
      </el-table-column>
    </el-table>
    <div class="qm-pager"><el-pagination :current-page.sync="q.page" :page-size.sync="q.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load" /></div>

    <el-dialog :title="form.id ? '编辑用例' : '新增用例'" :visible.sync="dlg" width="600px" append-to-body>
      <el-form label-width="90px" size="small">
        <el-form-item label="智能体 code"><el-input v-model="form.aiBodyCode" /></el-form-item>
        <el-form-item label="问题"><el-input type="textarea" :rows="2" v-model="form.question" /></el-form-item>
        <el-form-item label="期望表"><el-input v-model="exp.table" /></el-form-item>
        <el-form-item label="期望指标"><el-input v-model="exp.metrics" placeholder="code，逗号分隔" /></el-form-item>
        <el-form-item label="期望维度"><el-input v-model="exp.dims" placeholder="code，逗号分隔" /></el-form-item>
        <el-form-item label="答案关键词"><el-input v-model="exp.answer_keywords" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="tags" placeholder="逗号分隔（表名/指标 code，用于按变更资产挑选）" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="dlg = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { getRegressionPageAPI, saveRegressionAPI, deleteRegressionAPI, toggleRegressionAPI } from '@/api/qualityManager/qualityAPI'
import toast from '@/utils/toast'
const split = (s) => (s || '').split(/[,，]/).map((x) => x.trim()).filter(Boolean)
export default {
  name: 'RegressionList',
  data() { return { q: { page: 1, pageSize: 20, keyword: '', aiBodyCode: '', statusStr: '' }, rows: [], total: 0, loading: false, dlg: false, form: {}, exp: {}, tags: '' } },
  mounted() { this.load() },
  activated() { this.load() },
  methods: {
    parse(v) { if (Array.isArray(v)) return v; try { return JSON.parse(v || '[]') } catch (e) { return [] } },
    brief(v) { try { const e = typeof v === 'string' ? JSON.parse(v) : v || {}; return ['表 ' + (e.table || '-'), '指标 ' + (e.metrics || []).join('/'), '维度 ' + (e.dims || []).join('/')].join(' · ') } catch (x) { return v } },
    async load() { this.loading = true; try { const r = await getRegressionPageAPI(this.q); if (r && r.code === 200) { this.rows = r.data.records || []; this.total = r.data.total || 0 } } finally { this.loading = false } },
    edit(row) {
      const e = row ? (typeof row.expected === 'string' ? JSON.parse(row.expected || '{}') : row.expected || {}) : {}
      this.form = row ? { id: row.id, aiBodyCode: row.aiBodyCode, question: row.question, source: row.source, sourceChatId: row.sourceChatId, enabled: row.enabled === 1 } : { source: 'MANUAL', enabled: true }
      this.exp = { table: e.table || '', metrics: (e.metrics || []).join(','), dims: (e.dims || []).join(','), answer_keywords: (e.answer_keywords || []).join(',') }
      this.tags = row ? this.parse(row.tags).join(',') : ''
      this.dlg = true
    },
    async save() {
      const expected = { table: this.exp.table || null, metrics: split(this.exp.metrics), dims: split(this.exp.dims), answer_keywords: split(this.exp.answer_keywords) }
      const r = await saveRegressionAPI({ ...this.form, expected, tags: split(this.tags) })
      if (r && r.code === 200) { toast.success('已保存'); this.dlg = false; this.load() }
    },
    async del(row) { if (!(await this.$confirm('删除该用例？', '确认', { type: 'warning' }).catch(() => false))) return; const r = await deleteRegressionAPI(row.id); if (r && r.code === 200) this.load() },
    async toggle(row, v) { const r = await toggleRegressionAPI(row.id, v); if (r && r.code === 200) row.enabled = v ? 1 : 0 },
  },
}
</script>
