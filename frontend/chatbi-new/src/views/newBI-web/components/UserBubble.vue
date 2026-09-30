<template>
  <div class="ub fx-fade-up">
    <div class="ub__row">
      <div class="ub__bubble">{{ text }}</div>
      <span class="ub__avatar">{{ avatarText }}</span>
    </div>
    <div class="ub__meta">
      <span v-if="time" class="ub__time">{{ time }}</span>
      <button class="ub__act u-tap" @click="$emit('copy', text)">
        <app-icon name="copy" :size="12" />
      </button>
      <button class="ub__act u-tap" @click="$emit('resend', text)">
        <app-icon name="refresh" :size="12" />
      </button>
    </div>
  </div>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
export default {
  name: 'UserBubble',
  components: { AppIcon },
  props: {
    text: { type: String, default: '' },
    time: { type: String, default: '' },
    userName: { type: String, default: '' },
  },
  computed: {
    avatarText() {
      return String(this.userName || '用').trim().charAt(0).toUpperCase();
    },
  },
};
</script>

<style scoped lang="scss">
.ub {
  &__row {
    display: flex;
    align-items: flex-start;
    justify-content: flex-end;
    gap: 8px;
  }

  &__bubble {
    max-width: calc(100% - 44px);
    padding: 11px 14px;
    font-size: var(--fs-14);
    line-height: 1.62;
    color: var(--c-ink-800);
    word-break: break-word;
    white-space: pre-wrap;
    background: linear-gradient(140deg, #e8f0ff 0%, #e6f5fd 100%);
    border: 1px solid rgba(46, 107, 230, 0.14);
    border-radius: 14px 4px 14px 14px;
    box-shadow: 0 2px 8px rgba(46, 107, 230, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.85);
  }

  &__avatar {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--fs-12);
    font-weight: 600;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 2px 8px rgba(46, 107, 230, 0.2);
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 4px;
    margin: 5px 38px 0 0;
  }

  &__time {
    font-size: 10px;
    color: var(--c-ink-300);
    margin-right: 2px;
  }

  &__act {
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: var(--c-brand-500);
    &:active {
      background: var(--c-brand-50);
      color: var(--c-brand-500);
    }
  }
}
</style>
