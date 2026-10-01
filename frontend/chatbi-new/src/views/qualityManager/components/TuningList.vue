<template>
  <div>
    <div class="qm-toolbar">
      <el-radio-group v-model="q.statusStr" size="small" @change="load">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="DRAFT">草稿</el-radio-button>
        <el-radio-button label="VERIFYING">验证中</el-radio-button>
        <el-radio-button label="VERIFIED">待提交</el-radio-button>
        <el-radio-button label="PENDING_APPROVAL">待审批</el-radio-button>
        <el-radio-button label="PUBLISHED">已发布</el-radio-button>
        <el-radio-button label="ROLLED_BACK">已回退</el-radio-button>
      </el-radio-group>
      <el-input v-model="q.keyword" placeholder="任务号 / 问题" clearable @keyup.enter.native="load" />
      <el-input v-model="q.aiBodyCode" placeholder="智能体 code" clearable style="width: 140px" @keyup.enter.native="load" />
      <el-button type="primary" size="small" @click="load">查询</el-button>
      <el-button size="small" icon="el-icon-refresh" @click="load" />
    </div>
    <el-table :data="rows" v-loading="loading" size="small" border stripe height="calc(100vh - 260px)" @row-dblclick="(r) => $emit('open', r.id)">
      <el-table-column label="任务号" prop="taskNo" width="150"><template slot-scope="{ row }"><a class="qm-link" @click="$emit('open', row.id)">{{ row.taskNo }}</a></template></el-table-column>
      <el-table-column label="状态" width="90" align="center"><template slot-scope="{ row }"><el-tag size="mini" :type="TASK_STATUS[row.status].type">{{ TASK_STATUS[row.status].label }}</el-tag></template></el-table-column>
      <el-table-column label="来源问题" prop="question" min-width="240" show-overflow-tooltip />
      <el-table-column label="错误类型" prop="primaryErrorType" width="140" />
      <el-table-column label="智能体" prop="aiBodyCode" width="110" />
      <el-table-column label="变更" prop="changeCount" width="60" align="center" />
      <el-table-column label="轮次" prop="round" width="60" align="center" />
      <el-table-column label="验证结果" min-width="200">
        <template slot-scope="{ row }">
          <template v-if="row.verifySummary">
            <el-tag size="mini" :type="{ PASS: 'success', PASS_WITH_WARN: 'warning', FAIL: 'danger' }[row.verifySummary.conclusion]">{{ row.verifySummary.conclusion }}</el-tag>
            <span class="qm-muted" style="margin-left: 6px">原问题 {{ row.verifySummary.fixed ? '✅' : '❌' }} · 回归 {{ row.verifySummary.regression_pass }}/{{ row.verifySummary.regression_total }} · 退化 {{ row.verifySummary.degraded }}</span>
          </template>
          <span v-else class="qm-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="观察告警" width="110"><template slot-scope="{ row }"><el-tag v-if="row.observeAlert" size="mini" type="danger">👎 ×{{ row.observeAlert.count }}</el-tag></template></el-table-column>
      <el-table-column label="创建" width="150"><template slot-scope="{ row }">{{ row.createdBy }}<div class="qm-muted">{{ row.createdAt }}</div></template></el-table-column>
      <el-table-column label="操作" width="80" fixed="right"><template slot-scope="{ row }"><el-button type="text" size="mini" @click="$emit('open', row.id)">详情</el-button></template></el-table-column>
    </el-table>
    <div class="qm-pager">
      <el-pagination :current-page.sync="q.page" :page-size.sync="q.pageSize" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[20, 50]" @current-change="load" @size-change="load" />
    </div>
  </div>
</template>

<script>
import { getTaskPageAPI, TASK_STATUS } from '@/api/qualityManager/qualityAPI'
export default {
  name: 'TuningList',
  data() { return { TASK_STATUS, q: { page: 1, pageSize: 20, statusStr: '', keyword: '', aiBodyCode: '' }, rows: [], total: 0, loading: false } },
  mounted() { this.load() },
  activated() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try { const r = await getTaskPageAPI(this.q); if (r && r.code === 200) { this.rows = r.data.records || []; this.total = r.data.total || 0 } } finally { this.loading = false }
    },
  },
}
</script>
<style scoped>.qm-link { color: #2b5cff; cursor: pointer; }</style>
