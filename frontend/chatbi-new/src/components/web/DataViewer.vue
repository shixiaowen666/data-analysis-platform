<template>
  <div class="dview">
    <!-- 视图切换 + 计数 -->
    <div class="dview__bar">
      <div class="dview__meta">
        <app-icon name="table" :size="13" />
        <span class="u-num">{{ total }}</span>
        <span class="dview__metaunit">条</span>
        <span v-if="columns.length" class="dview__dot">·</span>
        <span v-if="columns.length" class="u-num">{{ columns.length }}</span>
        <span v-if="columns.length" class="dview__metaunit">列</span>
      </div>

      <div class="dview__modes">
        <button
          class="dview__mode"
          :class="{ 'is-on': view === 'card' }"
          @click="view = 'card'"
        >
          <app-icon name="grid" :size="13" />卡片
        </button>
        <button
          class="dview__mode"
          :class="{ 'is-on': view === 'table' }"
          @click="view = 'table'"
        >
          <app-icon name="list" :size="13" />表格
        </button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="dview__loading fx-scan-host">
      <skeleton-block variant="record" :rows="5" />
    </div>

    <!-- 空 -->
    <state-block
      v-else-if="!records.length"
      type="empty"
      title="当前条件下无数据"
      desc="可尝试放宽筛选条件或调整时间范围"
    />

    <template v-else>
      <!-- 卡片视图：左右滑动切换记录 -->
      <div v-if="view === 'card'" class="dview__cards">
        <van-swipe
          ref="swipe"
          :loop="false"
          :show-indicators="false"
          :duration="260"
          :touchable="records.length > 1"
          @change="onSwipe"
        >
          <van-swipe-item v-for="(rec, ri) in records" :key="ri">
            <div class="dview__cardwrap">
              <data-record-card
                :record="rec"
                :columns="columns"
                :index="baseIndex + ri + 1"
                :hit-keys="hitKeys"
                :value-by="valueBy"
              />
            </div>
          </van-swipe-item>
        </van-swipe>

        <!-- 当前页内记录指示 -->
        <div v-if="records.length > 1" class="dview__dots">
          <template v-if="records.length <= 10">
            <i
              v-for="n in records.length"
              :key="n"
              :class="{ 'is-on': n - 1 === activeIdx }"
              @click="goto(n - 1)"
            ></i>
          </template>
          <div v-else class="dview__progress">
            <div class="dview__progressbar">
              <span :style="{ width: ((activeIdx + 1) / records.length) * 100 + '%' }"></span>
            </div>
            <span class="dview__progresstext u-num">
              {{ activeIdx + 1 }}/{{ records.length }}
            </span>
          </div>
        </div>

        <div v-if="records.length > 1" class="dview__swipehint">
          <app-icon name="chevron-left" :size="12" />
          左右滑动查看
          <app-icon name="chevron-right" :size="12" />
        </div>
      </div>

      <!-- 表格视图：横向滚动 + 首列吸附 -->
      <div v-else class="dview__table u-no-scrollbar">
        <table>
          <thead>
            <tr>
              <th class="is-sticky">#</th>
              <th v-for="(col, ci) in columns" :key="ci" :class="{ 'is-hit': isHit(col) }">
                {{ col.cn_name || col.name }}<em v-if="col.unit">({{ col.unit }})</em>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(rec, ri) in records" :key="ri">
              <td class="is-sticky u-num">{{ baseIndex + ri + 1 }}</td>
              <td
                v-for="(col, ci) in columns"
                :key="ci"
                :class="{ 'is-hit': isHit(col), 'is-num': isNum(cell(rec, col)) }"
              >
                {{ fmt(cell(rec, col)) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页（内联，不再是 fixed 屏幕两侧） -->
      <div v-if="totalPage > 1" class="dview__pager">
        <button class="dview__pgbtn" :disabled="page <= 1" @click="$emit('prev')">
          <app-icon name="chevron-left" :size="15" />上一页
        </button>
        <span class="dview__pginfo u-num">{{ page }} / {{ totalPage }}</span>
        <button class="dview__pgbtn" :disabled="page >= totalPage" @click="$emit('next')">
          下一页<app-icon name="chevron-right" :size="15" />
        </button>
      </div>
    </template>
  </div>
</template>

<script>
import AppIcon from './AppIcon.vue';
import DataRecordCard from './DataRecordCard.vue';
import SkeletonBlock from './SkeletonBlock.vue';
import StateBlock from './StateBlock.vue';

/**
 * 数据结果浏览器
 * 修复原实现的核心问题：
 *  - 移除 position:fixed 的 SideButtons（多卡重叠、不知道操作哪张卡）
 *  - 分页按钮内联在数据下方，44px 高
 *  - 提供 卡片/表格 双视图，卡片支持左右滑动
 */
export default {
  name: 'DataViewer',
  components: { AppIcon, DataRecordCard, SkeletonBlock, StateBlock },
  props: {
    records: { type: Array, default: () => [] },
    columns: { type: Array, default: () => [] },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 10 },
    total: { type: Number, default: 0 },
    totalPage: { type: Number, default: 1 },
    loading: { type: Boolean, default: false },
    hitKeys: { type: Array, default: () => [] },
    valueBy: { type: String, default: 'key' },
    defaultView: { type: String, default: 'card' },
  },
  data() {
    return { view: this.defaultView, activeIdx: 0 };
  },
  computed: {
    baseIndex() {
      return (this.page - 1) * this.pageSize;
    },
  },
  watch: {
    records() {
      this.activeIdx = 0;
      this.$nextTick(() => {
        if (this.$refs.swipe) this.$refs.swipe.swipeTo(0, { immediate: true });
      });
    },
  },
  methods: {
    onSwipe(i) {
      this.activeIdx = i;
    },
    goto(i) {
      this.activeIdx = i;
      if (this.$refs.swipe) this.$refs.swipe.swipeTo(i);
    },
    isHit(col) {
      return this.hitKeys.includes(col.key || col.name);
    },
    cell(rec, col) {
      const k = this.valueBy === 'name' ? col.name : col.key || col.name;
      const v = rec[k];
      return v === undefined ? rec[col.name] : v;
    },
    isNum(v) {
      return v !== null && v !== '' && v !== undefined && !isNaN(Number(v));
    },
    fmt(v) {
      if (v === null || v === undefined || v === '') return '—';
      if (this.isNum(v)) {
        const n = Number(v);
        return Number.isInteger(n)
          ? n.toLocaleString('en-US')
          : n.toLocaleString('en-US', { maximumFractionDigits: 2 });
      }
      return String(v);
    },
  },
};
</script>

<style scoped lang="scss">
.dview {
  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 3px;
    font-size: var(--fs-12);
    color: var(--c-ink-500);
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;

    .app-icon {
      color: var(--c-brand-400);
      margin-right: 2px;
    }
  }
  &__metaunit {
    color: var(--c-ink-400);
  }
  &__dot {
    color: var(--c-ink-300);
    margin: 0 2px;
  }

  &__modes {
    flex-shrink: 0;
    display: flex;
    background: var(--bg-sunk);
    border-radius: var(--r-full);
    padding: 2px;
  }

  &__mode {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 28px;
    padding: 0 11px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-ink-500);
    transition: all var(--dur-fast) var(--ease-out);

    &.is-on {
      background: #fff;
      color: var(--c-brand-600);
      font-weight: 500;
      box-shadow: var(--sh-1);
    }
  }

  &__loading {
    border-radius: var(--r-md);
  }

  &__cardwrap {
    padding: 0 1px 2px;
  }

  &__dots {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    margin-top: 10px;
    min-height: 20px;

    i {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--c-ink-200);
      transition: all var(--dur-base) var(--ease-out);
      cursor: pointer;

      &.is-on {
        width: 16px;
        border-radius: 3px;
        background: var(--g-brand);
      }
    }
  }

  &__progress {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    max-width: 220px;
  }
  &__progressbar {
    flex: 1;
    height: 3px;
    border-radius: 2px;
    background: var(--c-ink-100);
    overflow: hidden;
    span {
      display: block;
      height: 100%;
      border-radius: 2px;
      background: var(--g-brand);
      transition: width var(--dur-base) var(--ease-out);
    }
  }
  &__progresstext {
    flex-shrink: 0;
    font-size: var(--fs-11);
    color: var(--c-ink-400);
  }

  &__swipehint {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    margin-top: 4px;
    font-size: var(--fs-11);
    color: var(--c-ink-300);
  }

  &__table {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    border: 1px solid var(--bd-light);
    border-radius: var(--r-md);
    background: #fff;
    max-height: 46vh;
    overflow-y: auto;

    table {
      border-collapse: separate;
      border-spacing: 0;
      width: max-content;
      min-width: 100%;
      font-size: var(--fs-12);
    }

    th,
    td {
      padding: 8px 11px;
      text-align: left;
      white-space: nowrap;
      border-bottom: 1px solid var(--bd-light);
    }

    th {
      position: sticky;
      top: 0;
      z-index: 2;
      background: var(--c-brand-50);
      color: var(--c-brand-700);
      font-weight: 600;
      font-size: var(--fs-11);
      border-bottom: 1px solid var(--c-brand-100);

      em {
        font-style: normal;
        font-weight: 400;
        color: var(--c-ink-400);
        margin-left: 2px;
      }

      &.is-hit {
        background: var(--c-brand-100);
      }
    }

    td {
      color: var(--c-ink-700);
      &.is-num {
        font-family: var(--ff-num);
        font-variant-numeric: tabular-nums;
        text-align: right;
        color: var(--c-ink-900);
      }
      &.is-hit {
        background: rgba(46, 107, 230, 0.05);
        color: var(--c-brand-700);
        font-weight: 600;
      }
    }

    .is-sticky {
      position: sticky;
      left: 0;
      z-index: 3;
      background: var(--bg-soft);
      color: var(--c-ink-400);
      font-family: var(--ff-num);
      min-width: 40px;
      text-align: center;
      box-shadow: 1px 0 0 var(--bd-light);
    }
    th.is-sticky {
      z-index: 4;
      background: var(--c-brand-50);
    }

    tbody tr:nth-child(even) td {
      background: rgba(246, 250, 255, 0.72);
      &.is-sticky {
        background: var(--bg-soft);
      }
      &.is-hit {
        background: rgba(46, 107, 230, 0.05);
      }
    }
    tbody tr:last-child td {
      border-bottom: none;
    }
  }

  &__pager {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
  }

  &__pgbtn {
    flex: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    height: 40px;
    border-radius: var(--r-sm);
    font-size: var(--fs-13);
    color: var(--c-brand-600);
    background: rgba(255, 255, 255, 0.92);
    border: 1px solid var(--c-brand-100);
    transition: all var(--dur-fast) var(--ease-out);

    &:active:not(:disabled) {
      transform: scale(0.975);
      background: var(--c-brand-50);
    }
    &:disabled {
      color: var(--c-ink-300);
      border-color: var(--bd-light);
      background: var(--bg-sunk);
    }
  }

  &__pginfo {
    flex-shrink: 0;
    font-size: var(--fs-12);
    color: var(--c-ink-500);
    min-width: 54px;
    text-align: center;
  }
}
</style>
