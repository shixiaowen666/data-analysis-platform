<template>
  <div>
    <div class="qm-toolbar">
      <el-input v-model="q.keyword" placeholder="搜索问题 / 描述 / 用户" clearable @keyup.enter.native="load" />
      <el-select v-model="q.statusStr" placeholder="评价" clearable @change="load">
        <el-option label="👎 踩" value="down" /><el-option label="👍 赞" value="up" />
      </el-select>
      <el-select v-model="q.errorType" placeholder="用户选的错误类型" clearable @change="load">
        <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
      </el-select>
      <el-select v-model="q.status" placeholder="处理状态" clearable @change="load">
        <el-option v-for="(s, i) in FEEDBACK_STATUS" :key="i" :label="s" :value="i" />
      </el-select>
      <el-input v-model="q.aiBodyCode" placeholder="智能体 code" clearable @keyup.enter.native="load" style="width: 140px" />
      <el-button type="primary" size="small" @click="load">查询</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" border stripe height="calc(100vh - 260px)">
      <el-table-column label="时间" prop="createdAt" width="150" />
      <el-table-column label="用户" prop="username" width="90" />
      <el-table-column label="智能体" prop="aiBodyCode" width="110" />
      <el-table-column label="问题" prop="question" min-width="220" show-overflow-tooltip />
      <el-table-column label="评价" width="60" align="center">
        <template slot-scope="{ row }"><span :style="{ color: row.rating > 0 ? '#2b5cff' : '#f56c6c' }">{{ row.rating > 0 ? '👍' : '👎' }}</span></template>
      </el-table-column>
      <el-table-column label="用户选的类型" min-width="160">
        <template slot-scope="{ row }"><el-tag v-for="t in row.errorTypes" :key="t" size="mini" type="info" style="margin-right: 4px">{{ typeName(t) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="错误描述" prop="description" min-width="180" show-overflow-tooltip />
      <el-table-column label="管理员定位" width="130">
        <template slot-scope="{ row }">
          <el-tag v-if="row.adminErrorType" size="mini">{{ typeName(row.adminErrorType) }}</el-tag>
          <span v-else class="qm-muted">未定位</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="{ row }"><el-tag size="mini" :type="['warning', '', 'success', 'info'][row.status]">{{ FEEDBACK_STATUS[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template slot-scope="{ row }">
          <el-button type="text" size="mini" @click="$emit('diagnose', row.chatId)">去定位</el-button>
          <el-button type="text" size="mini" v-if="row.status === 0" @click="ignore(row)">忽略</el-button>
          <el-button type="text" size="mini" v-if="row.status === 3" @click="setStatus(row, 0)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="qm-pager">
      <el-pagination :current-page.sync="q.page" :page-size.sync="q.pageSize" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[20, 50, 100]" @current-change="load" @size-change="load" />
    </div>
  </div>
</template>

<script>
import { getFeedbackPageAPI, getErrorTypesAPI, updateFeedbackStatusAPI, FEEDBACK_STATUS } from '@/api/qualityManager/qualityAPI'
import toast from '@/utils/toast'

export default {
  name: 'FeedbackList',
  data() {
    return { FEEDBACK_STATUS, q: { page: 1, pageSize: 20, keyword: '', statusStr: 'down', errorType: '', status: null, aiBodyCode: '' }, rows: [], total: 0, loading: false, types: [] }
  },
  mounted() { this.loadTypes(); this.load() },
  activated() { this.load() },
  methods: {
    async loadTypes() { const r = await getErrorTypesAPI('both'); if (r && r.code === 200) this.types = r.data || [] },
    typeName(code) { const t = this.types.find((x) => x.code === code); return t ? t.name : code },
    async load() {
      this.loading = true
      try {
        const r = await getFeedbackPageAPI(this.q)
        if (r && r.code === 200) { this.rows = r.data.records || []; this.total = r.data.total || 0 }
      } finally { this.loading = false }
    },
    async ignore(row) { await this.setStatus(row, 3) },
    async setStatus(row, status) {
      const r = await updateFeedbackStatusAPI(row.id, status)
      if (r && r.code === 200) { toast.success('已更新'); this.load() }
    },
  },
}
</script>
