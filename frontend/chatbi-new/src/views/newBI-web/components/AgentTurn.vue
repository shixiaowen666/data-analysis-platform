<template>
  <div class="aturn">
    <div class="aturn__head fx-fade-in">
      <span class="aturn__logo"><app-icon name="bot" :size="13" /></span>
      <span class="aturn__name u-ellipsis">{{ agentName }}</span>
      <span v-if="turn.status === 'stopped'" class="aturn__flag">已停止</span>
    </div>

    <div class="aturn__timeline">
      <step-card
        v-if="turn.think"
        :step="thinkStep"
        @toggle="(v) => onToggleThink(v, turn, `think${turn.chatId}`)"
        :ref="`think${turn.chatId}`"
      >
        <step-think :content="turn.think.text" :streaming="!turn.think.done" />
      </step-card>

      <step-card
        v-for="s in turn.steps"
        :key="s.uid"
        :step="s"
        @toggle="(v) => onToggle(s, v, `${turn.chatId}${s.itemId}`)"
        :ref="`${turn.chatId}${s.itemId}`"
      >
        <step-query
          v-if="s.stepType === 'query'"
          :step="s"
          v-on="queryListeners(s)"
        />
        <step-compute v-else-if="s.stepType === 'compute'" :step="s" @page="(p) => $emit('page', s, p)" />
        <step-analyze
          v-else-if="s.stepType === 'analyze'"
          :content="s.answer"
          :streaming="s.status === 'running'"
          variant="analyze"
        />
        
        <step-analyze
          v-else-if="s.stepType === 'summarize'"
          :content="s.answer"
          :streaming="s.status === 'running'"
          variant="summary"
        />
      </step-card>
    </div>
  </div>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
import StepCard from './StepCard.vue';
import StepThink from './StepThink.vue';
import StepQuery from './StepQuery.vue';
import StepCompute from './StepCompute.vue';
import StepAnalyze from './StepAnalyze.vue';

export default {
  name: 'AgentTurn',
  components: { AppIcon, StepCard, StepThink, StepQuery, StepCompute, StepAnalyze },
  props: {
    turn: { type: Object, required: true },
    agentName: { type: String, default: '智能体' },
  },
  computed: {
    thinkStep() {
      const t = this.turn.think || {};
      return {
        uid: 'think-' + this.turn.uid,
        stepType: 'think',
        itemId: 0,
        title: t.title || '正在制定执行计划…',
        status: t.done ? 'done' : 'running',
        open: !!t.open,
      };
    },
  },
  methods: {
    onToggleThink(v, turn, name) {
      turn.think.open = v
      if (v) {
        this.scrollToCollapseTop(name);
      }
    },

    onToggle(s, v, name) {
      s.open = v;
      
      if (v) {
        this.scrollToCollapseTop(name);
      }
    },

    //分析和总结，展开到最上边
    scrollToCollapseTop(name) {
      // 当面板展开时触发（activeId 为当前展开的 name）
      if (name) {
        this.$nextTick(() => {
          setTimeout(() => {
            const panel = this.$refs[name];
            if (panel) {
              const el = panel[0]?.$el || panel.$el;
              el.scrollIntoView({ behavior: "smooth", block: "start" }); // 滚动到中间位置更舒适
            }
          }, 300);
        });
      }
    },

    queryListeners(s) {
      const self = this;
      return {
        'view-sql': () => self.$emit('view-sql', s),
        apply: (p) => self.$emit('apply', s, p),
        page: (p) => self.$emit('page', s, p),
        'toggle-toolbar': (v) => { s.toolbarOpen = v; },
        'toggle-highlight': (k) => self.$emit('toggle-highlight', s, k),
        'load-candidates': (t) => self.$emit('load-candidates', s, t),
        'add-dim': (v) => self.$emit('add-dim', s, v),
        'add-metric': (v) => self.$emit('add-metric', s, v),
        'remove-dim': (d) => self.$emit('remove-dim', s, d),
        'remove-metric': (m) => self.$emit('remove-metric', s, m),
        'add-filters': (v) => self.$emit('add-filters', s, v),
        'remove-filter': (i) => self.$emit('remove-filter', s, i),
        'load-filter-values': (f) => self.$emit('load-filter-values', s, f),
        'set-granularity': (g) => self.$emit('set-granularity', s, g),
        'set-range': (r) => self.$emit('set-range', s, r),
        reset: () => self.$emit('reset-query', s),
        dirty: () => { s.dirty = true; },
      };
    },
  },
};
</script>

<style scoped lang="scss">
.aturn {
  &__head {
    display: flex;
    align-items: center;
    gap: 7px;
    margin-bottom: 9px;
  }

  &__logo {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 2px 7px rgba(46, 107, 230, 0.2);
  }

  &__name {
    min-width: 0;
    font-size: var(--fs-12);
    font-weight: 600;
    color: var(--c-ink-600);
  }

  &__flag {
    flex-shrink: 0;
    font-size: 10px;
    color: var(--c-ink-400);
    background: var(--bg-sunk);
    border-radius: var(--r-full);
    padding: 1px 7px;
  }

  &__timeline {
    position: relative;
    padding-left: 11px;
    display: flex;
    flex-direction: column;
    gap: 10px;

    &::before {
      content: '';
      position: absolute;
      left: 11px;
      top: 6px;
      bottom: 6px;
      width: 2px;
      border-radius: 2px;
      background: linear-gradient(180deg, var(--c-brand-200), rgba(226, 236, 253, 0.2));
    }
  }
}
</style>
