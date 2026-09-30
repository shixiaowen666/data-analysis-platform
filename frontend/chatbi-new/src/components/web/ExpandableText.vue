<template>
  <span class="etext" :class="{ 'is-open': open }" @click.stop="toggle">
    <span ref="body" class="etext__body" :style="bodyStyle">{{ text }}</span>
    <span v-if="truncatable" class="etext__more">{{ open ? '收起' : '展开' }}</span>
  </span>
</template>

<script>
/**
 * 点击展开长文本（替代依赖 hover 的 el-tooltip，触屏可用）
 */
export default {
  name: 'ExpandableText',
  props: {
    text: { type: [String, Number], default: '' },
    lines: { type: Number, default: 1 },
  },
  data() {
    return { open: false, truncatable: false };
  },
  computed: {
    bodyStyle() {
      if (this.open) {
        return { display: 'inline', whiteSpace: 'normal', wordBreak: 'break-word' };
      }
      if (this.lines === 1) {
        return { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' };
      }
      return {
        display: '-webkit-box',
        '-webkit-line-clamp': String(this.lines),
        '-webkit-box-orient': 'vertical',
        overflow: 'hidden',
      };
    },
  },
  watch: {
    text() {
      this.$nextTick(this.measure);
    },
  },
  mounted() {
    this.$nextTick(this.measure);
  },
  methods: {
    measure() {
      const el = this.$refs.body;
      if (!el) return;
      this.truncatable = el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.clientHeight + 1;
    },
    toggle() {
      if (!this.truncatable && !this.open) return;
      this.open = !this.open;
      if (!this.open) this.$nextTick(this.measure);
    },
  },
};
</script>

<style scoped lang="scss">
.etext {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
  max-width: 100%;

  &__body {
    min-width: 0;
    flex: 1;
  }

  &__more {
    flex-shrink: 0;
    font-size: var(--fs-11);
    color: var(--c-brand-500);
    opacity: 0.9;
  }

  &.is-open {
    align-items: flex-start;
  }
}
</style>
