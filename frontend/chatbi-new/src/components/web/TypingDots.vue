<template>
  <span class="tdots" :style="{ '--dot': size + 'px' }">
    <i></i><i></i><i></i>
  </span>
</template>

<script>
export default {
  name: 'TypingDots',
  props: {
    size: { type: Number, default: 4 },
  },
};
</script>

<style scoped lang="scss">
.tdots {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  vertical-align: middle;

  i {
    width: var(--dot);
    height: var(--dot);
    border-radius: 50%;
    background: var(--c-brand-400);
    animation: fxTyping 1.15s var(--ease-inout) infinite;

    &:nth-child(2) {
      animation-delay: 0.16s;
      background: var(--c-brand-500);
    }
    &:nth-child(3) {
      animation-delay: 0.32s;
      background: var(--c-cyan-500);
    }
  }
}
</style>
