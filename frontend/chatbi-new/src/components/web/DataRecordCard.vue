<template>
  <div class="rcard">
    <div class="rcard__head">
      <span class="rcard__no">#{{ index }}</span>
      <span v-if="title" class="rcard__title u-ellipsis">{{ title }}</span>
    </div>

    <div class="rcard__kvs">
      <div
        v-for="(col, ci) in columns"
        :key="ci"
        class="rcard__kv"
        :class="{ 'is-hit': hitKeys.includes(col.key || col.name) }"
      >
        <span class="rcard__k">
          <expandable-text :text="colLabel(col)" />
        </span>
        <span class="rcard__v" :class="{ 'is-num': isNum(cellOf(col)) }">
          {{ formatCell(cellOf(col)) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import ExpandableText from './ExpandableText.vue';

/**
 * 单条数据记录卡
 * 改进点：
 *  1) 字段名允许换行/点击展开，不再定宽 100px 截断
 *  2) 数值等宽字体 + 千分位，右对齐易于比较
 *  3) 命中筛选字段整行浅蓝高亮（替代重阴影 + 旋转水印）
 */
export default {
  name: 'DataRecordCard',
  components: { ExpandableText },
  props: {
    record: { type: Object, default: () => ({}) },
    columns: { type: Array, default: () => [] },
    index: { type: [Number, String], default: 1 },
    title: { type: String, default: '' },
    /** 高亮的列 key 集合 */
    hitKeys: { type: Array, default: () => [] },
    /** 取值方式：key（默认，按 column.key）| name（按 column.name） */
    valueBy: { type: String, default: 'key' },
  },
  methods: {
    colLabel(col) {
      return col.unit ? `${col.cn_name || col.name}（${col.unit}）` : col.cn_name || col.name;
    },
    cellOf(col) {
      const k = this.valueBy === 'name' ? col.name : col.key || col.name;
      const v = this.record ? this.record[k] : undefined;
      return v === undefined && col.name ? this.record[col.name] : v;
    },
    isNum(v) {
      return v !== null && v !== '' && v !== undefined && !isNaN(Number(v));
    },
    formatCell(v) {
      if (v === null || v === undefined || v === '') return '—';
      if (this.isNum(v)) {
        const n = Number(v);
        if (Number.isInteger(n)) return n.toLocaleString('en-US');
        return n.toLocaleString('en-US', { maximumFractionDigits: 2 });
      }
      return String(v);
    },
  },
};
</script>

<style scoped lang="scss">
.rcard {
  background: var(--g-card);
  border: 1px solid var(--bd-light);
  border-radius: var(--r-md);
  box-shadow: var(--sh-2);
  overflow: hidden;

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 12px;
    background: linear-gradient(90deg, rgba(46, 107, 230, 0.055), rgba(34, 184, 207, 0.03));
    border-bottom: 1px solid var(--bd-light);
  }

  &__no {
    flex-shrink: 0;
    font-family: var(--ff-num);
    font-size: var(--fs-11);
    font-weight: 600;
    color: var(--c-brand-600);
    background: rgba(255, 255, 255, 0.86);
    border: 1px solid var(--c-brand-100);
    border-radius: var(--r-full);
    padding: 2px 8px;
    line-height: 1.4;
  }

  &__title {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-11);
    color: var(--c-ink-400);
  }

  &__kvs {
    padding: 4px 0;
  }

  &__kv {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 7px 12px;
    transition: background var(--dur-fast) var(--ease-out);

    & + & {
      border-top: 1px dashed var(--bd-light);
    }

    &.is-hit {
      background: linear-gradient(90deg, rgba(46, 107, 230, 0.09), rgba(34, 184, 207, 0.05));
      .rcard__k,
      .rcard__v {
        color: var(--c-brand-700);
        font-weight: 600;
      }
    }
  }

  &__k {
    flex: 0 1 42%;
    max-width: 42%;
    min-width: 0;
    font-size: var(--fs-12);
    color: var(--c-ink-500);
    line-height: 1.5;
  }

  &__v {
    flex: 1;
    min-width: 0;
    text-align: right;
    font-size: var(--fs-13);
    color: var(--c-ink-800);
    line-height: 1.5;
    word-break: break-word;

    &.is-num {
      font-family: var(--ff-num);
      font-variant-numeric: tabular-nums;
      font-weight: 600;
      color: var(--c-ink-900);
    }
  }
}
</style>
