<template>
  <div>
    <div class="qm-toolbar">
      <el-radio-group v-model="days" size="small" @change="load"><el-radio-button :label="7">近 7 天</el-radio-button><el-radio-button :label="30">近 30 天</el-radio-button><el-radio-button :label="90">近 90 天</el-radio-button></el-radio-group>
      <el-input v-model="aiBodyCode" placeholder="智能体 code" clearable style="width: 140px" @keyup.enter.native="load" />
      <el-button type="primary" size="small" @click="load">查询</el-button>
    </div>
    <div class="st-cards" v-if="s">
      <div class="st-card"><div class="v">{{ s.total }}</div><div class="k">问答总数</div></div>
      <div class="st-card"><div class="v" style="color: #f56c6c">{{ s.failed }}</div><div class="k">执行失败</div></div>
      <div class="st-card"><div class="v" style="color: #2b5cff">{{ s.up }}</div><div class="k">👍</div></div>
      <div class="st-card"><div class="v" style="color: #f56c6c">{{ s.down }}</div><div class="k">👎</div></div>
      <div class="st-card"><div class="v">{{ s.total ? ((1 - s.down / Math.max(1, s.up + s.down)) * 100).toFixed(0) + '%' : '-' }}</div><div class="k">满意率（有反馈）</div></div>
      <div class="st-card"><div class="v" style="color: #e6a23c">{{ s.pendingFeedback }}</div><div class="k">待处理 👎</div></div>
    </div>
    <div class="st-cols" v-if="s">
      <div class="qm-card"><div class="qm-card-title">用户反馈错误类型分布</div><bar :data="s.userErrorTypeDist" /></div>
      <div class="qm-card"><div class="qm-card-title">管理员定位结论分布</div><bar :data="s.adminErrorTypeDist" /></div>
    </div>
  </div>
</template>

<script>
import { getQualityStatsAPI } from '@/api/qualityManager/qualityAPI'
const Bar = {
  functional: true, props: { data: Object },
  render(h, { props }) {
    const entries = Object.entries(props.data || {}); const max = Math.max(1, ...entries.map((e) => e[1]))
    if (!entries.length) return h('div', { class: 'qm-empty' }, '暂无数据')
    return h('div', entries.map(([k, v]) => h('div', { class: 'st-bar' }, [h('span', { class: 'st-bar-k' }, k), h('div', { class: 'st-bar-track' }, [h('div', { class: 'st-bar-fill', style: { width: (v / max) * 100 + '%' } })]), h('span', { class: 'st-bar-v' }, v)])))
  },
}
export default {
  name: 'QualityStats', components: { Bar },
  data() { return { days: 7, aiBodyCode: '', s: null } },
  mounted() { this.load() },
  methods: { async load() { const r = await getQualityStatsAPI({ days: this.days, aiBodyCode: this.aiBodyCode }); if (r && r.code === 200) this.s = r.data } },
}
</script>
<style>
.st-cards { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; margin-bottom: 12px; }
.st-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; text-align: center; background: #fff; }
.st-card .v { font-size: 24px; font-weight: 600; } .st-card .k { font-size: 12px; color: #909399; margin-top: 4px; }
.st-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.st-bar { display: flex; align-items: center; gap: 8px; font-size: 12px; margin: 6px 0; }
.st-bar-k { width: 160px; text-align: right; color: #606266; } .st-bar-v { width: 30px; }
.st-bar-track { flex: 1; height: 14px; background: #f2f3f5; border-radius: 7px; overflow: hidden; } .st-bar-fill { height: 100%; background: #2b5cff; }
</style>
