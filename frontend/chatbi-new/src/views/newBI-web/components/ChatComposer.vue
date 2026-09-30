<template>
  <div class="comp">
    <div v-if="quickTips.length && !busy" class="comp__quick u-no-scrollbar">
      <button v-for="t in quickTips" :key="t" class="comp__qbtn" @click="$emit('send', t)">{{ t }}</button>
    </div>

    <div class="comp__bar">
      <button class="comp__menu u-tap" @click="$emit('menu')" :disabled="load || busy">
        <app-icon name="grid" :size="18" />
      </button>

      <div class="comp__field" :class="{ 'is-focus': focused }">
        <textarea
          ref="ta"
          v-model="text"
          class="comp__input u-no-scrollbar"
          rows="1"
          :placeholder="placeholder"
          :disabled="busy"
          enterkeyhint="send"
          @focus="onFocus"
          @blur="focused = false"
          @input="autoGrow"
        ></textarea>
        <span v-if="text.length > 200" class="comp__count" :class="countClass">{{ text.length }}/300</span>
      </div>

      <!-- 语音输入占位（后续接入 ASR），空输入时才出现，避免与发送按钮争抢注意力 -->
      <!--<button
        v-if="!busy && !canSend"
        class="comp__voice u-tap"
        aria-label="语音输入"
        @click="onVoice"
      >
        <app-icon name="mic" :size="17" />
      </button>-->

      <button
        class="comp__send"
        :class="{ 'is-stop': busy, 'is-disabled': !busy && !canSend }"
        @click="onAction"
      >
        <app-icon :name="busy ? 'stop' : 'send'" :size="16" />
      </button>
    </div>
    <div class="comp__safe"></div>
  </div>
</template>

<script>
import AppIcon from '@/components/web/AppIcon.vue';
import toast from '@/utils/toast';

export default {
  name: 'ChatComposer',
  components: { AppIcon },
  props: {
    load: { type: Boolean, default: false },
    busy: { type: Boolean, default: false },
    quickTips: { type: Array, default: () => [] },
    placeholder: { type: String, default: '输入你的问题，如：上个月深圳供电量趋势' },
  },
  data() {
    return { text: '', focused: false };
  },
  computed: {
    canSend() {
      const t = this.text.trim();
      return t.length > 0 && t.length <= 300;
    },
    countClass() {
      if (this.text.length > 300) return 'is-over';
      if (this.text.length > 260) return 'is-warn';
      return '';
    },
  },
  methods: {
    onFocus() {
      this.focused = true;
      setTimeout(() => this.$emit('focus'), 220);
    },
    autoGrow() {
      const el = this.$refs.ta;
      if (!el) return;
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 108) + 'px';
    },
    reset() {
      this.text = '';
      this.$nextTick(() => {
        const el = this.$refs.ta;
        if (el) el.style.height = 'auto';
      });
    },
    onAction() {
      if (this.busy) {
        this.$emit('stop');
        return;
      }
      const t = this.text.trim();
      if (!t) return;
      if (t.length > 300) {
        toast.warn('问题最多 300 个字');
        return;
      }
      this.$emit('send', t);
      this.reset();
    },
    onVoice() {
      toast.tip('语音输入即将开放，请先使用文字提问');
    },
  },
};
</script>

<style scoped lang="scss">
.comp {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: saturate(170%) blur(16px);
  -webkit-backdrop-filter: saturate(170%) blur(16px);
  border-top: 1px solid var(--bd-light);
  box-shadow: 0 -2px 14px rgba(29, 62, 120, 0.05);

  &__quick {
    display: flex;
    gap: 7px;
    padding: 9px 12px 0;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  &__qbtn {
    flex-shrink: 0;
    height: 30px;
    padding: 0 12px;
    border-radius: var(--r-full);
    font-size: var(--fs-12);
    color: var(--c-brand-600);
    background: var(--c-brand-50);
    border: 1px solid var(--c-brand-100);
    white-space: nowrap;
    &:active { transform: scale(0.96); }
  }

  &__bar {
    display: flex;
    align-items: flex-end;
    gap: 8px;
    padding: 9px 12px;
  }

  &__menu {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-sm);
    color: var(--c-ink-500);
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid var(--bd-base);
    &:active { background: var(--c-brand-50); color: var(--c-brand-500); transform: scale(0.96); }
  }

  &__voice {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-sm);
    color: var(--c-ink-500);
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid var(--bd-base);
    &:active { background: var(--c-brand-50); color: var(--c-brand-500); transform: scale(0.96); }
  }

  &__field {
    flex: 1;
    min-width: 0;
    position: relative;
    display: flex;
    align-items: center;
    min-height: 40px;
    padding: 4px 12px;
    border-radius: var(--r-lg);
    background: #fff;
    border: 1.5px solid var(--bd-base);
    transition: border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);

    &.is-focus {
      border-color: var(--c-brand-300);
      box-shadow: var(--sh-focus);
    }
  }

  &__input {
    width: 100%;
    min-height: 30px;
    max-height: 108px;
    border: none;
    outline: none;
    resize: none;
    background: transparent;
    font-size: var(--fs-14);
    line-height: 1.55;
    padding: 5px 0;
    color: var(--c-ink-900);
    overflow-y: auto;
    &::placeholder { color: var(--c-ink-300); }
    &:disabled { color: var(--c-ink-400); }
  }

  &__count {
    position: absolute;
    right: 10px;
    bottom: -16px;
    font-family: var(--ff-num);
    font-size: 10px;
    color: var(--c-ink-400);
    &.is-warn { color: var(--c-warn); }
    &.is-over { color: var(--c-danger); font-weight: 600; }
  }

  &__send {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: #fff;
    background: var(--g-brand);
    box-shadow: var(--sh-brand);
    transition: all var(--dur-fast) var(--ease-out);

    &:active { transform: scale(0.94); }
    &.is-disabled { background: var(--c-ink-200); box-shadow: none; color: #fff; }
    &.is-stop { background: linear-gradient(135deg, #ef5a5f, #e5484d); box-shadow: 0 4px 14px rgba(229, 72, 77, 0.24); }
  }

  &__safe { height: var(--safe-bottom); }
}
</style>
