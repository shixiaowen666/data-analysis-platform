<template>
  <van-popup
    v-model="visible"
    position="bottom"
    round
    :close-on-click-overlay="closeOnOverlay"
    :style="popupStyle"
    :safe-area-inset-bottom="false"
    @closed="$emit('closed')"
    @opened="$emit('opened')"
  >
    <div class="sheet">
      <!-- 拖拽把手 -->
      <div class="sheet__handle" @click="close"><i></i></div>

      <!-- 标题栏 -->
      <header class="sheet__header">
        <div class="sheet__lead">
          <slot name="lead">
            <span v-if="cancelText" class="sheet__side sheet__side--cancel u-tap" @click="onCancel">
              {{ cancelText }}
            </span>
          </slot>
        </div>

        <div class="sheet__titlebox">
          <h3 class="sheet__title u-ellipsis">{{ title }}</h3>
          <p v-if="subtitle" class="sheet__subtitle u-ellipsis">{{ subtitle }}</p>
        </div>

        <div class="sheet__trail">
          <slot name="trail">
            <span
              v-if="confirmText"
              class="sheet__side sheet__side--confirm u-tap"
              :class="{ 'is-disabled': confirmDisabled }"
              @click="onConfirm"
            >
              {{ confirmText }}
            </span>
          </slot>
        </div>
      </header>

      <!-- 副标题区（搜索框等） -->
      <div v-if="$slots.toolbar" class="sheet__toolbar">
        <slot name="toolbar" />
      </div>

      <!-- 内容 -->
      <div class="sheet__body u-scroll-y u-no-scrollbar" :class="{ 'is-flat': flat }">
        <div v-if="loading" class="sheet__loading">
          <slot name="loading">
            <tech-loader :size="42" text="加载中" />
          </slot>
        </div>
        <slot v-else />
      </div>

      <!-- 底部操作 -->
      <footer v-if="$slots.footer" class="sheet__footer">
        <slot name="footer" />
      </footer>

      <div class="sheet__safe"></div>
    </div>
  </van-popup>
</template>

<script>
import TechLoader from './TechLoader.vue';

/**
 * 统一底部抽屉容器
 * - 拖拽把手 / 标题栏 / 工具条 / 滚动内容 / 底部操作 / 安全区
 * - 所有点击热区 ≥ 44px
 */
export default {
  name: 'BottomSheet',
  components: { TechLoader },
  props: {
    value: { type: Boolean, default: false },
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' },
    /** 高度，支持 '72vh' 之类 */
    height: { type: String, default: '74vh' },
    cancelText: { type: String, default: '' },
    confirmText: { type: String, default: '' },
    confirmDisabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    closeOnOverlay: { type: Boolean, default: true },
    /** 内容区无左右内边距 */
    flat: { type: Boolean, default: false },
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
    popupStyle() {
      return { height: this.height, maxHeight: '92vh' };
    },
  },
  methods: {
    close() {
      this.visible = false;
    },
    onCancel() {
      this.$emit('cancel');
      this.close();
    },
    onConfirm() {
      if (this.confirmDisabled) return;
      this.$emit('confirm');
    },
  },
};
</script>

<style scoped lang="scss">
.sheet {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--g-page);
  overflow: hidden;

  &__handle {
    flex-shrink: 0;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    i {
      width: 36px;
      height: 4px;
      border-radius: 2px;
      background: var(--c-ink-200);
      transition: background var(--dur-fast);
    }
    &:active i {
      background: var(--c-ink-300);
    }
  }

  &__header {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    min-height: 48px;
    padding: 0 12px 10px;
    border-bottom: 1px solid var(--bd-light);
  }

  &__lead,
  &__trail {
    flex-shrink: 0;
    min-width: 52px;
    display: flex;
    align-items: center;
  }
  &__trail {
    justify-content: flex-end;
  }

  &__titlebox {
    flex: 1;
    min-width: 0;
    text-align: center;
    padding: 0 6px;
  }

  &__title {
    margin: 0;
    font-size: var(--fs-15);
    font-weight: 600;
    color: var(--c-ink-900);
    line-height: 1.4;
  }

  &__subtitle {
    margin: 2px 0 0;
    font-size: var(--fs-11);
    color: var(--c-ink-400);
  }

  &__side {
    display: inline-flex;
    align-items: center;
    height: 40px;
    padding: 0 4px;
    font-size: var(--fs-14);
    cursor: pointer;
    user-select: none;

    &--cancel {
      color: var(--c-ink-500);
    }
    &--confirm {
      color: var(--c-brand-500);
      font-weight: 600;
      &.is-disabled {
        color: var(--c-ink-300);
      }
      &:active:not(.is-disabled) {
        opacity: 0.65;
      }
    }
  }

  &__toolbar {
    flex-shrink: 0;
    padding: 10px 14px 4px;
  }

  &__body {
    flex: 1;
    min-height: 0;
    padding: 8px 14px 4px;

    &.is-flat {
      padding: 0;
    }
  }

  &__loading {
    height: 100%;
    min-height: 180px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__footer {
    flex-shrink: 0;
    padding: 10px 14px;
    border-top: 1px solid var(--bd-light);
    background: rgba(255, 255, 255, 0.86);
    backdrop-filter: blur(10px);
  }

  &__safe {
    flex-shrink: 0;
    height: var(--safe-bottom);
  }
}
</style>
