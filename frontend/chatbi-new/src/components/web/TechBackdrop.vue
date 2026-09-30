<template>
  <div class="backdrop" aria-hidden="true">
    <div class="backdrop__grid"></div>
    <div class="backdrop__blob backdrop__blob--a"></div>
    <div class="backdrop__blob backdrop__blob--b"></div>
    <div class="backdrop__blob backdrop__blob--c"></div>
    <div class="backdrop__sweep"></div>
  </div>
</template>

<script>
/**
 * 全局科技感背景：浅蓝渐变 + 点阵网格(≤4%) + 缓慢漂移光斑 + 高光横扫
 * 只使用 transform / opacity 动画，GPU 友好
 */
export default { name: 'TechBackdrop' };
</script>

<style scoped lang="scss">
.backdrop {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: var(--g-page);

  &__grid {
    position: absolute;
    inset: -10%;
    background-image: radial-gradient(rgba(46, 107, 230, 0.075) 1px, transparent 1px);
    background-size: 22px 22px;
    opacity: 0.62;
    -webkit-mask-image: radial-gradient(ellipse at 50% 30%, #000 0%, transparent 78%);
    mask-image: radial-gradient(ellipse at 50% 30%, #000 0%, transparent 78%);
  }

  &__blob {
    position: absolute;
    border-radius: 50%;
    filter: blur(46px);
    will-change: transform;

    &--a {
      width: 62vw;
      height: 62vw;
      left: -20vw;
      top: -14vh;
      background: radial-gradient(circle, rgba(46, 107, 230, 0.15) 0%, transparent 68%);
      animation: fxDriftA 16s var(--ease-inout) infinite;
    }

    &--b {
      width: 58vw;
      height: 58vw;
      right: -18vw;
      bottom: 8vh;
      background: radial-gradient(circle, rgba(34, 184, 207, 0.14) 0%, transparent 68%);
      animation: fxDriftB 19s var(--ease-inout) infinite;
    }

    &--c {
      width: 46vw;
      height: 46vw;
      right: 6vw;
      top: 26vh;
      background: radial-gradient(circle, rgba(124, 92, 240, 0.085) 0%, transparent 70%);
      animation: fxDriftA 22s var(--ease-inout) infinite reverse;
    }
  }

  &__sweep {
    position: absolute;
    top: 0;
    left: 0;
    width: 34%;
    height: 100%;
    background: linear-gradient(
      100deg,
      transparent 0%,
      rgba(255, 255, 255, 0.5) 48%,
      transparent 100%
    );
    animation: fxSweep 18s var(--ease-inout) infinite;
    will-change: transform, opacity;
  }
}
</style>
