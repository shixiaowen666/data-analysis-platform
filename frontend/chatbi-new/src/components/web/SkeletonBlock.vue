<template>
  <div class="skeleton" :class="`skeleton--${variant}`">
    <!-- 文本行 -->
    <template v-if="variant === 'text'">
      <div
        v-for="i in rows"
        :key="i"
        class="skeleton__line fx-shimmer"
        :style="{ width: lineWidth(i), height: lineHeight }"
      ></div>
    </template>

    <!-- 卡片 -->
    <template v-else-if="variant === 'card'">
      <div v-for="i in rows" :key="i" class="skeleton__card">
        <div class="skeleton__row">
          <div class="skeleton__dot fx-shimmer"></div>
          <div class="skeleton__line fx-shimmer" style="width: 46%; height: 13px"></div>
        </div>
        <div class="skeleton__line fx-shimmer" style="width: 92%; height: 11px"></div>
        <div class="skeleton__line fx-shimmer" style="width: 66%; height: 11px"></div>
      </div>
    </template>

    <!-- 列表行 -->
    <template v-else-if="variant === 'list'">
      <div v-for="i in rows" :key="i" class="skeleton__listitem">
        <div class="skeleton__line fx-shimmer" :style="{ width: lineWidth(i), height: '13px' }"></div>
        <div class="skeleton__line fx-shimmer" style="width: 34%; height: 10px"></div>
      </div>
    </template>

    <!-- 数据表格 -->
    <template v-else-if="variant === 'record'">
      <div class="skeleton__record">
        <div v-for="i in rows" :key="i" class="skeleton__kv">
          <div class="skeleton__line fx-shimmer" style="width: 32%; height: 12px"></div>
          <div class="skeleton__line fx-shimmer" :style="{ width: lineWidth(i), height: '12px' }"></div>
        </div>
      </div>
    </template>

    <!-- 纯块 -->
    <div
      v-else
      class="skeleton__line fx-shimmer"
      :style="{ width: '100%', height: lineHeight, borderRadius: 'var(--r-md)' }"
    ></div>
  </div>
</template>

<script>
export default {
  name: 'SkeletonBlock',
  props: {
    /** text | card | list | record | block */
    variant: { type: String, default: 'text' },
    rows: { type: Number, default: 3 },
    lineHeight: { type: String, default: '12px' },
  },
  methods: {
    lineWidth(i) {
      const widths = ['92%', '78%', '86%', '64%', '88%', '72%'];
      return widths[(i - 1) % widths.length];
    },
  },
};
</script>

<style scoped lang="scss">
.skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__line {
    border-radius: 6px;
    background-color: var(--c-brand-100);
  }

  &__dot {
    width: 22px;
    height: 22px;
    border-radius: 7px;
    background-color: var(--c-brand-100);
    flex-shrink: 0;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__card {
    display: flex;
    flex-direction: column;
    gap: 9px;
    padding: 14px;
    border-radius: var(--r-md);
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid var(--bd-light);
  }

  &__listitem {
    display: flex;
    flex-direction: column;
    gap: 7px;
    padding: 13px 4px;
    border-bottom: 1px solid var(--bd-light);
  }

  &__record {
    display: flex;
    flex-direction: column;
    gap: 11px;
    padding: 14px;
    border-radius: var(--r-md);
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid var(--bd-light);
  }

  &__kv {
    display: flex;
    align-items: center;
    gap: 12px;
    > *:last-child {
      margin-left: auto;
    }
  }
}
</style>
