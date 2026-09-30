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
    @cancel="onCancel"
    @confirm="onConfirm"
    @closed="onClosed"
  >
    <template v-if="searchable" #toolbar>
      <search-bar v-model="keyword" :placeholder="searchPlaceholder" />
    </template>

    <!-- 全选（多选） -->
    <div
      v-if="mode === 'multiple' && showSelectAll && filtered.length"
      class="picker__all u-tap"
      @click="toggleAll"
    >
      <span class="picker__all-left">
        <span class="picker__box" :class="{ 'is-on': allSelected }">
          <app-icon v-if="allSelected" name="check" :size="12" :stroke-width="3" />
        </span>
        全选当前列表
      </span>
      <span class="picker__count">已选 {{ inner.length }}</span>
    </div>

    <!-- 选项列表 -->
    <div v-if="filtered.length" class="picker__list">
      <div
        v-for="(item, i) in filtered"
        :key="keyOf(item)"
        class="picker__row fx-fade-up"
        :class="{ 'is-on': isSelected(item), 'is-disabled': item.disabled }"
        :style="{ animationDelay: Math.min(i, 8) * 26 + 'ms' }"
        @click="pick(item)"
      >
        <span v-if="mode === 'multiple'" class="picker__marker" :class="{ 'is-round': mode === 'multiple' }">
          <app-icon v-if="isSelected(item)" name="check" :size="12" :stroke-width="3" />
        </span>
        <span class="picker__body">
          <span class="picker__label">{{ labelOf(item) }}</span>
          <span v-if="item.desc" class="picker__desc u-ellipsis">{{ item.desc }}</span>
        </span>
        <span v-if="item.badge" class="picker__badge">{{ item.badge }}</span>
      </div>
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
 * 通用选择抽屉（替代原 MultiSelectSheet）
 * 改进点：
 *  1) 行高 52px，触控友好
 *  2) 内置搜索过滤（长列表必备）
 *  3) 单选点击即确认；多选顶部全选 + 底部确认
 *  4) 空态 / 加载骨架 / 安全区
 */
export default {
  name: 'OptionPicker',
  components: { BottomSheet, SearchBar, StateBlock, AppIcon },
  props: {
    value: { type: Boolean, default: false },
    title: { type: String, default: '请选择' },
    options: { type: Array, default: () => [] },
    selected: { type: [Array, String, Number], default: () => [] },
    labelKey: { type: String, default: 'name' },
    valueKey: { type: String, default: 'id' },
    /** single | multiple */
    mode: { type: String, default: 'single' },
    loading: { type: Boolean, default: false },
    searchable: { type: Boolean, default: true },
    searchPlaceholder: { type: String, default: '输入关键词筛选' },
    showSelectAll: { type: Boolean, default: true },
    emptyText: { type: String, default: '暂无可选项' },
    height: { type: String, default: '70vh' },
    /** 打开时是否清空已选（原逻辑：维度/指标追加场景需要清空） */
    resetOnOpen: { type: Boolean, default: false },
  },
  data() {
    return { inner: [], keyword: '' };
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
    filtered() {
      const kw = this.keyword.trim().toLowerCase();
      if (!kw) return this.options || [];
      return (this.options || []).filter((o) =>
        String(this.labelOf(o) || '').toLowerCase().includes(kw)
      );
    },
    allSelected() {
      const ids = this.filtered.map((o) => this.keyOf(o));
      return ids.length > 0 && ids.every((id) => this.inner.includes(id));
    },
    subtitleText() {
      if (this.loading) return '';
      const total = (this.options || []).length;
      if (this.mode === 'multiple') return `共 ${total} 项 · 已选 ${this.inner.length}`;
      return total ? `共 ${total} 项` : '';
    },
  },
  watch: {
    value(open) {
      if (open) {
        this.keyword = '';
        this.inner = this.resetOnOpen ? [] : this.normalize(this.selected);
      }
    },
    selected: {
      handler(v) {
        if (this.value && !this.resetOnOpen) this.inner = this.normalize(v);
      },
      deep: true,
    },
  },
  methods: {
    normalize(v) {
      let arr = [];
      if (Array.isArray(v)) arr = [...v];
      else if (v !== null && v !== undefined && v !== '') arr = [v];
      if (this.mode === 'single' && arr.length > 1) arr = [arr[0]];
      return arr;
    },
    keyOf(item) {
      return item[this.valueKey] !== undefined ? item[this.valueKey] : item[this.labelKey];
    },
    labelOf(item) {
      return item[this.labelKey];
    },
    isSelected(item) {
      return this.inner.includes(this.keyOf(item));
    },
    pick(item) {
      if (item.disabled) return;
      const id = this.keyOf(item);
      if (this.mode === 'single') {
        this.inner = [id];
        this.emitConfirm();
      } else {
        const i = this.inner.indexOf(id);
        if (i > -1) this.inner.splice(i, 1);
        else this.inner.push(id);
      }
    },
    toggleAll() {
      const ids = this.filtered.map((o) => this.keyOf(o));
      if (this.allSelected) {
        this.inner = this.inner.filter((id) => !ids.includes(id));
      } else {
        const add = ids.filter((id) => !this.inner.includes(id));
        this.inner = [...this.inner, ...add];
      }
    },
    onConfirm() {
      this.emitConfirm();
    },
    emitConfirm() {
      const all = this.options || [];
      const items = all.filter((o) => this.inner.includes(this.keyOf(o)));
      this.$emit('confirm', { ids: [...this.inner], items });
      this.$emit(
        'update:selected',
        this.mode === 'single' ? (this.inner.length ? this.inner[0] : null) : [...this.inner]
      );
      this.visible = false;
    },
    onCancel() {
      this.$emit('cancel');
    },
    onClosed() {
      this.keyword = '';
    },
  },
};
</script>

<style scoped lang="scss">
.picker {
  &__all {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 46px;
    padding: 0 16px;
    background: rgba(255, 255, 255, 0.66);
    border-bottom: 1px solid var(--bd-light);
    font-size: var(--fs-13);
    color: var(--c-ink-700);
    user-select: none;
  }

  &__all-left {
    display: flex;
    align-items: center;
    gap: 9px;
  }

  &__count {
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
    transition: all var(--dur-fast) var(--ease-out);

    &.is-on {
      background: var(--g-brand);
      border-color: transparent;
    }
  }

  &__list {
    padding: 4px 0 8px;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 11px;
    min-height: 52px;
    padding: 9px 16px;
    background: transparent;
    border-bottom: 1px solid var(--bd-light);
    transition: background var(--dur-fast) var(--ease-out);

    &:active {
      background: var(--c-brand-50);
    }

    &.is-on {
      background: linear-gradient(90deg, rgba(46, 107, 230, 0.07), rgba(34, 184, 207, 0.04));
    }

    &.is-disabled {
      opacity: 0.4;
    }
  }

  &__marker {
    flex-shrink: 0;
    width: 19px;
    height: 19px;
    border-radius: 6px;
    border: 1.5px solid var(--c-ink-200);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: #fff;
    transition: all var(--dur-fast) var(--ease-out);

    &.is-round {
      border-radius: 6px;
    }
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

  &__badge {
    flex-shrink: 0;
    font-size: var(--fs-11);
    color: var(--c-ink-400);
    background: var(--bg-sunk);
    border-radius: var(--r-full);
    padding: 2px 8px;
  }
}
</style>
