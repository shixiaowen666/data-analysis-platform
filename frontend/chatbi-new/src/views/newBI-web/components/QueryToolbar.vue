<template>
  <div class="qtool">
    <!-- ============ 摘要行（默认折叠，节省垂直空间） ============ -->
    <button
      class="qtool__summary"
      :class="{ 'is-open': step.toolbarOpen }"
      @click="toggleOpen"
    >
      <app-icon name="sliders" :size="14" class="qtool__sicon" />
      <span class="qtool__stext u-ellipsis">{{ summaryText }}</span>
      <span v-if="step.dirty" class="qtool__dirty">未应用</span>
      <app-icon
        name="chevron-down"
        :size="15"
        class="qtool__caret"
        :class="{ 'is-up': step.toolbarOpen }"
      />
    </button>

    <!-- ============ 展开面板：竖向堆叠，每块独立 ============ -->
    <transition name="fx-collapse">
      <div v-show="step.toolbarOpen" class="qtool__panel">
        <!-- 维度 -->
        <div class="qtool__block">
          <div class="qtool__bhead">
            <span class="qtool__blabel">
              <app-icon name="layers" :size="13" />维度
              <em>{{ step.dims.length }}</em>
            </span>
            <button class="qtool__add" @click="openDimPicker">
              <app-icon name="plus" :size="13" :stroke-width="2.4" />添加
            </button>
          </div>
          <div v-if="step.dims.length" class="qtool__tags">
            <span
              v-for="d in step.dims"
              :key="d.key"
              class="qtool__tag"
              :class="{ 'is-hit': step.highlightKeys.includes(d.key) }"
              @click="$emit('toggle-highlight', d.key)"
            >
              <span class="qtool__tagtext">{{ d.name }}</span>
              <button class="qtool__tagx" @click.stop="$emit('remove-dim', d)">
                <app-icon name="close" :size="10" :stroke-width="2.6" />
              </button>
            </span>
          </div>
          <p v-else class="qtool__none">未选择维度</p>
        </div>

        <!-- 指标 -->
        <div class="qtool__block">
          <div class="qtool__bhead">
            <span class="qtool__blabel">
              <app-icon name="sigma" :size="13" />指标
              <em>{{ step.metrics.length }}</em>
            </span>
            <button class="qtool__add" @click="openMetricPicker">
              <app-icon name="plus" :size="13" :stroke-width="2.4" />添加
            </button>
          </div>
          <div v-if="step.metrics.length" class="qtool__tags">
            <span
              v-for="m in step.metrics"
              :key="m.key"
              class="qtool__tag qtool__tag--metric"
              :class="{ 'is-hit': step.highlightKeys.includes(m.key) }"
              @click="$emit('toggle-highlight', m.key)"
            >
              <span class="qtool__tagtext">{{ m.name }}</span>
              <button
                class="qtool__tagx"
                @click.stop="$emit('remove-metric', m)"
              >
                <app-icon name="close" :size="10" :stroke-width="2.6" />
              </button>
            </span>
          </div>
          <p v-else class="qtool__none">未选择指标</p>
        </div>

        <!-- 时间 -->
        <div class="qtool__block">
          <div class="qtool__bhead">
            <span class="qtool__blabel"
              ><app-icon name="calendar" :size="13" />时间</span
            >
          </div>

          <button class="qtool__row" @click="showGranularity = true">
            <span class="qtool__rlabel">统计粒度</span>
            <span class="qtool__rvalue">{{ granularityName }}</span>
            <app-icon name="chevron-right" :size="14" class="qtool__rchev" />
          </button>

          <button class="qtool__row" @click="showCalendar = true">
            <span class="qtool__rlabel">时间范围</span>
            <span class="qtool__rvalue u-num u-ellipsis">
              {{ step.dateRange[0] }} ~ {{ step.dateRange[1] }}
            </span>
            <app-icon name="chevron-right" :size="14" class="qtool__rchev" />
          </button>

          <div class="qtool__quick u-no-scrollbar">
            <button
              
              v-for="q in quickRanges[step.granularity]"

              :key="q.key"
              class="qtool__qbtn"
              :class="{ 'is-on': isQuickActive(q) }"
              @click="applyQuick(q)"
            >
              {{ q.text }}
            </button>
          </div>
        </div>

        <!-- 筛选条件：每条一张卡（竖向 3 行），彻底解决横向挤压 -->
        <div class="qtool__block">
          <div class="qtool__bhead">
            <span class="qtool__blabel">
              <app-icon name="filter" :size="13" />筛选条件
              <em>{{ step.filters.length }}</em>
            </span>
            <button class="qtool__add" @click="openFilterPicker">
              <app-icon name="plus" :size="13" :stroke-width="2.4" />添加
            </button>
          </div>

          <div v-if="step.filters.length" class="qtool__filters">
            <div
              v-for="(f, fi) in step.filters"
              :key="f.uid"
              class="qtool__fcard fx-fade-up"
            >
              <div class="qtool__fhead">
                <span
                  class="qtool__fkind"
                  :class="f.type === 0 ? 'is-dim' : 'is-met'"
                >
                  {{ f.type === 0 ? "维" : "标" }}
                </span>
                <span class="qtool__fname u-ellipsis">{{ f.name }}</span>
                <button
                  class="qtool__fdel u-tap"
                  @click="$emit('remove-filter', fi)"
                >
                  <app-icon name="trash" :size="13" />
                </button>
              </div>

              <button class="qtool__frow" @click="openOperator(f)">
                <span class="qtool__flabel">运算符</span>
                <span class="qtool__fvalue">{{
                  operatorName(f.operator)
                }}</span>
                <app-icon
                  name="chevron-right"
                  :size="13"
                  class="qtool__rchev"
                />
              </button>

              <button
                v-if="isMulti(f)"
                class="qtool__frow"
                @click="openValuePicker(f)"
              >
                <span class="qtool__flabel">取值</span>
                <span class="qtool__fvalue u-ellipsis">{{ valueText(f) }}</span>
                <app-icon
                  name="chevron-right"
                  :size="13"
                  class="qtool__rchev"
                />
              </button>

              <div v-else class="qtool__frow qtool__frow--input">
                <span class="qtool__flabel">取值</span>
                <input
                  v-model="f.value"
                  class="qtool__finput"
                  type="text"
                  placeholder="请输入"
                  @input="markDirty"
                />
              </div>
            </div>
          </div>
          <p v-else class="qtool__none">未设置筛选条件</p>

          <div v-if="step.loadingFilterMeta" class="qtool__loading">
            <tech-loader :size="22" inline text="加载可选值" />
          </div>
        </div>

        <!-- 查询语句：SQL 入口收纳在此，替代原独立工具按钮行 -->
        <div class="qtool__block">
          <div class="qtool__bhead">
            <span class="qtool__blabel"
              ><app-icon name="file-code" :size="13" />查询语句</span
            >
          </div>
          <button
            class="qtool__row"
            :disabled="!step.viewSql"
            @click="$emit('view-sql', step)"
          >
            <span class="qtool__rlabel">查看 SQL</span>
            <span class="qtool__rvalue qtool__rvalue--sql u-ellipsis">
              {{ step.viewSql ? sqlPreview : "暂不可用" }}
            </span>
            <app-icon name="chevron-right" :size="14" class="qtool__rchev" />
          </button>
        </div>

        <!-- 操作 -->
        <div class="qtool__actions">
          <button class="qtool__reset" @click="$emit('reset')">
            <app-icon name="refresh" :size="14" />重置
          </button>
          <button
            class="qtool__apply"
            :class="{ 'is-dirty': step.dirty }"
            :disabled="step.loadingData"
            @click="$emit('apply', 1)"
          >
            <tech-loader v-if="step.loadingData" :size="17" inline />
            <app-icon v-else name="play" :size="13" />
            {{
              step.loadingData ? "查询中" : step.dirty ? "应用修改" : "重新查询"
            }}
          </button>
        </div>
      </div>
    </transition>

    <!-- ============ 选择器 ============ -->
    <option-picker
      v-model="showDimPicker"
      title="添加维度"
      mode="single"
      :options="step.candidateDims"
      label-key="name"
      value-key="id"
      :loading="step.loadingCandidate"
      reset-on-open
      empty-text="暂无可添加的维度"
      @confirm="(v) => $emit('add-dim', v)"
    />

    <option-picker
      v-model="showMetricPicker"
      title="添加指标"
      mode="single"
      :options="step.candidateMetrics"
      label-key="name"
      value-key="id"
      :loading="step.loadingCandidate"
      reset-on-open
      empty-text="暂无可添加的指标"
      @confirm="(v) => $emit('add-metric', v)"
    />

    <group-picker
      v-model="showFilterPicker"
      title="选择筛选字段"
      mode="multiple"
      :items="filterFieldGroups"
      reset-on-open
      empty-text="请先添加维度或指标"
      @confirm="(v) => $emit('add-filters', v)"
    />

    <option-picker
      v-model="showOperatorPicker"
      title="选择运算符"
      mode="single"
      :options="operatorOptions"
      label-key="label"
      value-key="value"
      :searchable="false"
      height="56vh"
      @confirm="onOperatorConfirm"
    />

    <option-picker
      v-model="showValuePicker"
      title="选择取值"
      mode="multiple"
      :options="valueOptions"
      label-key="name"
      value-key="name"
      :selected="activeFilter ? toArray(activeFilter.value) : []"
      :loading="activeFilter ? activeFilter.loadingOptions : false"
      empty-text="暂无可选值"
      @confirm="onValueConfirm"
    />

    <option-picker
      v-model="showGranularity"
      title="统计粒度"
      mode="single"
      :options="granularityOptions"
      label-key="name"
      value-key="value"
      :searchable="false"
      :selected="step.granularity"
      height="48vh"
      @confirm="onGranularityConfirm"
    />

    <van-calendar
      v-model="showCalendar"
      type="range"
      :min-date="minDate"
      :max-date="maxDate"
      :default-date="calendarDefault"
      :allow-same-day="true"
      color="#2e6be6"
      confirm-text="确定"
      @confirm="onCalendarConfirm"
    />
  </div>
