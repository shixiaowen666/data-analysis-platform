<template>
  <div class="scroll-navigator" :class="{ 'always-show-arrows': alwaysShowArrows }">
    <!-- 左箭头 -->
    <div
      v-show="showLeftArrow"
      class="arrow arrow-left"
      :class="{ disabled: !hasPrev }"
      @click="scrollPrev"
    >
      <slot name="arrow-left">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path d="M15 18l-6-6 6-6" stroke="currentColor" fill="none" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </slot>
    </div>

    <!-- 滚动容器 -->
    <div class="scroll-wrapper" ref="wrapper">
      <div class="scroll-content" ref="content" :style="contentStyle">
        <slot></slot>
      </div>
    </div>

    <!-- 右箭头 -->
    <div
      v-show="showRightArrow"
      class="arrow arrow-right"
      :class="{ disabled: !hasNext }"
      @click="scrollNext"
    >
      <slot name="arrow-right">
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path d="M9 18l6-6-6-6" stroke="currentColor" fill="none" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScrollNavigator',
  props: {
    step: { type: Number, default: 0 },
    loop: { type: Boolean, default: false },
    alwaysShowArrows: { type: Boolean, default: false }
  },
  data() {
    return {
      scrollPos: 0,
      maxScroll: 0,
      containerWidth: 0,
      _resizeObserver: null,
      _mutationObserver: null,
      _refreshTimer: null
    };
  },
  computed: {
    showLeftArrow() {
      return this.alwaysShowArrows || this.maxScroll > 0;
    },
    showRightArrow() {
      return this.alwaysShowArrows || this.maxScroll > 0;
    },
    hasPrev() {
      if (this.loop) return this.maxScroll > 0;
      return this.scrollPos > 0;
    },
    hasNext() {
      if (this.loop) return this.maxScroll > 0;
      return this.scrollPos < this.maxScroll - 0.5;
    },
    contentStyle() {
      return {
        transform: `translateX(-${this.scrollPos}px)`,
        transition: 'transform 0.3s ease'
      };
    }
  },
  mounted() {
   this.updateSize();

    // 创建防抖更新函数（统一使用 requestAnimationFrame）
    const scheduleUpdate = () => {
      if (this._updateTimer) {
        cancelAnimationFrame(this._updateTimer);
        this._updateTimer = null;
      }
      this._updateTimer = requestAnimationFrame(() => {
        this.updateSize();
        this._updateTimer = null;
      });
    };

    // ResizeObserver 监听 wrapper 尺寸变化
    if (window.ResizeObserver) {
      this._resizeObserver = new ResizeObserver(scheduleUpdate);
      this._resizeObserver.observe(this.$refs.wrapper);
    } else {
      window.addEventListener('resize', scheduleUpdate);
    }

    // MutationObserver 监听子元素变化（增删改）
    this._mutationObserver = new MutationObserver(scheduleUpdate);
    this._mutationObserver.observe(this.$refs.content, {
      childList: true,
      subtree: true
    });
  },
  updated() {
    this.$nextTick(this.updateSize);
  },
  beforeDestroy() {
    if (this._resizeObserver) {
      this._resizeObserver.disconnect();
      this._resizeObserver = null;
    } else {
      window.removeEventListener('resize', this.updateSize);
    }
    if (this._mutationObserver) {
      this._mutationObserver.disconnect();
      this._mutationObserver = null;
    }
    if (this._updateTimer) {
      cancelAnimationFrame(this._updateTimer);
      this._updateTimer = null;
    }
  },
  methods: {
    updateSize() {
      const wrapper = this.$refs.wrapper;
      const content = this.$refs.content;
      if (!wrapper || !content) return;
      this.containerWidth = wrapper.clientWidth;
      // 计算内容总宽度（含 margin）
      const children = content.children;
      let totalWidth = 0;
      for (let i = 0; i < children.length; i++) {
        const child = children[i];
        const style = window.getComputedStyle(child);
        const marginRight = parseFloat(style.marginRight) || 0;
        totalWidth += child.offsetWidth + marginRight;
      }
      const contentWidth = Math.max(content.scrollWidth, totalWidth);
      // 只有真正溢出时才加补偿，否则为 0
      const rawMax = contentWidth - this.containerWidth;
      this.maxScroll = rawMax > 0 ? rawMax + 2 : 0;
      // 修正滚动位置
      if (this.scrollPos > this.maxScroll) {
        this.scrollPos = this.maxScroll;
      }
      if (this.maxScroll <= 0) {
        this.scrollPos = 0;
      }
    },
    refresh() {
      if (this._refreshTimer) clearTimeout(this._refreshTimer);
      this._refreshTimer = setTimeout(() => {
        this.updateSize();
        this._refreshTimer = null;
      }, 50);
    },
    getStep() {
      return this.step > 0 ? this.step : Math.ceil(this.containerWidth * 0.8);
    },
    scrollPrev() {
      if (!this.hasPrev) return;
      const step = this.getStep();
      let newPos = this.scrollPos - step;
      if (newPos < 0) {
        newPos = this.loop ? this.maxScroll : 0;
      }
      this.scrollPos = Math.round(newPos);
      this.$emit('change', this.scrollPos);
    },
    scrollNext() {
      if (!this.hasNext) return;
      const step = this.getStep();
      let newPos = this.scrollPos + step;
      if (this.maxScroll - this.scrollPos < step) {
        newPos = this.maxScroll;
      } else if (newPos > this.maxScroll) {
        newPos = this.loop ? 0 : this.maxScroll;
      }
      this.scrollPos = Math.round(newPos);
      this.$emit('change', this.scrollPos);
    }
  }
};
</script>

<style scoped>
/* 样式保持不变 */
.scroll-navigator {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  overflow: hidden;
  position: relative;
}
.arrow {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  cursor: pointer;
  color: #606266;
  background: #f5f7fa;
  transition: all 0.2s;
  user-select: none;
}
.arrow:hover:not(.disabled) {
  background: #e6e9f0;
  color: #409EFF;
}
.arrow.disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.5;
}
.scroll-wrapper {
  flex: 1;
  overflow: hidden;
  position: relative;
}
.scroll-content {
  display: flex;
  flex-wrap: nowrap;
  white-space: nowrap;
  will-change: transform;
  transition: transform 0.3s ease;
  gap: 8px;
}
.scroll-content > * {
  flex-shrink: 0;
  white-space: nowrap;
  margin: 0 !important;
}
.scroll-navigator:not(.always-show-arrows) .arrow {
  opacity: 0;
  transition: opacity 0.2s;
}
.scroll-navigator:not(.always-show-arrows):hover .arrow {
  opacity: 1;
}
.scroll-navigator:not(.always-show-arrows) .arrow.disabled {
  opacity: 0.3;
}
</style>