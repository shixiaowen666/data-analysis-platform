<template>
  <!-- 单行模式 -->
  <el-tooltip
    v-if="!isMultiLine"
    :content="tooltipContent"
    :placement="placement || finalPlacement"
    :effect="effect || finalEffect"
    :open-delay="openDelay || finalOpenDelay"
    :disabled="otherText ? false : !truncated"
  >
    <div
      ref="el"
      class="truncate-tip"
      :style="maxWidth ? { maxWidth: maxWidth + 'px' } : null"
      @mouseenter="check"
      v-html="displayText"
    />
  </el-tooltip>

  <!-- 多行模式 -->
  <el-tooltip
    v-else
    :content="tooltipContent"
    :placement="placement || finalPlacement"
    :effect="effect || finalEffect"
    :open-delay="openDelay || finalOpenDelay"
    :disabled="otherText ? false : !truncated"
  >
    <div
      ref="el"
      class="truncate-tip"
      :style="maxWidth ? { maxWidth: maxWidth + 'px' } : null"
      @mouseenter="check"
    >
      <div
        v-for="(line, index) in displayLines"
        :key="index"
        ref="lineRefs"
        class="line"
        v-html="line"
      />
    </div>
  </el-tooltip>
</template>

<script>
export default {
  name: 'TruncateTip',
  props: {
    text: { type: [String, Number], default: '' },
    otherText: { type: [String, Number], default: '' },
    maxWidth: { type: Number, default: 0 },
    placement: { type: String, default: '' },
    effect: { type: String, default: '' },
    openDelay: { type: Number, default: 0 },
    isMultiLine: {type: Boolean, default: false}
  },
  data() {
    return {
      truncated: false,
      //isMultiLine: false,
    }
  },
  computed: {
    finalPlacement() {
      return this.placement || this.$toolTipPlacement || 'top'
    },
    finalEffect() {
      return this.effect || this.$toolTipEffect || 'dark'
    },
    finalOpenDelay() {
      return this.openDelay || this.$toolTipOpenDelay || 500
    },
    lines() {
      const raw = this.text?.toString() || ''
      return raw.split('\n')
    },
    displayText() {
      return this.text?.toString() || ''
    },
    displayLines() {
      return this.lines
    },
    // Tooltip 显示内容：去掉 HTML 标签，只显示纯文本
    tooltipContent() {
      // 优先使用 otherText
      if (this.otherText) {
        return this.stripHtml(this.otherText.toString())
      }
      // 去掉 HTML 标签
      return this.stripHtml(this.text?.toString() || '')
    },
  },
  watch: {
    text: {
      handler() {
        this.updateMultiLine()
        this.truncated = false
        this.$nextTick(() => {
          this.check()
        })
      },
      immediate: true,
    },
  },
  methods: {
    // 去掉 HTML 标签，只保留纯文本
    stripHtml(str) {
      if (!str) return ''
      // 移除所有 HTML 标签，保留文本内容
      // 将 <br> 替换为换行符
      return str
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<[^>]+>/g, '') // 移除所有 HTML 标签
        .replace(/\n{2,}/g, '\n') // 合并多个换行
        .trim()
    },
    updateMultiLine() {
      const raw = this.text?.toString() || ''
      const lines = raw.split('\n')
      //this.isMultiLine = lines.length > 1
    },
    check() {
      const el = this.$refs.el
      if (!el) return

      if (this.isMultiLine) {
        const lineRefs = this.$refs.lineRefs
        if (!lineRefs) {
          this.truncated = false
          return
        }
        const lineArray = Array.isArray(lineRefs) ? lineRefs : [lineRefs]
        this.truncated = lineArray.some((lineEl) => {
          return lineEl.scrollWidth > lineEl.clientWidth
        })
      } else {
        this.truncated = el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight
      }
    },
  },
}
</script>

<style scoped>
.truncate-tip {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  min-width: 0;
}

.truncate-tip .line {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.8;
}
</style>