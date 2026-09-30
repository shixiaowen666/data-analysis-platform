<template>
  <div class="searchbar" :class="{ 'is-focus': focused }">
    <app-icon name="search" :size="16" class="searchbar__icon" />
    <input
      ref="input"
      v-model="inner"
      class="searchbar__input"
      type="search"
      enterkeyhint="search"
      :placeholder="placeholder"
      @focus="focused = true"
      @blur="focused = false"
      @keydown.enter="$emit('search', inner)"
    />
    <button v-if="inner" class="searchbar__clear u-tap" @click="clear">
      <app-icon name="close" :size="13" :stroke-width="2.2" />
    </button>
  </div>
</template>

<script>
import AppIcon from './AppIcon.vue';

export default {
  name: 'SearchBar',
  components: { AppIcon },
  props: {
    value: { type: String, default: '' },
    placeholder: { type: String, default: '搜索' },
  },
  data() {
    return { focused: false };
  },
  computed: {
    inner: {
      get() {
        return this.value;
      },
      set(v) {
        this.$emit('input', v);
      },
    },
  },
  methods: {
    clear() {
      this.inner = '';
      this.$emit('search', '');
    },
    focus() {
      this.$refs.input && this.$refs.input.focus();
    },
  },
};
</script>

<style scoped lang="scss">
.searchbar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  border-radius: var(--r-full);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--bd-base);
  transition: border-color var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) var(--ease-out);

  &.is-focus {
    border-color: var(--bd-brand);
    box-shadow: var(--sh-focus);
  }

  &__icon {
    color: var(--c-ink-400);
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: none;
    outline: none;
    background: transparent;
    font-size: var(--fs-14);
    color: var(--c-ink-800);

    &::placeholder {
      color: var(--c-ink-400);
    }
    &::-webkit-search-cancel-button {
      display: none;
    }
  }

  &__clear {
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--c-ink-200);
    color: #fff;
    &:active {
      background: var(--c-ink-300);
    }
  }
}
</style>
