<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style quality-manager">
    <div class="allbg">
      <div class="tablebg qm-root">
        <div class="qm-head">
          <el-tabs v-model="tab" class="qm-tabs">
            <el-tab-pane label="用户反馈" name="feedback" />
            <el-tab-pane label="问答诊断" name="diagnosis" />
            <el-tab-pane label="调优任务" name="tuning" />
            <el-tab-pane name="approval">
              <span slot="label">审批中心 <el-badge v-if="pendingCount" :value="pendingCount" class="qm-badge" /></span>
            </el-tab-pane>
            <el-tab-pane label="回归集" name="regression" />
            <el-tab-pane label="质量统计" name="stats" />
            <el-tab-pane label="设置" name="settings" v-if="superAdmin" />
          </el-tabs>
          <div class="qm-notify">
            <el-popover placement="bottom-end" width="360" trigger="click" @show="loadNotifications">
              <div class="qm-notify-list">
                <div v-if="!notifications.length" class="qm-empty">暂无通知</div>
                <div v-for="n in notifications" :key="n.id" class="qm-notify-item" :class="{ unread: !n.readFlag }" @click="openNotification(n)">
                  <div class="qm-notify-title">{{ n.title }}</div>
                  <div class="qm-notify-content">{{ n.content }}</div>
                  <div class="qm-notify-time">{{ n.createdAt }}</div>
                </div>
              </div>
              <el-badge slot="reference" :value="unreadCount" :hidden="!unreadCount">
                <el-button size="small" icon="el-icon-bell" circle />
              </el-badge>
            </el-popover>
          </div>
        </div>

        <div class="qm-body">
          <keep-alive>
            <feedback-list v-if="tab === 'feedback'" @diagnose="openDiagnosis" />
            <diagnosis-list v-else-if="tab === 'diagnosis'" @diagnose="openDiagnosis" />
            <tuning-list v-else-if="tab === 'tuning'" @open="openTask" />
            <approval-center v-else-if="tab === 'approval'" @open="openTask" @changed="loadPending" />
            <regression-list v-else-if="tab === 'regression'" />
            <quality-stats v-else-if="tab === 'stats'" />
            <quality-settings v-else-if="tab === 'settings'" />
          </keep-alive>
        </div>
      </div>
    </div>

    <!-- 诊断页（抽屉，三栏） -->
    <diagnosis-detail
      :visible.sync="diagVisible"
      :chatId="diagChatId"
      @task-created="onTaskCreated"
    />
    <!-- 调优任务详情 -->
    <tuning-detail
      :visible.sync="taskVisible"
      :taskId="taskId"
      :superAdmin="superAdmin"
      @changed="loadPending"
    />
  </div>
</template>

<script>
import { getPendingApprovalsAPI, getNotificationsAPI, readNotificationAPI } from '@/api/qualityManager/qualityAPI'
import { isSuperAdmin } from '@/utils/auth'
import FeedbackList from './components/FeedbackList.vue'
import DiagnosisList from './components/DiagnosisList.vue'
import DiagnosisDetail from './components/DiagnosisDetail.vue'
import TuningList from './components/TuningList.vue'
import TuningDetail from './components/TuningDetail.vue'
import ApprovalCenter from './components/ApprovalCenter.vue'
import RegressionList from './components/RegressionList.vue'
import QualityStats from './components/QualityStats.vue'
import QualitySettings from './components/QualitySettings.vue'

export default {
  name: 'qualityManager',
  components: { FeedbackList, DiagnosisList, DiagnosisDetail, TuningList, TuningDetail, ApprovalCenter, RegressionList, QualityStats, QualitySettings },
  data() {
    return {
      tab: 'feedback',
      superAdmin: false,
      pendingCount: 0,
      notifications: [],
      diagVisible: false,
      diagChatId: '',
      taskVisible: false,
      taskId: null,
    }
  },
  computed: {
    unreadCount() { return this.notifications.filter((n) => !n.readFlag).length },
  },
  mounted() {
    this.superAdmin = isSuperAdmin()
    this.loadPending()
    this.loadNotifications()
  },
  methods: {
    async loadPending() {
      const r = await getPendingApprovalsAPI()
      if (r && r.code === 200) this.pendingCount = (r.data || []).length
    },
    async loadNotifications() {
      const r = await getNotificationsAPI(false)
      if (r && r.code === 200) this.notifications = r.data || []
    },
    async openNotification(n) {
      if (!n.readFlag) { await readNotificationAPI(n.id); n.readFlag = 1 }
      if (n.taskId) this.openTask(n.taskId)
    },
    openDiagnosis(chatId) { this.diagChatId = chatId; this.diagVisible = true },
    openTask(id) { this.taskId = id; this.taskVisible = true },
    onTaskCreated(id) { this.diagVisible = false; this.tab = 'tuning'; this.openTask(id) },
  },
}
</script>

<style lang="scss">
.quality-manager {
  .qm-root { display: flex; flex-direction: column; height: 100%; padding: 0 16px 12px; }
  .qm-head { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #ebeef5; }
  .qm-tabs { flex: 1; .el-tabs__header { margin-bottom: 0; } .el-tabs__nav-wrap::after { display: none; } }
  .qm-badge { .el-badge__content { transform: translateY(-2px); } }
  .qm-body { flex: 1; min-height: 0; overflow: auto; padding-top: 12px; }
  .qm-toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
  .qm-toolbar .el-input, .qm-toolbar .el-select { width: 180px; }
  .qm-pager { display: flex; justify-content: flex-end; margin-top: 10px; }
  .qm-empty { color: #909399; text-align: center; padding: 24px 0; font-size: 13px; }
  .qm-muted { color: #909399; font-size: 12px; }
  .qm-mono { font-family: Menlo, Consolas, monospace; font-size: 12px; }
  .qm-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 14px; margin-bottom: 12px; background: #fff; }
  .qm-card-title { font-weight: 600; font-size: 13px; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
  .qm-kv { display: grid; grid-template-columns: 90px 1fr; gap: 4px 8px; font-size: 12px; .k { color: #909399; } }
  .qm-notify-list { max-height: 400px; overflow: auto; }
  .qm-notify-item { padding: 8px 6px; border-bottom: 1px solid #f2f3f5; cursor: pointer; &.unread .qm-notify-title { font-weight: 600; } }
  .qm-notify-title { font-size: 13px; }
  .qm-notify-content { font-size: 12px; color: #606266; margin-top: 2px; }
  .qm-notify-time { font-size: 11px; color: #c0c4cc; margin-top: 2px; }
  .qm-stage { display: flex; gap: 6px; align-items: stretch; }
  .qm-stage-item { flex: 1; border: 1px solid #ebeef5; border-radius: 6px; padding: 8px; font-size: 12px; cursor: pointer;
    &.ok { border-color: #e1f3d8; background: #f0f9eb; } &.warn { border-color: #faecd8; background: #fdf6ec; }
    &.failed { border-color: #fde2e2; background: #fef0f0; } &.active { outline: 2px solid #2b5cff; } }
  .qm-stage-name { font-weight: 600; }
  .qm-stage-sum { color: #606266; margin-top: 4px; word-break: break-all; }
  .diff-add { background: #e6ffed; } .diff-del { background: #ffeef0; text-decoration: line-through; color: #999; }
}
</style>
