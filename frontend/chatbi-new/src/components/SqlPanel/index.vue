<template>
  <div class="sql-panel" :class="{ 'sql-panel--plain': plain }">
    <div class="sql-panel__header" v-if="!plain">
      <div class="sql-panel__lang">
        <base-icon name="terminal" :size="13" />
        <span>SQL</span>
      </div>
      <div class="sql-panel__title" v-if="title">{{ title }}</div>
      <button class="sql-panel__copy" type="button" @click="copySQL">
        <base-icon :name="copied ? 'check' : 'copy'" :size="13" />
        <span>{{ copied ? '已复制' : '复制' }}</span>
      </button>
    </div>
    <div class="sql-panel__body">
      <div class="sql-panel__gutter" v-if="lineNumbers">
        <div v-for="(l, idx) in lines" :key="'ln' + idx" class="sql-panel__lineno">{{ idx + 1 }}</div>
      </div>
      <div class="sql-panel__code">
        <div v-for="(l, idx) in lines" :key="'l' + idx" class="sql-panel__line" v-html="l"></div>
      </div>
    </div>
  </div>
</template>

<script>
import { highlightSQLLines } from '@/utils/sqlHighlight';
import { formatSQL } from '@/utils/sqlFormat';
import BaseIcon from '@/components/BaseIcon';

export default {
  name: 'SqlPanel',
  components: { BaseIcon },
  props: {
    sql: { type: String, default: '' },
    title: { type: String, default: '' },
    lineNumbers: { type: Boolean, default: true },
    plain: { type: Boolean, default: false }, // 无头部工具栏的简洁模式
    autoFormat: { type: Boolean, default: true }, // 自动格式化单行/未排版 SQL
  },
  data() {
    return { copied: false };
  },
  computed: {
    displaySql() {
      return this.autoFormat ? formatSQL(this.sql) : this.sql;
    },
    lines() {
      return highlightSQLLines(this.displaySql);
    },
  },
  methods: {
    copySQL() {
      const text = this.displaySql || '';
      const done = () => {
        this.copied = true;
        setTimeout(() => { this.copied = false; }, 1600);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done).catch(() => this.fallbackCopy(text, done));
      } else {
        this.fallbackCopy(text, done);
      }
    },
    fallbackCopy(text, done) {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); done(); } catch (e) { /* ignore */ }
      document.body.removeChild(ta);
    },
  },
};
</script>

<style scoped lang="scss">
.sql-panel {
  border: 1px solid var(--code-border, #e2e8f0);
  border-radius: var(--radius-md, 10px);
  background: var(--code-bg, #f6f8fb);
  overflow: hidden;
  font-family: var(--font-mono, Consolas, monospace);

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    background: #eef2f7;
    border-bottom: 1px solid var(--code-border, #e2e8f0);
  }

  &__lang {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: #64748b;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 5px;
    padding: 2px 8px;
  }

  &__title {
    flex: 1;
    font-size: 12px;
    color: #94a3b8;
    font-family: var(--font-family, sans-serif);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__copy {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-left: auto;
    font-size: 12px;
    color: #64748b;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 3px 10px;
    cursor: pointer;
    transition: all 0.15s;
    font-family: var(--font-family, sans-serif);

    &:hover {
      color: var(--brand, #2b5cff);
      border-color: rgba(43, 92, 255, 0.4);
    }
  }

  &__body {
    display: flex;
    max-height: 350px;
    overflow: auto;
  }

  &__gutter {
    flex-shrink: 0;
    padding: 12px 0;
    background: var(--code-gutter-bg, #eef2f7);
    border-right: 1px solid var(--code-border, #e2e8f0);
    user-select: none;
    position: sticky;
    left: 0;
  }

  &__lineno {
    font-size: 12px;
    line-height: 1.7;
    color: var(--code-gutter-color, #94a3b8);
    text-align: right;
    padding: 0 10px;
    min-width: 40px;
  }

  &__code {
    flex: 1;
    padding: 12px 16px;
    min-width: 0;
  }

  &__line {
    font-size: 13px;
    line-height: 1.7;
    color: var(--code-text, #334155);
    white-space: pre;
  }
}
</style>

<style lang="scss">
/* v-html 内容为非 scoped, 单独声明 token 样式 */
.sql-panel__line {
  .sql-keyword { color: #7c3aed; font-weight: 700; }
  .sql-function { color: #0369a1; font-weight: 600; }
  .sql-string { color: #059669; }
  .sql-number { color: #d97706; }
  .sql-comment { color: #94a3b8; font-style: italic; }
  .sql-ident { color: #0f766e; }
}
</style>
