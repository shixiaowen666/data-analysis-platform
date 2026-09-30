<template>
  <div class="tech-loader" :class="{ 'is-inline': inline }">
    <div class="tech-loader__ring" :style="ringStyle">
      <i class="tech-loader__arc tech-loader__arc--a"></i>
      <i class="tech-loader__arc tech-loader__arc--b"></i>
      <b class="tech-loader__core"></b>
    </div>
    <div v-if="text" class="tech-loader__text">{{ text }}</div>
  </div>
</template>

<script>
/**
 * 科技感 Loader：双环反向轨道 + 中心脉冲光点
 * 动效克制：仅旋转与呼吸，无位移/缩放跳变
 */
export default {
  name: 'TechLoader',
  props: {
    size: { type: Number, default: 40 },
    text: { type: String, default: '' },
    inline: { type: Boolean, default: false },
  },
  computed: {
    ringStyle() {
      return { width: this.size + 'px', height: this.size + 'px' };
    },
  },
};
</script>

<style scoped lang="scss">
.tech-loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;

  &.is-inline {
    flex-direction: row;
    gap: 8px;
  }

  &__ring {
    position: relative;
    flex-shrink: 0;
  }

  &__arc {
    position: absolute;
    inset: 0;
    border-radius: 50%;
    border: 1.5px solid transparent;
    border-top-color: var(--c-brand-500);
    border-right-color: rgba(46, 107, 230, 0.24);
    animation: fxSpin 1.5s linear infinite;

    &--b {
      inset: 22%;
      border-top-color: var(--c-cyan-500);
      border-right-color: rgba(34, 184, 207, 0.22);
      animation: fxSpinRev 2.1s linear infinite;
    }
  }

  &__core {
    position: absolute;
    left: 50%;
    top: 50%;
    width: 13%;
    height: 13%;
    min-width: 5px;
    min-height: 5px;
    transform: translate(-50%, -50%);
    border-radius: 50%;
    background: var(--g-brand);
    animation: fxBreath 1.8s var(--ease-inout) infinite;
  }

  &__text {
    font-size: var(--fs-13);
    color: var(--c-ink-500);
    letter-spacing: 0.03em;
  }
}
</style>
