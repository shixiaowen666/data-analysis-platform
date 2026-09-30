<template>
  <section class="stepcard" :class="[`is-${step.stepType}`, `is-${step.status}`, { 'is-open': step.open }]">
    <!-- 运行中扫描线 -->
    <span v-if="step.status === 'running'" class="stepcard__scan"></span>

    <!-- 头部 -->
    <header class="stepcard__head" @click="toggle">
      <span class="stepcard__badge" :class="{ 'is-pulse': justDone }">
        <app-icon v-if="step.stepType === 'think'" name="brain" :size="13" :stroke-width="2" />
        <template v-else>{{ step.itemId }}</template>
      </span>

      <span class="stepcard__type">{{ typeLabel }}</span>

      <span class="stepcard__title u-clamp-2">{{ step.title || placeholderTitle }}</span>

      <span class="stepcard__tail">
        <typing-dots v-if="step.status === 'running'" :size="3.5" />
        <app-icon
          v-else
          name="chevron-down"
          :size="16"
          class="stepcard__caret"
          :class="{ 'is-up': step.open }"
        />
      </span>
    </header>

    <!-- 内容 -->
    <transition name="stepbody" @enter="onEnter" @after-enter="onAfterEnter" @leave="onLeave">
      <div v-if="step.open" class="stepcard__body">
        <div class="stepcard__bodyinner">
          <slot />
        </div>
      </div>
    </transition>
  </section>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
import TypingDots from '@/components/web/TypingDots.vue';

const TYPE_LABEL = {
  think: '思考',
  query: '查询',
  compute: '计算',
  analyze: '分析',
  summarize: '总结',
};

const TYPE_PLACEHOLDER = {
  think: '正在制定执行计划…',
  query: '正在准备取数…',
  compute: '正在计算…',
  analyze: '正在分析…',
  summarize: '正在归纳结论…',
};

/**
 * 步骤卡通用外壳
 * 改进点：
 *  - 头部 ≥ 56px 大触控区
 *  - 标题两行截断（不再定宽 65% nowrap）
 *  - 各卡独立展开（不再强行关闭其他卡）
 *  - 运行中顶部扫描线 + 完成脉冲环
 */
export default {
  name: 'StepCard',
  components: { AppIcon, TypingDots },
  props: {
    step: { type: Object, required: true },
  },
  data() {
    return { justDone: false };
  },
  computed: {
    typeLabel() {
      return TYPE_LABEL[this.step.stepType] || '步骤';
    },
    placeholderTitle() {
      return TYPE_PLACEHOLDER[this.step.stepType] || '处理中…';
    },
  },
  watch: {
    'step.status'(v, old) {
      if (v === 'done' && old === 'running') {
        this.justDone = true;
        setTimeout(() => (this.justDone = false), 700);
      }
    },
  },
  methods: {
    toggle() {
      this.$emit('toggle', !this.step.open);
    },
    onEnter(el) {
      el.style.height = '0px';
      void el.offsetHeight;
      el.style.height = el.scrollHeight + 'px';
    },
    onAfterEnter(el) {
      el.style.height = '';
    },
    onLeave(el) {
      el.style.height = el.scrollHeight + 'px';
      void el.offsetHeight;
      el.style.height = '0px';
    },
  },
};
</script>

