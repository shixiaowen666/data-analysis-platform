<template>
  <transition name="fx-collapse">
    <div v-if="visible" class="tind">
      <tech-loader :size="20" inline />
      <div class="tind__body">
        <span class="tind__label u-ellipsis">{{ label }}</span>
        <span class="tind__time u-num">{{ elapsedText }}</span>
      </div>
      <button v-if="canStop" class="tind__stop" @click="$emit('stop')">
        <app-icon name="stop" :size="11" />停止
      </button>
    </div>
  </transition>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
import TechLoader from '@/components/web/TechLoader.vue';

export default {
  name: 'ThinkingIndicator',
  components: { AppIcon, TechLoader },
  props: {
    visible: { type: Boolean, default: false },
    label: { type: String, default: '正在思考' },
    canStop: { type: Boolean, default: true },
  },
  data() {
    return { seconds: 0, timer: null };
  },
  computed: {
    elapsedText() {
      const m = Math.floor(this.seconds / 60);
      const s = this.seconds % 60;
      return m ? `${m}:${String(s).padStart(2, '0')}` : `${s}s`;
    },
  },
  watch: {
    visible(v) {
      if (v) this.start();
      else this.stopTimer();
    },
  },
  mounted() {
    if (this.visible) this.start();
  },
  beforeDestroy() {
    this.stopTimer();
  },
  methods: {
    start() {
      this.seconds = 0;
      this.stopTimer();
      this.timer = setInterval(() => (this.seconds += 1), 1000);
    },
    stopTimer() {
      if (this.timer) clearInterval(this.timer);
      this.timer = null;
    },
  },
};
</script>

<style scoped lang="scss">
.tind {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px 8px 12px;
  border-radius: var(--r-full);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: saturate(160%) blur(12px);
  -webkit-backdrop-filter: saturate(160%) blur(12px);
  border: 1px solid var(--c-brand-100);
  box-shadow: var(--sh-3);

  &__body {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: baseline;
    gap: 6px;
  }

  &__label {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-12);
    color: var(--c-ink-700);
    font-weight: 500;
  }

  &__time {
    flex-shrink: 0;
    font-size: var(--fs-11);
    color: var(--c-ink-400);
  }

  &__stop {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 28px;
    padding: 0 11px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-danger);
    background: var(--c-danger-bg);
    border: 1px solid rgba(229, 72, 77, 0.22);
    &:active {
      transform: scale(0.96);
    }
  }
}
</style>
