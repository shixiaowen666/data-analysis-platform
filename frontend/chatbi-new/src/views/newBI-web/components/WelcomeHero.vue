<template>
  <div class="welcome u-scroll-y u-no-scrollbar">
    <div class="welcome__inner">
      <!-- 品牌徽标 -->
      <div class="welcome__badge fx-scale-in">
        <span class="welcome__badge-orbit"></span>
        <span class="welcome__badge-core">
          <app-icon name="sparkles" :size="24" :stroke-width="1.6" />
        </span>
      </div>

      <h2 class="welcome__title fx-fade-up fx-d2">
        你好，欢迎使用
      </h2>
      <p class="welcome__agent fx-fade-up fx-d3">{{ agentName || '智能问数' }}</p>
      <p class="welcome__sub fx-fade-up fx-d4">
        用自然语言提问，我会为你完成
        <br/>
        <strong>取数 · 计算 · 分析 · 总结</strong>
      </p>

      <!-- 能力标签 -->
      <div class="welcome__caps fx-fade-up fx-d5">
        <span class="welcome__cap"><app-icon name="database" :size="12" />指标查询</span>
        <span class="welcome__cap"><app-icon name="trending-up" :size="12" />趋势分析</span>
        <span class="welcome__cap"><app-icon name="lightbulb" :size="12" />归因总结</span>
      </div>

      <!-- ============ 推荐问 ============ -->
      <div class="welcome__section">
        <div class="welcome__sechead">
          <span class="welcome__sectitle">
            <app-icon name="zap" :size="13" />试试这样问
          </span>
          <div v-if="!loading" class="welcome__secops">
            <button v-if="canShuffle" class="welcome__op" @click="shuffle">
              <app-icon name="refresh" :size="12" :spin="spinning" />换一批
            </button>
            <button class="welcome__op welcome__op--more" @click="$emit('more')">
              更多<app-icon name="chevron-right" :size="12" />
            </button>
          </div>
        </div>

        <!-- 加载骨架 -->
        <div v-if="loading" class="welcome__qskel">
          <skeleton-block variant="block" line-height="112px" />
          <div class="welcome__qgrid">
            <skeleton-block variant="block" line-height="98px" />
            <skeleton-block variant="block" line-height="98px" />
          </div>
        </div>

        <div v-else-if="shown.length" :key="offset" class="welcome__qwrap">
          <!-- 主推大卡：渐变浅蓝底 + 标签 + 引导语，视觉权重最高 -->
          <button
            v-if="featured"
            class="welcome__hero fx-fade-up"
            @click="$emit('ask', featured.question)"
          >
            <span class="welcome__heroglow"></span>
            <span class="welcome__herotop">
              <span class="welcome__hotpill">
                <app-icon name="sparkles" :size="10" :stroke-width="2.2" />推荐
              </span>
              <span v-if="tagName(featured)" class="welcome__herotag">{{ tagName(featured) }}</span>
            </span>
            <span class="welcome__herotext u-clamp-3">{{ featured.question }}</span>
            <span class="welcome__herocta">
              立即分析
              <app-icon name="arrow-right" :size="13" />
            </span>
          </button>

          <!-- 次级卡：双列网格，配色按类型区分，尺寸与主卡形成对比 -->
          <div v-if="rest.length" class="welcome__qgrid">
            <button
              v-for="(q, i) in rest"
              :key="q.id || i"
              class="welcome__qmini fx-fade-up"
              :class="'is-' + accent(i)"
              :style="{ animationDelay: 90 + i * 70 + 'ms' }"
              @click="$emit('ask', q.question)"
            >
              <span class="welcome__minihead">
                <span class="welcome__miniicon">
                  <app-icon :name="tagIcon(q)" :size="12" :stroke-width="2" />
                </span>
                <span class="welcome__minitag u-ellipsis">{{ tagName(q) || '数据分析' }}</span>
              </span>
              <span class="welcome__minitext u-clamp-3">{{ q.question }}</span>
              <span class="welcome__miniarrow">
                <app-icon name="arrow-right" :size="12" :stroke-width="2.2" />
              </span>
            </button>
          </div>
        </div>

        <state-block
          v-else
          type="empty"
          title="暂无推荐问题"
          desc="直接在下方输入框提出你的问题"
        />
      </div>

      <div class="welcome__hint fx-fade-up fx-d8">
        <app-icon name="arrow-down" :size="13" class="welcome__hintarrow" />
        也可以在下方直接输入你的问题
      </div>
    </div>
  </div>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
import SkeletonBlock from '@/components/web/SkeletonBlock.vue';
import StateBlock from '@/components/web/StateBlock.vue';

