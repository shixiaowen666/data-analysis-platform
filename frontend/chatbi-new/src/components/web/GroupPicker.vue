<template>
  <bottom-sheet
    v-model="visible"
    :title="title"
    :subtitle="subtitleText"
    :height="height"
    :loading="loading"
    :cancel-text="mode === 'multiple' ? '取消' : ''"
    :confirm-text="mode === 'multiple' ? '确定' : ''"
    flat
    @cancel="$emit('cancel')"
    @confirm="emitConfirm"
    @closed="keyword = ''"
  >
    <template v-if="searchable" #toolbar>
      <search-bar v-model="keyword" :placeholder="searchPlaceholder" />
    </template>

    <!-- 顶部横向分类 Tab（替代 Vant 窄侧栏，中文标签更友好） -->
    <div v-if="!keyword && groups.length > 1" class="gpick__tabs u-no-scrollbar">
      <button
        v-for="(g, gi) in groups"
        :key="g._key"
        class="gpick__tab"
        :class="{ 'is-on': gi === activeGroup }"
        @click="activeGroup = gi"
      >
        {{ g.text }}
        <em v-if="(g.children || []).length">{{ (g.children || []).length }}</em>
      </button>
    </div>

    <!-- 全选（多选，仅当前分类） -->
    <div
      v-if="mode === 'multiple' && !keyword && current.length"
      class="gpick__all u-tap"
      @click="toggleAllCurrent"
    >
      <span class="gpick__all-left">
        <span class="gpick__box" :class="{ 'is-on': allCurrentSelected }">
          <app-icon v-if="allCurrentSelected" name="check" :size="12" :stroke-width="3" />
        </span>
        全选「{{ groups[activeGroup] ? groups[activeGroup].text : '' }}」
      </span>
      <span class="gpick__count">已选 {{ inner.length }}</span>
    </div>

    <!-- 列表 -->
    <div v-if="rows.length" class="gpick__list">
      <template v-for="(row, i) in rows">
        <div v-if="row._group" :key="'g' + i" class="gpick__grouphead">{{ row.text }}</div>
        <div
          v-else
          :key="'i' + row.id"
          class="gpick__row fx-fade-up"
          :class="{ 'is-on': inner.includes(row.id) }"
          :style="{ animationDelay: Math.min(i, 8) * 24 + 'ms' }"
          @click="pick(row)"
        >
          <span v-if="mode === 'multiple'" class="gpick__marker">
            <app-icon v-if="inner.includes(row.id)" name="check" :size="12" :stroke-width="3" />
          </span>
          <span class="gpick__body">
            <span class="gpick__label">{{ row.text }}</span>
            <span v-if="row.desc" class="gpick__desc u-ellipsis">{{ row.desc }}</span>
          </span>
          <app-icon
            v-if="mode === 'single'"
            name="chevron-right"
            :size="15"
            class="gpick__chev"
          />
        </div>
      </template>
    </div>

    <state-block
      v-else-if="!loading"
      :type="keyword ? 'search' : 'empty'"
      :title="keyword ? '未找到匹配项' : emptyText"
      :desc="keyword ? '换个关键词试试' : ''"
    />
  </bottom-sheet>
</template>

<script>
import BottomSheet from './BottomSheet.vue';
import SearchBar from './SearchBar.vue';
import StateBlock from './StateBlock.vue';
import AppIcon from './AppIcon.vue';

/**
 * 分组选择抽屉（替代原 TreeSelectSheet）
 * items: [{ text, children: [{ id, text, desc? }] }]
 * 改进点：
 *  1) 分类改为顶部横向滚动 Tab，中文标签不再被窄侧栏截断
 *  2) 搜索时跨分类平铺 + 分组头
 *  3) 行高 52px，触控友好
 */
