<template>
  <div class="fb-bar" v-if="chatId">
    <el-tooltip content="回答有帮助" :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay">
      <el-button type="text" class="fb-btn" :class="{ 'is-active': rating === 1 }" @click="rateUp">
        <i class="el-icon-thumb"></i>
      </el-button>
    </el-tooltip>
    <el-tooltip content="回答有误" :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay">
      <el-button type="text" class="fb-btn fb-btn--down" :class="{ 'is-active': rating === -1 }" @click="openDown">
        <i class="el-icon-thumb fb-flip"></i>
      </el-button>
    </el-tooltip>
    <span v-if="rating === -1 && submitted" class="fb-hint">已反馈 · <a @click="openDown">修改</a></span>

    <el-dialog title="反馈问题" :visible.sync="visible" width="520px" append-to-body :close-on-click-modal="false" class="fb-dialog">
      <div class="fb-q">{{ question }}</div>
      <div class="fb-label">哪里出错了？（可多选）</div>
      <div class="fb-chips">
        <el-tag
          v-for="t in types"
          :key="t.code"
          :type="form.errorTypes.includes(t.code) ? '' : 'info'"
          :effect="form.errorTypes.includes(t.code) ? 'dark' : 'plain'"
          class="fb-chip"
          @click="toggle(t.code)"
        >{{ t.name }}</el-tag>
      </div>
      <div class="fb-label">
        错误描述（选填，≤500 字）
        <span v-if="form.errorTypes.includes('RESULT')" class="fb-tip">选择「结果错误」时请写清期望结果，便于定位</span>
      </div>
      <el-input type="textarea" v-model="form.description" :rows="4" maxlength="500" show-word-limit placeholder="例如：应该查分压线损率表，而不是分区表" />
      <div slot="footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!form.errorTypes.length" @click="submitDown">提交反馈</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getErrorTypesAPI, submitFeedbackAPI, getMyFeedbackAPI } from '@/api/qualityManager/qualityAPI'
import toast from '@/utils/toast'

let cachedTypes = null

export default {
  name: 'feedbackBar',
  props: {
    chatSessionId: { type: String, default: '' },
    chatId: { type: String, default: '' },
    aiBodyCode: { type: String, default: '' },
    question: { type: String, default: '' },
    answer: { type: String, default: '' },
  },
  data() {
    return {
      rating: 0,
      submitted: false,
      visible: false,
      saving: false,
      types: [],
      form: { errorTypes: [], description: '' },
    }
  },
  watch: {
    chatId: { immediate: true, handler() { this.loadMine() } },
  },
  methods: {
    async loadMine() {
      if (!this.chatId) return
      try {
        const r = await getMyFeedbackAPI(this.chatId)
        if (r && r.code === 200 && r.data) {
          this.rating = r.data.rating
          this.submitted = true
          this.form.errorTypes = this.parse(r.data.errorTypes)
          this.form.description = r.data.description || ''
        }
      } catch (e) { /* ignore */ }
    },
    parse(v) {
      if (Array.isArray(v)) return v
      try { return JSON.parse(v || '[]') } catch (e) { return [] }
    },
    async ensureTypes() {
      if (cachedTypes) { this.types = cachedTypes; return }
      const r = await getErrorTypesAPI('user')
      if (r && r.code === 200) {
        // 用户侧只展示一级（含 RESULT），细分项交给管理员
        cachedTypes = (r.data || []).filter((t) => !t.parentCode)
        this.types = cachedTypes
      }
    },
    async rateUp() {
      if (this.rating === 1) return
      const r = await submitFeedbackAPI(this.payload(1))
      if (r && r.code === 200) {
        this.rating = 1
        this.submitted = true
        toast.success('感谢反馈')
      }
    },
    async openDown() {
      await this.ensureTypes()
      this.visible = true
    },
    toggle(code) {
      const i = this.form.errorTypes.indexOf(code)
      if (i > -1) this.form.errorTypes.splice(i, 1)
      else this.form.errorTypes.push(code)
    },
    payload(rating) {
      return {
        chatSessionId: this.chatSessionId,
        chatId: this.chatId,
        aiBodyCode: this.aiBodyCode,
        question: this.question,
        answerSnapshot: (this.answer || '').slice(0, 4000),
        rating,
        errorTypes: rating < 0 ? this.form.errorTypes : [],
        description: rating < 0 ? this.form.description : '',
      }
    },
    async submitDown() {
      this.saving = true
      try {
        const r = await submitFeedbackAPI(this.payload(-1))
        if (r && r.code === 200) {
          this.rating = -1
          this.submitted = true
          this.visible = false
          toast.success('已提交，感谢反馈')
        }
      } finally {
        this.saving = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.fb-bar { display: inline-flex; align-items: center; gap: 2px; margin-left: 6px; }
.fb-btn { padding: 4px !important; height: 22px; color: #909399; i { font-size: 14px; } }
.fb-btn.is-active { color: #2b5cff; }
.fb-btn--down.is-active { color: #f56c6c; }
.fb-flip { display: inline-block; transform: rotate(180deg); }
.fb-hint { font-size: 11px; color: #909399; margin-left: 4px; a { color: #2b5cff; cursor: pointer; } }
.fb-q { background: #f5f7fa; border-radius: 6px; padding: 8px 12px; color: #606266; font-size: 13px; margin-bottom: 12px; }
.fb-label { font-size: 13px; color: #303133; margin: 10px 0 6px; font-weight: 500; }
.fb-tip { font-weight: normal; color: #e6a23c; font-size: 12px; margin-left: 8px; }
.fb-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.fb-chip { cursor: pointer; user-select: none; }
</style>