/* 标签 → 图标映射，让每张卡的视觉锚点不同 */
const TAG_ICON = {
  趋势分析: 'trending-up',
  对比分析: 'columns',
  异常归因: 'target',
  经营指标: 'gauge',
};
const ACCENTS = ['cyan', 'violet'];
const PAGE = 3;

export default {
  name: 'WelcomeHero',
  components: { AppIcon, SkeletonBlock, StateBlock },
  props: {
    agentName: { type: String, default: '' },
    /** 完整推荐问池，组件内部按 3 条一屏轮换 */
    questions: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
  },
  data() {
    return { offset: 0, spinning: false };
  },
  computed: {
    shown() {
      const list = this.questions;
      if (list.length <= PAGE) return list;
      // 环形取 3 条，保证「换一批」不会出现空位
      return Array.from({ length: PAGE }, (_, i) => list[(this.offset + i) % list.length]);
    },
    featured() {
      return this.shown[0] || null;
    },
    rest() {
      return this.shown.slice(1, PAGE);
    },
    canShuffle() {
      return this.questions.length > PAGE;
    },
  },
  watch: {
    // 智能体切换后推荐问重载，重置轮换游标
    questions() {
      this.offset = 0;
    },
  },
  methods: {
    tagName(q) {
      const t = (q && q.tags) || [];
      const first = t[0];
      if (!first) return '';
      return typeof first === 'string' ? first : first.name || '';
    },
    tagIcon(q) {
      return TAG_ICON[this.tagName(q)] || 'sparkles';
    },
    accent(i) {
      return ACCENTS[i % ACCENTS.length];
    },
    shuffle() {
      if (!this.canShuffle) return;
      this.offset = (this.offset + PAGE) % this.questions.length;
      this.spinning = true;
      setTimeout(() => (this.spinning = false), 520);
    },
  },
};
</script>

