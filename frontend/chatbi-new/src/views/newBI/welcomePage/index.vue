<template>
  <div
    class="my-custom-style"
  >
    <div style="height: 100%">
      <div class="welcome">
        <!--<div class="welcome-hero__badge">
          <base-icon name="sparkles" :size="26" :stroke-width="1.8" />
        </div>-->
    <div class="welcome__badge fx-scale-in">
        <span class="welcome__badge-orbit"></span>
        <span class="welcome__badge-core">
          <base-icon name="sparkles" :size="26" :stroke-width="1.8" />
        </span>
      </div>

 
        <div class="welcome__title">
          你好，欢迎使用
        </div>
        <div>
          <span class="welcome__agent">{{
            agentName
          }}</span>
        </div>

        <div class="welcome__sub fx-fade-up fx-d4">

                  用自然语言提问，我会为你完成
            <div style="text-align: center;">
        <strong>取数 · 计算 · 分析 · 总结</strong>
            </div>
        </div>

        <div class="welcome__caps">
          <span class="welcome__cap">
            <base-icon name="bar-chart" :size="13" :stroke-width="2" />
            指标查询
          </span>
          <span class="welcome__cap">
            <base-icon name="trending-up" :size="13" :stroke-width="2" />
            趋势分析
          </span>
          <span class="welcome__cap">
            <base-icon name="lightbulb" :size="13" :stroke-width="2" />
            归因总结
          </span>
        </div>
        <div
          style="
            display: flex;
            width: 80%;
            justify-content: space-around;
            margin-top: 20px;
          "
        >
          <!--<div
            class="homeRecommend"
            v-for="(q, index) in questions.slice(0, 3)"
            :key="index"
            style="width: 30%"
            @click.stop="$emit('send', q.question)"
          >
            <truncate-tip
              :text="q.question"
              style="
                overflow: hidden;
                -webkit-line-clamp: 2;
                text-overflow: ellipsis;
                display: -webkit-box;
                -webkit-box-orient: vertical;
                white-space: normal;
              "
            />
          </div>-->


        <div v-if="questions.length" class="welcome__qgrid">
            <button
              v-for="(q, i) in questions.slice(0, 4)"
              :key="q.id || i"
              class="welcome__qmini fx-fade-up"
              :class="'is-' + accent(i)"
              :style="{ animationDelay: 90 + i * 70 + 'ms' }"
              @click="$emit('send', q.question)"
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
      </div>
    </div>

  

    </div>
</template>


<script>
import TruncateTip from "@/components/TruncateTip";
const TAG_ICON = {
  趋势分析: 'trending-up',
  对比分析: 'columns',
  异常归因: 'target',
  经营指标: 'gauge',
};

const ACCENTS = ['cyan', 'violet'];

export default {
  name: 'welcomePage',
  components: { TruncateTip },
  props: {
    agentName: { type: String, default: '' },
    /** 完整推荐问池，组件内部按 3 条一屏轮换 */
    questions: { type: Array, default: () => [] },
  },
  data() {
    return { 

        };
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
  },
};


</script>

<style lang="scss">
@use "@/styles/web/tokens.scss" as *;
</style>


<style scoped lang="scss">
.welcome {
    display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  gap: 14px;
  padding: 0 24px;

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
    //margin-bottom: 16px;
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
    //max-width: 280px;

    strong {
      color: var(--c-brand-600);
      font-weight: 600;
    }
  }

  &__caps {
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    gap: 30px;
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
    flex: 1;
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