</template>

<script>
import AppIcon from "@/components/web/AppIcon.vue";
import OptionPicker from "@/components/web/OptionPicker.vue";
import GroupPicker from "@/components/web/GroupPicker.vue";
import TechLoader from "@/components/web/TechLoader.vue";
import { isMultiValueOperator } from "@/api/newBI/newBIAPI.js";
import {shortcutDate, handleShortcutDate} from "@/utils/common";

import {
  granularityEnum,
  operatorEnum,
} from "@/api/metricDataPreview/metricPreviewAPI.js";

import { fmt } from "@/utils/parseChat";

/**
 * 查询改写工具条（移动端重灾区重构）
 * 原实现：一行塞 4 个 60px 控件，375px 屏下全部省略号
 * 现实现：摘要行折叠 + 展开后竖向分块 + 每条筛选器独立卡片 + 抽屉选择
 */
export default {
  name: "QueryToolbar",
  components: { AppIcon, OptionPicker, GroupPicker, TechLoader },
  props: {
    step: { type: Object, required: true },
  },
  data() {
    return {
      granularityEnum,
      operatorEnum,

      showDimPicker: false,
      showMetricPicker: false,
      showFilterPicker: false,
      showOperatorPicker: false,
      showValuePicker: false,
      showGranularity: false,
      showCalendar: false,
      activeFilter: null,
      granularityOptions: granularityEnum,
      minDate: new Date(2020, 0, 1),
      maxDate: new Date(2030, 11, 31),
      /*quickRanges: [
        { label: "今天", type: "day" },
        { label: "本周", type: "week" },
        { label: "本月", type: "month" },
        { label: "本季", type: "quarter" },
        { label: "今年", type: "year" },
      ],*/

      quickRanges:shortcutDate,

    };
  },
  computed: {
    summaryText() {
      const parts = [];
      parts.push(`${this.step.dims.length} 维度`);
      parts.push(`${this.step.metrics.length} 指标`);
      if (this.step.filters.length)
        parts.push(`${this.step.filters.length} 筛选`);
      parts.push(this.granularityName);
      parts.push(`${this.step.dateRange[0]} ~ ${this.step.dateRange[1]}`);
      return parts.join(" · ");
    },
    granularityName() {
      const g = granularityEnum.find((x) => x.value === this.step.granularity);
      return g ? g.name : "日";
    },
    /* SQL 单行预览：压缩空白，截断到 30 字符，避免撑破行宽 */
    sqlPreview() {
      const s = String(this.step.viewSql || "")
        .replace(/\s+/g, " ")
        .trim();
      return s.length > 30 ? `${s.slice(0, 30)}…` : s;
    },
    operatorOptions() {
      return operatorEnum.map((o) => ({
        value: o.value,
        label: `${o.name}  ${o.symbol}`,
      }));
    },
    valueOptions() {
      if (!this.activeFilter) return [];
      return (this.activeFilter.options || []).map((v) => ({
        name: String(v),
      }));
    },
    filterFieldGroups() {
      const groups = [];
      if (this.step.dims.length) {
        groups.push({
          id: "dim",
          text: "维度",
          children: this.step.dims.map((d) => ({
            id: `dim:${d.key}`,
            text: d.name,
          })),
        });
      }
      if (this.step.metrics.length) {
        groups.push({
          id: "metric",
          text: "指标",
          children: this.step.metrics.map((m) => ({
            id: `metric:${m.key}`,
            text: m.name,
          })),
        });
      }
      return groups;
    },
    calendarDefault() {
      try {
        return [
          new Date(this.step.dateRange[0]),
          new Date(this.step.dateRange[1]),
        ];
      } catch (e) {
        return undefined;
      }
    },
  },
  methods: {
    toggleOpen() {
      this.$emit("toggle-toolbar", !this.step.toolbarOpen);
    },
    markDirty() {
      this.$emit("dirty");
    },
    isMulti(f) {
      return f.type === 0 && isMultiValueOperator(f.operator);
    },
    toArray(v) {
      return Array.isArray(v) ? v : v ? [v] : [];
    },
    operatorName(op) {
      const o = operatorEnum.find((x) => x.value === op);
      return o ? `${o.name} ${o.symbol}` : "请选择";
    },
    valueText(f) {
      const arr = this.toArray(f.value);
      if (!arr.length) return "请选择";
      if (arr.length === 1) return String(arr[0]);
      return `${arr[0]} +${arr.length - 1}`;
    },
    openDimPicker() {
      this.showDimPicker = true;
      this.$emit("load-candidates", "dim");
    },
    openMetricPicker() {
      this.showMetricPicker = true;
      this.$emit("load-candidates", "metric");
    },
    openFilterPicker() {
      if (!this.step.dims.length && !this.step.metrics.length) {
        this.$toast2 && this.$toast2.warn("请先添加维度或指标");
        return;
      }
      this.showFilterPicker = true;
    },
    openOperator(f) {
      this.activeFilter = f;
      this.showOperatorPicker = true;
    },
    onOperatorConfirm(v) {
      if (!this.activeFilter || !v.ids.length) return;
      const op = v.ids[0];
      const f = this.activeFilter;
      f.operator = op;
      // 值类型随运算符切换（数组 <-> 标量）
      if (this.isMulti(f)) {
        f.value = Array.isArray(f.value) ? f.value : f.value ? [f.value] : [];
      } else {
        f.value = Array.isArray(f.value)
          ? f.value[0] || ""
          : f.value == null
          ? ""
          : f.value;
      }
      this.markDirty();
    },
    openValuePicker(f) {
      this.activeFilter = f;
      this.showValuePicker = true;
      this.$emit("load-filter-values", f);
    },
    onValueConfirm(v) {
      if (!this.activeFilter) return;
      this.activeFilter.value = v.ids;
      this.markDirty();
    },
    onGranularityConfirm(v) {
      if (!v.ids.length) return;
      this.$emit("set-granularity", v.ids[0]);
    },
    onCalendarConfirm(dates) {
      if (!dates || dates.length < 2) return;
      this.showCalendar = false;
      this.$emit("set-range", [fmt(dates[0]), fmt(dates[1])]);
    },
    isQuickActive(q) {
      const r = this.computeQuick(q);
      return r[0] === this.step.dateRange[0] && r[1] === this.step.dateRange[1];
    },
    computeQuick(q) {
      /*const now = new Date();
      let s;
      let e = new Date(now);
      if (q.days) {
        s = new Date(now);
        s.setDate(s.getDate() - (q.days - 1));
      } else if (q.type === 'thisMonth') {
        s = new Date(now.getFullYear(), now.getMonth(), 1);
      } else if (q.type === 'lastMonth') {
        s = new Date(now.getFullYear(), now.getMonth() - 1, 1);
        e = new Date(now.getFullYear(), now.getMonth(), 0);
      } else if (q.type === 'thisQuarter') {
        s = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1);
      } else {
        s = new Date(now.getFullYear(), 0, 1);
      }*/
      let s;
      let e;
      const type = q.type

      const date = handleShortcutDate(type)
      s = new Date(date[0]);
      e = new Date(date[1]);

      return [fmt(s), fmt(e)];
    },
    applyQuick(q) {
      this.$emit("set-range", this.computeQuick(q));
    },
  },
};
</script>