<style scoped lang="scss">
.stepcard {
  position: relative;
  border-radius: var(--r-md);
  background: var(--g-card);
  border: 1px solid var(--bd-light);
  box-shadow: var(--sh-2);
  overflow: hidden;
  transition: border-color var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);

  &.is-open {
    border-color: var(--c-brand-100);
    box-shadow: var(--sh-3);
  }

  &.is-error {
    border-color: rgba(229, 72, 77, 0.28);
  }

  /* ---------- 扫描线 ---------- */
  &__scan {
    position: absolute;
    top: 0;
    left: 0;
    width: 34%;
    height: 2px;
    border-radius: 2px;
    background: linear-gradient(
      90deg,
      rgba(46, 107, 230, 0) 0%,
      rgba(46, 107, 230, 0.8) 46%,
      rgba(34, 184, 207, 0) 100%
    );
    animation: fxScan 1.5s var(--ease-inout) infinite;
    z-index: 2;
    pointer-events: none;
  }

  /* ---------- 头部 ---------- */
  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 56px;
    padding: 10px 12px;
    cursor: pointer;
    user-select: none;
    transition: background var(--dur-fast) var(--ease-out);

    &:active {
      background: rgba(46, 107, 230, 0.04);
    }
  }

  &__badge {
    position: relative;
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: var(--ff-num);
    font-size: var(--fs-12);
    font-weight: 700;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 2px 7px rgba(46, 107, 230, 0.24);

    &.is-pulse::after {
      content: '';
      position: absolute;
      inset: -2px;
      border-radius: 10px;
      border: 2px solid var(--c-brand-400);
      animation: fxPulseRing 0.7s var(--ease-out);
    }
  }

  &__type {
    flex-shrink: 0;
    height: 21px;
    padding: 0 9px;
    border-radius: var(--r-full);
    font-size: var(--fs-11);
    font-weight: 600;
    line-height: 21px;
    background: var(--c-brand-50);
    color: var(--c-brand-600);
    border: 1px solid var(--c-brand-100);
  }

  &__title {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-13);
    line-height: 1.5;
    color: var(--c-ink-700);
  }

  &__tail {
    flex-shrink: 0;
    width: 22px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__caret {
    color: var(--c-ink-300);
    transition: transform var(--dur-base) var(--ease-out);
    &.is-up {
      transform: rotate(180deg);
      color: var(--c-brand-400);
    }
  }

  /* ---------- 内容 ---------- */
  &__body {
    overflow: hidden;
    transition: height var(--dur-base) var(--ease-out);
  }

  &__bodyinner {
    padding: 0 12px 12px;
    border-top: 1px solid var(--bd-light);
    padding-top: 12px;
  }

  /* ---------- 按类型着色 ---------- */
  &.is-think &__badge {
    background: linear-gradient(135deg, #4f8cf5, #2158cc);
  }
  &.is-think &__type {
    background: rgba(46, 107, 230, 0.09);
    color: var(--c-brand-600);
    border-color: rgba(46, 107, 230, 0.16);
  }

  &.is-compute &__badge {
    background: linear-gradient(135deg, #22b8cf, #1596ab);
    box-shadow: 0 2px 7px rgba(34, 184, 207, 0.24);
  }
  &.is-compute &__type {
    background: var(--c-cyan-50);
    color: var(--c-cyan-600);
    border-color: var(--c-cyan-100);
  }

  &.is-analyze &__badge {
    background: var(--g-violet);
    box-shadow: 0 2px 7px rgba(124, 92, 240, 0.24);
  }
  &.is-analyze &__type {
    background: var(--c-violet-50);
    color: var(--c-violet-600);
    border-color: var(--c-violet-100);
  }

  &.is-summarize {
    background: linear-gradient(150deg, rgba(240, 253, 249, 0.9), rgba(248, 252, 255, 0.92));
    border-color: rgba(16, 171, 127, 0.16);
  }
  &.is-summarize &__badge {
    background: var(--g-success);
    box-shadow: 0 2px 7px rgba(16, 171, 127, 0.24);
  }
  &.is-summarize &__type {
    background: var(--c-success-bg);
    color: var(--c-success);
    border-color: rgba(16, 171, 127, 0.2);
  }
  &.is-summarize &__title {
    color: var(--c-ink-800);
    font-weight: 500;
  }

  /* 折叠动画 */
  .stepbody-enter-active,
  .stepbody-leave-active {
    transition: height var(--dur-base) var(--ease-out), opacity var(--dur-fast) var(--ease-out);
  }
  .stepbody-enter,
  .stepbody-leave-to {
    opacity: 0;
  }
}
</style>
