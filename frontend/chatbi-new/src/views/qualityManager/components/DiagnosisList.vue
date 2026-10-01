<template>
  <div>
    <div class="qm-toolbar">
      <el-radio-group v-model="q.quick" size="small" @change="load">
        <el-radio-button label="">全部会话</el-radio-button>
        <el-radio-button label="feedback">有 👎 反馈</el-radio-button>
        <el-radio-button label="failed">执行失败</el-radio-button>
        <el-radio-button label="undiagnosed">未定位</el-radio-button>
      </el-radio-group>
      <el-input v-model="q.keyword" placeholder="搜索问题" clearable @keyup.enter.native="load" />
      <el-select v-model="q.errorType" placeholder="定位结论" clearable @change="load">
        <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
      </el-select>
      <el-input v-model="q.aiBodyCode" placeholder="智能体 code" clearable @keyup.enter.native="load" style="width: 140px" />
      <el-date-picker v-model="range" type="daterange" value-format="yyyy-MM-dd HH:mm:ss" :default-time="['00:00:00', '23:59:59']" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width: 300px" @change="load" />
      <el-button type="primary" size="small" @click="load">查询</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" border stripe height="calc(100vh - 260px)" @row-dblclick="(r) => $emit('diagnose', r.chatId)">
      <el-table-column label="时间" prop="createdAt" width="150" />
      <el-table-column label="用户" prop="username" width="90" />
      <el-table-column label="智能体" prop="aiBodyCode" width="110" />
      <el-table-column label="问题" prop="question" min-width="240" show-overflow-tooltip />
      <el-table-column label="执行" width="90" align="center">
        <template slot-scope="{ row }"><el-tag size="mini" :type="row.status === 'success' ? 'success' : 'danger'">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="耗时" width="80" align="right"><template slot-scope="{ row }">{{ row.totalElapsedMs ? (row.totalElapsedMs / 1000).toFixed(1) + 's' : '-' }}</template></el-table-column>
      <el-table-column label="👎" width="50" align="center"><template slot-scope="{ row }"><span v-if="row.downCount" style="color: #f56c6c">{{ row.downCount }}</span></template></el-table-column>
      <el-table-column label="用户反馈类型" min-width="140">
        <template slot-scope="{ row }"><el-tag v-for="t in row.userErrorTypes" :key="t" size="mini" type="info" style="margin-right: 4px">{{ typeName(t) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="自动预判" width="120">
        <template slot-scope="{ row }"><el-tag v-if="row.autoHint" size="mini" type="warning" effect="plain">{{ typeName(row.autoHint) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="定位结论" width="130">
        <template slot-scope="{ row }">
          <el-tag v-if="row.diagnosed" size="mini">{{ typeName(row.primaryErrorType) }}</el-tag>
          <span v-else class="qm-muted">未定位</span>
        </template>
      </el-table-column>
      <el-table-column label="修复" width="80" align="center">
        <template slot-scope="{ row }"><el-tag v-if="row.diagnosed" size="mini" :type="['warning', 'success', 'success'][row.fixStatus]">{{ FIX_STATUS[row.fixStatus] }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template slot-scope="{ row }"><el-button type="text" size="mini" @click="$emit('diagnose', row.chatId)">{{ row.diagnosed ? '查看' : '去定位' }}</el-button></template>
      </el-table-column>
    </el-table>
    <div class="qm-pager">
      <el-pagination :current-page.sync="q.page" :page-size.sync="q.pageSize" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[20, 50, 100]" @current-change="load" @size-change="load" />
    </div>
  </div>
</template>

<script>
import { getDiagnosisPageAPI, getErrorTypesAPI, FIX_STATUS } from '@/api/qualityManager/qualityAPI'

export default {
  name: 'DiagnosisList',
  data() {
    return { FIX_STATUS, q: { page: 1, pageSize: 20, quick: 'feedback', keyword: '', errorType: '', aiBodyCode: '' }, range: null, rows: [], total: 0, loading: false, types: [] }
  },
  mounted() { this.loadTypes(); this.load() },
  activated() { this.load() },
  methods: {
    async loadTypes() { const r = await getErrorTypesAPI('both'); if (r && r.code === 200) this.types = r.data || [] },
    typeName(code) { const t = this.types.find((x) => x.code === code); return t ? t.name : code },
    async load() {
      this.loading = true
      try {
        const p = { ...this.q, startTime: this.range && this.range[0], endTime: this.range && this.range[1] }
        const r = await getDiagnosisPageAPI(p)
        if (r && r.code === 200) { this.rows = r.data.records || []; this.total = r.data.total || 0 }
      } finally { this.loading = false }
    },
  },
}
</script>
