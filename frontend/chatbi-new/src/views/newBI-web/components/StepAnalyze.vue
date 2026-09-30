<template>
  <div class="sana" :class="{ 'is-summary': variant === 'summary' }">
    <div
      ref="body"
      class="sana__body md-body u-scroll-y u-no-scrollbar"
      :class="{ 'is-collapsed': collapsible && !expanded, 'fx-caret': streaming }"
      v-html="html"
    ></div>

    <div v-if="streaming" class="sana__typing">
      <typing-dots :size="4" />
      <span>{{ variant === 'summary' ? '归纳中…' : '分析中…' }}</span>
    </div>

    <div v-if="collapsible && !streaming" class="sana__foot">
      <button class="sana__expand" @click="expanded = !expanded">
        {{ expanded ? '收起' : '展开全文' }}
        <app-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="13" />
      </button>
      <button class="sana__copy" @click="copy">
        <app-icon name="copy" :size="13" />复制
      </button>
    </div>
  </div>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
import TypingDots from '@/components/web/TypingDots.vue';
import { renderMarkdown } from '@/utils/markdown';
import { copyToClipboard } from "@/utils/clipboard.js";
import toast from '@/utils/toast';

export default {
  name: 'StepAnalyze',
  components: { AppIcon, TypingDots },
  props: {
    content: { type: String, default: '' },
    streaming: { type: Boolean, default: false },
    /** analyze | summary */
    variant: { type: String, default: 'analyze' },
  },
  data() {
    return { expanded: false, overflowing: false };
  },
  computed: {
    html() {
      return renderMarkdown(this.content);
    },
    collapsible() {
      return this.overflowing || this.expanded;
    },
  },
  watch: {
    content() {
      if (this.streaming) this.$nextTick(this.scrollBottom);
      else this.$nextTick(this.measure);
    },
    streaming(v) {
      if (!v) this.$nextTick(this.measure);
    },
  },
  mounted() {
    this.$nextTick(this.measure);
  },
  methods: {
    measure() {
      const el = this.$refs.body;
      if (!el) return;
      this.overflowing = el.scrollHeight > 260;
    },
    scrollBottom() {
      const el = this.$refs.body;
      if (el) el.scrollTop = el.scrollHeight;
    },
    async copy() {
      try {
        await copyToClipboard(this.content);
        toast.success('已复制到剪贴板');
      } catch (e) {
        toast.error('复制失败，请手动选择文本');
      }
    },
  },
};
</script>

<style scoped lang="scss">
.sana {
  padding: 12px 13px;
  border-radius: var(--r-sm);
  background: linear-gradient(150deg, rgba(247, 245, 255, 0.9), rgba(245, 250, 255, 0.88));
  border: 1px solid rgba(124, 92, 240, 0.13);

  &.is-summary {
    background: linear-gradient(150deg, rgba(240, 253, 249, 0.92), rgba(244, 251, 255, 0.9));
    border-color: rgba(16, 171, 127, 0.16);
    position: relative;
    padding-left: 15px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 10px;
      bottom: 10px;
      width: 3px;
      border-radius: 0 2px 2px 0;
      background: var(--g-success);
    }
  }

  &__body {
    max-height: 46vh;
    transition: max-height var(--dur-slow) var(--ease-out);

    &.is-collapsed {
      max-height: 230px;
      overflow: hidden;
      -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 78%, transparent 100%);
      mask-image: linear-gradient(to bottom, #000 0, #000 78%, transparent 100%);
    }
  }

  &__typing {
    display: flex;
    align-items: center;
    gap: 7px;
    margin-top: 8px;
    font-size: var(--fs-11);
    color: var(--c-brand-500);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10px;
    padding-top: 9px;
    border-top: 1px dashed rgba(124, 92, 240, 0.16);
  }

  &.is-summary &__foot {
    border-top-color: rgba(16, 171, 127, 0.18);
  }

  &__expand,
  &__copy {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    min-height: 34px;
    padding: 0 10px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-brand-600);
    background: rgba(255, 255, 255, 0.8);
    border: 1px solid var(--c-brand-100);

    &:active {
      transform: scale(0.97);
      background: var(--c-brand-50);
    }
  }

  &.is-summary &__expand,
  &.is-summary &__copy {
    color: var(--c-success);
    border-color: rgba(16, 171, 127, 0.22);
  }
}
</style>