<style scoped lang="scss">
.welcome {
  height: 100%;

  &__inner {
    min-height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 24px 18px 20px;
    text-align: center;
  }

  /* ---------- 徽标 ---------- */
  &__badge {
    position: relative;
    width: 62px;
    height: 62px;
    margin-bottom: 16px;
  }

  &__badge-core {
    position: absolute;
    inset: 6px;
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 8px 22px rgba(46, 107, 230, 0.26);
    animation: fxBreath 2.4s var(--ease-inout) infinite;
  }

  &__badge-orbit {
    position: absolute;
    inset: 0;
    border-radius: 50%;
    border: 1px dashed rgba(46, 107, 230, 0.3);
    animation: fxSpin 16s linear infinite;

    &::before {
      content: '';
      position: absolute;
      top: -3px;
      left: 50%;
      width: 5px;
      height: 5px;
      margin-left: -2.5px;
      border-radius: 50%;
      background: var(--c-cyan-500);
      box-shadow: 0 0 8px rgba(34, 184, 207, 0.55);
    }
  }

  /* ---------- 文案 ---------- */
  &__title {
    margin: 0;
    font-size: var(--fs-20);
    font-weight: 600;
    color: var(--c-ink-800);
  }

  &__agent {
    margin: 4px 0 0;
    font-size: var(--fs-24);
    font-weight: 700;
    letter-spacing: 0.02em;
    background: linear-gradient(135deg, #2158cc, #22b8cf);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  &__sub {
    margin: 10px 0 0;
    font-size: var(--fs-13);
    color: var(--c-ink-500);
    line-height: 1.7;
    max-width: 280px;

    strong {
      color: var(--c-brand-600);
      font-weight: 600;
    }
  }

  &__caps {
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 16px;
  }

  &__cap {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 27px;
    padding: 0 12px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-brand-600);
    background: rgba(255, 255, 255, 0.86);
    border: 1px solid var(--c-brand-100);
    box-shadow: var(--sh-1);
  }

  /* ---------- 推荐问：区块头 ---------- */
  &__section {
    width: 100%;
    margin-top: 26px;
    text-align: left;
  }

  &__sechead {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  &__sectitle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: var(--fs-12);
    font-weight: 600;
    color: var(--c-ink-500);
    letter-spacing: 0.04em;

    .app-icon {
      color: var(--c-cyan-500);
    }
  }

  &__secops {
    display: flex;
    align-items: center;
    gap: 2px;
  }

  &__op {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    min-height: 32px;
    padding: 0 8px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-ink-500);
    transition: all var(--dur-fast) var(--ease-out);

    &:active {
      background: var(--c-brand-50);
      color: var(--c-brand-600);
    }

    &--more {
      color: var(--c-brand-500);
      padding-right: 2px;
    }
  }

  /* ---------- 骨架 ---------- */
  &__qskel {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__qwrap {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  /* ---------- 主推大卡 ---------- */
  &__hero {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 9px;
    width: 100%;
    padding: 14px 15px 13px;
    text-align: left;
    overflow: hidden;
    border-radius: var(--r-lg);
    background: var(--g-brand-soft);
    border: 1px solid var(--c-brand-200);
    box-shadow: var(--sh-2);
    transition:
      transform var(--dur-fast) var(--ease-out),
      box-shadow var(--dur-fast) var(--ease-out);

    &:active {
      transform: scale(0.99);
      box-shadow: var(--sh-3);
    }
  }

  /* 右上角柔光装饰弧，替代生硬的左侧竖条 */
  &__heroglow {
    position: absolute;
    top: -46px;
    right: -34px;
    width: 132px;
    height: 132px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(34, 184, 207, 0.2) 0%, rgba(34, 184, 207, 0) 68%);
    pointer-events: none;
  }

  &__herotop {
    position: relative;
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__hotpill {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    height: 20px;
    padding: 0 8px;
    border-radius: var(--r-full);
    font-size: 10px;
    font-weight: 600;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 2px 7px rgba(46, 107, 230, 0.24);
  }

  &__herotag {
    display: inline-flex;
    align-items: center;
    height: 20px;
    padding: 0 8px;
    border-radius: var(--r-full);
    font-size: 10px;
    color: var(--c-brand-600);
    background: rgba(255, 255, 255, 0.8);
    border: 1px solid var(--c-brand-100);
  }

  &__herotext {
    position: relative;
    font-size: var(--fs-14);
    font-weight: 600;
    line-height: 1.55;
    color: var(--c-ink-800);
  }

  &__herocta {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: var(--fs-12);
    font-weight: 600;
    color: var(--c-brand-500);

    .app-icon {
      transition: transform var(--dur-base) var(--ease-out);
    }
  }

  &__hero:active &__herocta .app-icon {
    transform: translateX(3px);
  }

  /* ---------- 次级双列卡 ---------- */
  &__qgrid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  &__qmini {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 7px;
    min-height: 98px;
    padding: 11px 11px 26px;
    text-align: left;
    border-radius: var(--r-md);
    background: rgba(255, 255, 255, 0.92);
    border: 1px solid var(--bd-light);
    box-shadow: var(--sh-1);
    transition: all var(--dur-fast) var(--ease-out);

    &:active {
      transform: scale(0.985);
      box-shadow: var(--sh-2);
    }

    /* 顶部一段短彩条：不同色系区分卡片，避免三张卡雷同 */
    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 11px;
      width: 22px;
      height: 2px;
      border-radius: 0 0 2px 2px;
    }

    &.is-cyan {
      &::before {
        background: linear-gradient(90deg, #22b8cf, #7fdcea);
      }
      .welcome__miniicon {
        color: var(--c-cyan-600);
        background: var(--c-cyan-50);
        border-color: var(--c-cyan-100);
      }
      &:active {
        border-color: var(--c-cyan-300);
      }
    }

    &.is-violet {
      &::before {
        background: linear-gradient(90deg, #7c5cf0, #a794f7);
      }
      .welcome__miniicon {
        color: var(--c-violet-600);
        background: var(--c-violet-50);
        border-color: var(--c-violet-100);
      }
      &:active {
        border-color: #c3b5fb;
      }
    }
  }

  &__minihead {
    display: flex;
    align-items: center;
    gap: 5px;
    max-width: 100%;
  }

  &__miniicon {
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    border-radius: 7px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid transparent;
  }

  &__minitag {
    min-width: 0;
    font-size: 10px;
    font-weight: 600;
    color: var(--c-ink-400);
    letter-spacing: 0.02em;
  }

  &__minitext {
    font-size: var(--fs-12);
    line-height: 1.6;
    color: var(--c-ink-700);
  }

  &__miniarrow {
    position: absolute;
    right: 9px;
    bottom: 8px;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--c-brand-400);
    background: var(--c-brand-50);
  }

  /* 窄屏（<340px）降级为单列，避免文字被挤成竖条 */
  @media (max-width: 339px) {
    &__qgrid {
      grid-template-columns: 1fr;
    }
    &__qmini {
      min-height: 0;
      padding-bottom: 11px;
    }
    &__miniarrow {
      display: none;
    }
  }

  /* ---------- 提示 ---------- */
  &__hint {
    margin-top: auto;
    padding-top: 22px;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: var(--fs-11);
    color: var(--c-ink-300);
  }

  &__hintarrow {
    animation: fxTyping 1.8s var(--ease-inout) infinite;
  }
}
</style>