<style scoped lang="scss">
.qtool {
  /* ---------- 摘要行 ---------- */
  &__summary {
    display: flex;
    align-items: center;
    gap: 7px;
    width: 100%;
    min-height: 40px;
    padding: 8px 10px;
    border-radius: var(--r-sm);
    background: linear-gradient(
      90deg,
      rgba(46, 107, 230, 0.055),
      rgba(34, 184, 207, 0.03)
    );
    border: 1px solid var(--c-brand-100);
    transition: all var(--dur-fast) var(--ease-out);

    &:active {
      transform: scale(0.99);
    }
    &.is-open {
      border-color: var(--c-brand-200);
      background: rgba(255, 255, 255, 0.86);
    }
  }

  &__sicon {
    flex-shrink: 0;
    color: var(--c-brand-500);
  }

  &__stext {
    flex: 1;
    min-width: 0;
    text-align: left;
    font-size: var(--fs-11);
    color: var(--c-ink-600);
    line-height: 1.5;
  }

  &__dirty {
    flex-shrink: 0;
    font-size: 10px;
    color: var(--c-warn);
    background: var(--c-warn-bg);
    border: 1px solid rgba(231, 146, 23, 0.24);
    border-radius: var(--r-full);
    padding: 1px 6px;
  }

  &__caret {
    flex-shrink: 0;
    color: var(--c-ink-300);
    transition: transform var(--dur-base) var(--ease-out);
    &.is-up {
      transform: rotate(180deg);
      color: var(--c-brand-400);
    }
  }

  /* ---------- 展开面板 ---------- */
  &__panel {
    margin-top: 10px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__block {
    padding: 11px 12px;
    border-radius: var(--r-sm);
    background: rgba(255, 255, 255, 0.78);
    border: 1px solid var(--bd-light);
  }

  &__bhead {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 9px;
  }

  &__blabel {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: var(--fs-12);
    font-weight: 600;
    color: var(--c-ink-600);

    .app-icon {
      color: var(--c-brand-400);
    }

    em {
      font-style: normal;
      font-size: var(--fs-11);
      font-weight: 500;
      color: var(--c-brand-500);
      background: var(--c-brand-50);
      border-radius: var(--r-full);
      padding: 1px 6px;
      margin-left: 1px;
    }
  }

  &__add {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    height: 28px;
    padding: 0 10px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-brand-600);
    background: var(--c-brand-50);
    border: 1px solid var(--c-brand-100);

    &:active {
      background: var(--c-brand-100);
      transform: scale(0.97);
    }
  }

  /* ---------- 标签 ---------- */
  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 7px;
  }

  &__tag {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    max-width: 100%;
    height: 32px;
    padding: 0 3px 0 11px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-brand-700);
    background: var(--c-brand-50);
    border: 1px solid var(--c-brand-100);
    transition: all var(--dur-fast) var(--ease-out);

    &--metric {
      color: var(--c-cyan-600);
      background: var(--c-cyan-50);
      border-color: var(--c-cyan-100);
    }

    &.is-hit {
      color: #fff;
      background: var(--g-brand);
      border-color: transparent;
      box-shadow: var(--sh-brand);
    }

    &:active {
      transform: scale(0.97);
    }
  }

  &__tagtext {
    min-width: 0;
    max-width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__tagx {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: currentColor;
    opacity: 0.55;
    /* 扩展至 44px 热区 */
    position: relative;
    &::after {
      content: "";
      position: absolute;
      left: 50%;
      top: 50%;
      width: 44px;
      height: 44px;
      transform: translate(-50%, -50%);
    }
    &:active {
      opacity: 1;
      background: rgba(0, 0, 0, 0.07);
    }
  }

  &__none {
    margin: 0;
    font-size: var(--fs-11);
    color: var(--c-ink-300);
    padding: 3px 0;
  }

  /* ---------- 行式选择 ---------- */
  &__row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 44px;
    padding: 8px 0;
    border-top: 1px dashed var(--bd-light);
    text-align: left;

    &:first-of-type {
      border-top: none;
    }
    &:active:not(:disabled) {
      background: rgba(46, 107, 230, 0.035);
    }
    &:disabled {
      opacity: 0.45;
    }
  }

  &__rlabel {
    flex-shrink: 0;
    font-size: var(--fs-12);
    color: var(--c-ink-500);
    min-width: 56px;
  }

  &__rvalue {
    flex: 1;
    min-width: 0;
    text-align: right;
    font-size: var(--fs-12);
    color: var(--c-ink-800);
    font-weight: 500;

    /* SQL 预览用等宽字体 + 弱化色，与可点击语义区分 */
    &--sql {
      font-family: var(--ff-num);
      font-size: var(--fs-11);
      font-weight: 400;
      color: var(--c-ink-400);
    }
  }

  &__rchev {
    flex-shrink: 0;
    color: var(--c-ink-300);
  }

  /* ---------- 快捷时间 ---------- */
  &__quick {
    display: flex;
    gap: 6px;
    margin-top: 9px;
    padding-top: 9px;
    border-top: 1px dashed var(--bd-light);
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  &__qbtn {
    flex-shrink: 0;
    height: 30px;
    padding: 0 12px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-ink-600);
    background: var(--bg-sunk);
    border: 1px solid transparent;
    transition: all var(--dur-fast) var(--ease-out);

    &.is-on {
      color: var(--c-brand-600);
      background: var(--c-brand-50);
      border-color: var(--c-brand-200);
      font-weight: 500;
    }
    &:active {
      transform: scale(0.96);
    }
  }

  /* ---------- 筛选卡 ---------- */
  &__filters {
    display: flex;
    flex-direction: column;
    gap: 9px;
  }

  &__fcard {
    border-radius: var(--r-sm);
    background: #fff;
    border: 1px solid var(--bd-base);
    overflow: hidden;
  }

  &__fhead {
    display: flex;
    align-items: center;
    gap: 7px;
    min-height: 40px;
    padding: 7px 10px;
    background: var(--bg-soft);
    border-bottom: 1px solid var(--bd-light);
  }

  &__fkind {
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    font-weight: 700;
    color: #fff;

    &.is-dim {
      background: var(--g-brand);
    }
    &.is-met {
      background: linear-gradient(135deg, #22b8cf, #1596ab);
    }
  }

  &__fname {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-12);
    font-weight: 600;
    color: var(--c-ink-800);
  }

  &__fdel {
    flex-shrink: 0;
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-xs);
    color: var(--c-danger);
    opacity: 0.72;
    &:active {
      opacity: 1;
      background: var(--c-danger-bg);
    }
  }

  &__frow {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 42px;
    padding: 6px 10px;
    text-align: left;

    & + & {
      border-top: 1px dashed var(--bd-light);
    }
    &:active:not(&--input) {
      background: rgba(46, 107, 230, 0.035);
    }
  }

  &__flabel {
    flex-shrink: 0;
    min-width: 46px;
    font-size: var(--fs-12);
    color: var(--c-ink-500);
  }

  &__fvalue {
    flex: 1;
    min-width: 0;
    text-align: right;
    font-size: var(--fs-12);
    color: var(--c-ink-800);
    font-weight: 500;
  }

  &__finput {
    flex: 1;
    min-width: 0;
    height: 32px;
    padding: 0 9px;
    text-align: right;
    border-radius: var(--r-xs);
    border: 1px solid var(--bd-base);
    outline: none;
    background: #fff;
    font-size: var(--fs-12);
    color: var(--c-ink-800);

    &:focus {
      border-color: var(--c-brand-300);
      box-shadow: var(--sh-focus);
    }
    &::placeholder {
      color: var(--c-ink-300);
    }
  }

  &__loading {
    margin-top: 8px;
    display: flex;
    justify-content: center;
  }

  /* ---------- 操作 ---------- */
  &__actions {
    display: flex;
    gap: 9px;
  }

  &__reset {
    flex: 0 0 88px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    height: 44px;
    border-radius: var(--r-sm);
    font-size: var(--fs-13);
    color: var(--c-ink-600);
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid var(--bd-base);
    &:active {
      transform: scale(0.98);
    }
  }

  &__apply {
    flex: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 44px;
    border-radius: var(--r-sm);
    font-size: var(--fs-14);
    font-weight: 600;
    color: #fff;
    background: var(--g-brand);
    box-shadow: var(--sh-brand);
    transition: all var(--dur-fast) var(--ease-out);

    &.is-dirty {
      background: linear-gradient(135deg, #f2a53a, #e79217);
      box-shadow: 0 4px 14px rgba(231, 146, 23, 0.26);
    }
    &:active:not(:disabled) {
      transform: scale(0.985);
    }
    &:disabled {
      opacity: 0.7;
    }
  }
}
</style>
