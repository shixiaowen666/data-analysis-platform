<template>
  <div class="state-block fx-fade-up" :class="`state-block--${type}`">
    <div class="state-block__art">
      <svg viewBox="0 0 120 96" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient :id="gid" x1="0" y1="0" x2="120" y2="96" gradientUnits="userSpaceOnUse">
            <stop :stop-color="c1" />
            <stop offset="1" :stop-color="c2" />
          </linearGradient>
        </defs>
        <!-- 底座 -->
        <ellipse cx="60" cy="82" rx="38" ry="6" :fill="c1" opacity="0.5" />
        <!-- 主体卡片 -->
        <rect x="26" y="20" width="68" height="52" rx="8" fill="#fff" :stroke="c2" stroke-width="1.4" />
        <rect x="36" y="32" width="30" height="4" rx="2" :fill="`url(#${gid})`" opacity="0.85" />
        <rect x="36" y="42" width="48" height="3.4" rx="1.7" :fill="c1" />
        <rect x="36" y="51" width="38" height="3.4" rx="1.7" :fill="c1" />
        <rect x="36" y="60" width="26" height="3.4" rx="1.7" :fill="c1" />
        <!-- 角标 -->
        <circle cx="92" cy="24" r="12" :fill="`url(#${gid})`" opacity="0.14" />
        <g :stroke="c2" stroke-width="1.8" stroke-linecap="round">
          <template v-if="type === 'error'">
            <path d="M88 20l8 8M96 20l-8 8" />
          </template>
          <template v-else-if="type === 'success'">
            <path d="M87.5 24.2l3 3 5-5.6" />
          </template>
          <template v-else>
            <circle cx="90.6" cy="22.6" r="4.6" fill="none" />
            <path d="M94.4 26.4L98 30" />
          </template>
        </g>
        <!-- 装饰点阵 -->
        <g :fill="c2" opacity="0.42">
          <circle cx="18" cy="30" r="1.6" />
          <circle cx="14" cy="46" r="1.2" />
          <circle cx="104" cy="52" r="1.6" />
          <circle cx="108" cy="66" r="1.2" />
        </g>
      </svg>
    </div>

    <div class="state-block__title">{{ title }}</div>
    <div v-if="desc" class="state-block__desc">{{ desc }}</div>

    <div v-if="actionText || $slots.action" class="state-block__actions">
      <slot name="action">
        <button class="state-block__btn fx-press" @click="$emit('action')">
          <app-icon :name="actionIcon" :size="15" />
          <span>{{ actionText }}</span>
        </button>
      </slot>
    </div>
  </div>
</template>

<script>
import AppIcon from './AppIcon.vue';

let uid = 0;

export default {
  name: 'StateBlock',
  components: { AppIcon },
  props: {
    /** empty | error | success | search */
    type: { type: String, default: 'empty' },
    title: { type: String, default: '暂无数据' },
    desc: { type: String, default: '' },
    actionText: { type: String, default: '' },
    actionIcon: { type: String, default: 'refresh' },
  },
  data() {
    uid += 1;
    return { gid: `sbg${uid}` };
  },
  computed: {
    c1() {
      return this.type === 'error' ? '#fbe3e4' : '#e4eefd';
    },
    c2() {
      return this.type === 'error' ? '#e5484d' : this.type === 'success' ? '#10ab7f' : '#4f8cf5';
    },
  },
};
</script>

<style scoped lang="scss">
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 26px 20px 22px;
  gap: 4px;

  &__art {
    width: 108px;
    margin-bottom: 6px;
    svg {
      width: 100%;
      height: auto;
    }
  }

  &__title {
    font-size: var(--fs-14);
    font-weight: 600;
    color: var(--c-ink-700);
  }

  &__desc {
    font-size: var(--fs-12);
    color: var(--c-ink-400);
    line-height: 1.6;
    max-width: 240px;
    margin-top: 2px;
  }

  &__actions {
    margin-top: 14px;
  }

  &__btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 40px;
    padding: 0 20px;
    border-radius: var(--r-full);
    font-size: var(--fs-13);
    font-weight: 500;
    color: var(--c-brand-600);
    background: var(--c-brand-50);
    border: 1px solid var(--c-brand-200);

    &:active {
      background: var(--c-brand-100);
    }
  }

  &--error &__btn {
    color: var(--c-danger);
    background: var(--c-danger-bg);
    border-color: rgba(229, 72, 77, 0.24);
  }
}
</style>
