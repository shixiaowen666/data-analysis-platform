<template>
  <bottom-sheet v-model="visible" title="查询 SQL" subtitle="点击右上角可复制" height="72vh">
    <template #trail>
      <span class="sqls__copy u-tap" @click="copy">复制</span>
    </template>
    <pre class="sqls__code u-no-scrollbar"><code v-html="highlighted"></code></pre>
  </bottom-sheet>
</template>

<script>
import BottomSheet from '@/components/web/BottomSheet.vue';
import { formatSQL } from '@/utils/sqlFormat';
import { highlightSQL } from '@/utils/sqlHighlight';
import { copyToClipboard } from "@/utils/clipboard.js";
import toast from '@/utils/toast';

export default {
  name: 'SqlSheet',
  components: { BottomSheet },
  props: {
    value: { type: Boolean, default: false },
    sql: { type: String, default: '' },
  },
  computed: {
    visible: {
      get() { return this.value; },
      set(v) { this.$emit('input', v); },
    },
    formatted() { return formatSQL(this.sql); },
    highlighted() { return highlightSQL(this.formatted); },
  },
  methods: {
    async copy() {
      try {
        await copyToClipboard(this.formatted);
        toast.success('SQL 已复制');
      } catch (e) {
        toast.error('复制失败');
      }
    },
  },
};
</script>

<style scoped lang="scss">
.sqls {
  &__copy {
    display: inline-flex;
    align-items: center;
    height: 40px;
    padding: 0 4px;
    font-size: var(--fs-14);
    font-weight: 600;
    color: var(--c-brand-500);
  }

  &__code {
    margin: 0;
    padding: 13px;
    border-radius: var(--r-sm);
    background: #fff;
    border: 1px solid var(--bd-light);
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    font-family: var(--ff-num);
    font-size: var(--fs-12);
    line-height: 1.85;
    color: var(--c-ink-700);
    white-space: pre;

    ::v-deep .sqlv-kw { color: #7c5cf0; font-weight: 600; }
    ::v-deep .sqlv-str { color: #10ab7f; }
    ::v-deep .sqlv-num { color: #e79217; }
  }
}
</style>