export default {
  name: 'GroupPicker',
  components: { BottomSheet, SearchBar, StateBlock, AppIcon },
  props: {
    value: { type: Boolean, default: false },
    title: { type: String, default: '请选择' },
    items: { type: Array, default: () => [] },
    selected: { type: [Array, String, Number], default: () => [] },
    mode: { type: String, default: 'single' },
    loading: { type: Boolean, default: false },
    searchable: { type: Boolean, default: true },
    searchPlaceholder: { type: String, default: '输入关键词筛选' },
    emptyText: { type: String, default: '暂无数据' },
    height: { type: String, default: '76vh' },
    resetOnOpen: { type: Boolean, default: false },
  },
  data() {
    return { inner: [], keyword: '', activeGroup: 0 };
  },
  computed: {
    visible: {
      get() {
        return this.value;
      },
      set(v) {
        this.$emit('input', v);
      },
    },
    groups() {
      return (this.items || []).map((g, i) => ({ ...g, _key: g.id != null ? g.id : 'g' + i }));
    },
    current() {
      const g = this.groups[this.activeGroup];
      return g && g.children ? g.children : [];
    },
    /** 渲染行：搜索时跨分类平铺（带分组头），否则当前分类 */
    rows() {
      const kw = this.keyword.trim().toLowerCase();
      if (!kw) return this.current;
      const out = [];
      this.groups.forEach((g) => {
        const hit = (g.children || []).filter((c) =>
          String(c.text || '').toLowerCase().includes(kw)
        );
        if (hit.length) {
          out.push({ _group: true, text: g.text });
          hit.forEach((h) => out.push(h));
        }
      });
      return out;
    },
    allCurrentSelected() {
      const ids = this.current.map((c) => c.id);
      return ids.length > 0 && ids.every((id) => this.inner.includes(id));
    },
    totalCount() {
      return this.groups.reduce((s, g) => s + (g.children || []).length, 0);
    },
    subtitleText() {
      if (this.loading) return '';
      if (this.mode === 'multiple') return `共 ${this.totalCount} 项 · 已选 ${this.inner.length}`;
      return this.totalCount ? `共 ${this.totalCount} 项` : '';
    },
  },
  watch: {
    value(open) {
      if (open) {
        this.keyword = '';
        this.activeGroup = 0;
        this.inner = this.resetOnOpen ? [] : this.normalize(this.selected);
      }
    },
    items() {
      if (this.activeGroup >= this.groups.length) this.activeGroup = 0;
    },
  },
  methods: {
    normalize(v) {
      let arr = [];
      if (Array.isArray(v)) arr = [...v];
      else if (v !== null && v !== undefined && v !== '' && v !== 0) arr = [v];
      if (this.mode === 'single' && arr.length > 1) arr = [arr[0]];
      return arr;
    },
    pick(row) {
      if (this.mode === 'single') {
        this.inner = [row.id];
        this.emitConfirm();
      } else {
        const i = this.inner.indexOf(row.id);
        if (i > -1) this.inner.splice(i, 1);
        else this.inner.push(row.id);
      }
    },
    toggleAllCurrent() {
      const ids = this.current.map((c) => c.id);
      if (this.allCurrentSelected) {
        this.inner = this.inner.filter((id) => !ids.includes(id));
      } else {
        this.inner = [...this.inner, ...ids.filter((id) => !this.inner.includes(id))];
      }
    },
    emitConfirm() {
      const all = this.groups.reduce((a, g) => a.concat(g.children || []), []);
      const items = all.filter((c) => this.inner.includes(c.id));
      this.$emit('confirm', { ids: [...this.inner], items });
      this.$emit(
        'update:selected',
        this.mode === 'single' ? (this.inner.length ? this.inner[0] : null) : [...this.inner]
      );
      this.visible = false;
    },
  },
};
</script>

<style scoped lang="scss">
.gpick {
  &__tabs {
    display: flex;
    gap: 8px;
    padding: 10px 14px 8px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    border-bottom: 1px solid var(--bd-light);
  }

  &__tab {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 34px;
    padding: 0 14px;
    border-radius: var(--r-full);
    font-size: var(--fs-13);
    color: var(--c-ink-600);
    background: rgba(255, 255, 255, 0.86);
    border: 1px solid var(--bd-base);
    transition: all var(--dur-fast) var(--ease-out);

    em {
      font-style: normal;
      font-size: var(--fs-11);
      color: var(--c-ink-400);
    }

    &.is-on {
      color: #fff;
      background: var(--g-brand);
      border-color: transparent;
      box-shadow: var(--sh-brand);
      em {
        color: rgba(255, 255, 255, 0.78);
      }
    }
    &:active {
      transform: scale(0.97);
    }
  }

  &__all {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 44px;
    padding: 0 16px;
    background: rgba(255, 255, 255, 0.6);
    border-bottom: 1px solid var(--bd-light);
    font-size: var(--fs-13);
    color: var(--c-ink-700);
  }
  &__all-left {
    display: flex;
    align-items: center;
    gap: 9px;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
  }
  &__count {
    flex-shrink: 0;
    font-size: var(--fs-12);
    color: var(--c-brand-500);
    font-weight: 500;
  }
  &__box {
    width: 18px;
    height: 18px;
    border-radius: 5px;
    border: 1.5px solid var(--c-ink-300);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    &.is-on {
      background: var(--g-brand);
      border-color: transparent;
    }
  }

  &__list {
    padding-bottom: 8px;
  }

  &__grouphead {
    padding: 12px 16px 6px;
    font-size: var(--fs-11);
    font-weight: 600;
    color: var(--c-brand-500);
    letter-spacing: 0.05em;
    background: rgba(242, 247, 255, 0.7);
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 11px;
    min-height: 52px;
    padding: 9px 16px;
    border-bottom: 1px solid var(--bd-light);
    transition: background var(--dur-fast) var(--ease-out);

    &:active {
      background: var(--c-brand-50);
    }
    &.is-on {
      background: linear-gradient(90deg, rgba(46, 107, 230, 0.07), rgba(34, 184, 207, 0.04));
    }
  }

  &__marker {
    flex-shrink: 0;
    width: 19px;
    height: 19px;
    border-radius: 6px;
    border: 1.5px solid var(--c-ink-200);
    background: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    transition: all var(--dur-fast) var(--ease-out);
  }
  &__row.is-on &__marker {
    background: var(--g-brand);
    border-color: transparent;
  }

  &__body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  &__label {
    font-size: var(--fs-14);
    line-height: 1.45;
    color: var(--c-ink-800);
    word-break: break-word;
  }
  &__row.is-on &__label {
    color: var(--c-brand-600);
    font-weight: 500;
  }
  &__desc {
    font-size: var(--fs-11);
    color: var(--c-ink-400);
  }
  &__chev {
    flex-shrink: 0;
    color: var(--c-ink-300);
  }
}
</style>
