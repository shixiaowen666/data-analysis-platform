<template>
  <div ref="scroller" class="think u-scroll-y u-no-scrollbar c-fade-mask">
    <div class="think__inner md-timeline">
      <!--<template v-for="b in blocks">
        <div v-if="b.type === 'step'" :key="b.id" class="md-step">
          <span class="md-step__no">{{ b.no }}</span>
          <div class="md-step__body md-body" v-html="b.html"></div>
        </div>
        <div v-else :key="b.id" class="md-plain md-body" v-html="b.html"></div>
      </template>-->
      <div style="white-space: break-spaces;">{{content}}
        
      </div>

      <div v-if="streaming" class="think__typing">
        <typing-dots :size="4" />
        <span>推理中…</span>
      </div>

      <div v-if="!blocks.length && !streaming" class="think__empty">暂无思考过程</div>
    </div>
  </div>
</template>

<script>
import { parseThinkBlocks } from '@/utils/markdown';
import TypingDots from '@/components/web/TypingDots.vue';

export default {
  name: 'StepThink',
  components: { TypingDots },
  props: {
    content: { type: String, default: '' },
    streaming: { type: Boolean, default: false },
  },
  computed: {
    blocks() {
      return parseThinkBlocks(this.content);
    },
  },
  watch: {
    content() {
      if (this.streaming) this.$nextTick(this.scrollBottom);
    },
  },
  methods: {
    scrollBottom() {
      const el = this.$refs.scroller;
      if (el) el.scrollTop = el.scrollHeight;
    },
  },
};
</script>

<style scoped lang="scss">
.think {
  max-height: 42vh;
  border-radius: var(--r-sm);
  background: linear-gradient(150deg, rgba(248, 251, 255, 0.9), rgba(240, 249, 255, 0.86));
  border: 1px solid rgba(46, 107, 230, 0.09);

  &__inner {
    padding: 12px 12px 10px;
  }

  &__typing {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 6px 0 2px;
    font-size: var(--fs-11);
    color: var(--c-brand-500);
  }

  &__empty {
    font-size: var(--fs-12);
    color: var(--c-ink-300);
    text-align: center;
    padding: 12px 0;
  }
}
</style>
