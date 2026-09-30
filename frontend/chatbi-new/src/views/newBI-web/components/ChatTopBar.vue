<template>
  <header class="topbar" :class="{ 'is-scrolled': scrolled }">
    <!-- 左：菜单 -->
    <!--<button class="topbar__icon u-tap" @click="$emit('menu')">
      <app-icon name="menu" :size="19" />
    </button>-->
    <div class="topbar__icon u-tap">
      </div>

    <!-- 中：智能体胶囊 -->
    <button class="topbar__agent" @click="$emit('pick-agent')">
      <span class="topbar__avatar">
        <app-icon name="bot" :size="14" />
      </span>
      <span class="topbar__name u-ellipsis">{{ agentName || '选择智能体' }}</span>
      <app-icon name="chevron-down" :size="14" class="topbar__caret" />
    </button>

    <!-- 右：状态灯 + 用户 -->
    <div class="topbar__right">
      <transition name="fx-collapse">
        <span v-if="busy" class="topbar__status">
          <i class="topbar__dot fx-breath"></i>
          <span class="topbar__statustext">{{ busyText }}</span>
        </span>
      </transition>

      <button class="topbar__user u-tap" @click="$emit('user')">
        {{ avatarText }}
      </button>
    </div>
  </header>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';

export default {
  name: 'ChatTopBar',
  components: { AppIcon },
  props: {
    agentName: { type: String, default: '' },
    userName: { type: String, default: '' },
    busy: { type: Boolean, default: false },
    busyText: { type: String, default: '分析中' },
    scrolled: { type: Boolean, default: false },
  },
  computed: {
    avatarText() {
      const n = String(this.userName || '用').trim();
      return n.charAt(0).toUpperCase();
    },
  },
};
</script>

<style scoped lang="scss">
.topbar {
  position: relative;
  z-index: var(--z-header);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  height: calc(var(--h-topbar) + var(--safe-top));
  padding: var(--safe-top) 10px 0;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(170%) blur(14px);
  -webkit-backdrop-filter: saturate(170%) blur(14px);
  border-bottom: 1px solid transparent;
  transition: border-color var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);

  &.is-scrolled {
    border-bottom-color: var(--bd-light);
    box-shadow: 0 1px 10px rgba(29, 62, 120, 0.05);
  }

  &__icon {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-sm);
    color: var(--c-ink-600);
    transition: background var(--dur-fast);

    &:active {
      background: var(--c-brand-50);
      color: var(--c-brand-500);
    }
  }

  &__agent {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 36px;
    padding: 0 10px;
    max-width: 62vw;
    margin: 0 auto;
    border-radius: var(--r-full);
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid var(--c-brand-100);
    box-shadow: var(--sh-1);
    transition: all var(--dur-fast) var(--ease-out);

    &:active {
      transform: scale(0.98);
      background: var(--c-brand-50);
    }
  }

  &__avatar {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
    border-radius: 7px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 2px 6px rgba(46, 107, 230, 0.2);
  }

  &__name {
    min-width: 0;
    font-size: var(--fs-14);
    font-weight: 600;
    color: var(--c-ink-800);
  }

  &__caret {
    flex-shrink: 0;
    color: var(--c-ink-400);
  }

  &__right {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__status {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 24px;
    padding: 0 9px 0 7px;
    border-radius: var(--r-full);
    background: var(--c-brand-50);
    border: 1px solid var(--c-brand-100);
    white-space: nowrap;
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--g-brand);
  }

  &__statustext {
    font-size: var(--fs-11);
    color: var(--c-brand-600);
    font-weight: 500;
  }

  &__user {
    flex-shrink: 0;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--fs-12);
    font-weight: 600;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 2px 8px rgba(46, 107, 230, 0.22);

    &:active {
      transform: scale(0.94);
    }
  }
}

@media (max-width: 359px) {
  .topbar__statustext {
    display: none;
  }
}
</style>
