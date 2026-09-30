<template>
  <div class="sql-editor" :class="{ 'is-focused': focused }">
    <div class="sql-editor__header">
      <span class="sql-editor__dots">
        <i class="dot dot--r"></i><i class="dot dot--y"></i><i class="dot dot--g"></i>
      </span>
      <span class="sql-editor__badge">SQL</span>
      <span class="sql-editor__title">{{ title }}</span>
      <span class="sql-editor__meta">
        <span class="sql-editor__meta-item">Ln {{ cursorLine }}, Col {{ cursorCol }}</span>
        <span class="sql-editor__meta-sep">·</span>
        <span class="sql-editor__meta-item">{{ lineCount }} 行</span>
        <span class="sql-editor__meta-sep">·</span>
        <span class="sql-editor__meta-item">{{ charCount }} 字符</span>
      </span>
      <button
        type="button"
        class="sql-editor__format"
        title="格式化 SQL"
        @click="formatSql"
      >
        <base-icon name="sparkles" :size="12" :stroke-width="2" />
        格式化
      </button>
    </div>
    <div class="sql-editor__body" :style="{ height: bodyHeight }">
      <div class="sql-editor__gutter" ref="gutter">
        <div
          v-for="n in lineCount"
          :key="n"
          class="sql-editor__lineno"
          :class="{ 'is-active': n === cursorLine }"
        >
          {{ n }}
        </div>
      </div>
      <div class="sql-editor__code">
        <!-- 高亮层(只读渲染) -->
        <pre
          class="sql-editor__highlight"
          ref="highlight"
          aria-hidden="true"
          v-html="highlightedHtml"
        ></pre>
        <!-- 输入层(透明文字, 光标可见) -->
        <textarea
          ref="textarea"
          class="sql-editor__textarea"
          :value="value"
          :placeholder="placeholder"
          spellcheck="false"
          wrap="off"
          @input="onInput"
          @scroll="syncScroll"
          @keydown.tab.prevent="onTab"
          @keyup="updateCursor"
          @click="updateCursor"
          @focus="focused = true"
          @blur="focused = false"
        ></textarea>
      </div>
    </div>
  </div>
</template>

<script>
/**
 * 专业 SQL 编辑器(零依赖):
 * - 实时语法高亮: 透明 textarea 叠加 highlight 层, 滚动同步
 * - 行号沟槽 + 当前行行号高亮 + 光标 Ln/Col 实时显示
 * - Tab 键 2 空格缩进; 一键格式化(关键字换行缩进)
 * - v-model 兼容, 不改变绑定值语义
 */
import { highlightSQL } from "@/utils/sqlHighlight";

const BREAK_BEFORE = [
  "FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT",
  "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL JOIN", "CROSS JOIN", "JOIN",
  "UNION ALL", "UNION", "ON", "AND", "OR",
];

export default {
  name: "SqlEditor",
  props: {
    value: { type: String, default: "" },
    title: { type: String, default: "SQL 编辑器" },
    placeholder: {
      type: String,
      default: "SELECT ... FROM ... JOIN ... WHERE ...",
    },
    rows: { type: Number, default: 8 },
  },
  data() {
    return {
      focused: false,
      cursorLine: 1,
      cursorCol: 1,
    };
  },
  computed: {
    lineCount() {
      const v = this.value || "";
      return Math.max(v.split("\n").length, 1);
    },
    charCount() {
      return (this.value || "").length;
    },
    bodyHeight() {
      return this.rows * 21 + 20 + "px";
    },
    highlightedHtml() {
      // 末尾补一个空格避免最后一行为空时高度塌陷
      return highlightSQL(this.value || "") + "\n ";
    },
  },
  methods: {
    onInput(e) {
      this.$emit("input", e.target.value);
      this.updateCursor(e);
    },
    syncScroll() {
      const ta = this.$refs.textarea;
      if (!ta) return;
      if (this.$refs.gutter) this.$refs.gutter.scrollTop = ta.scrollTop;
      if (this.$refs.highlight) {
        this.$refs.highlight.scrollTop = ta.scrollTop;
        this.$refs.highlight.scrollLeft = ta.scrollLeft;
      }
    },
    updateCursor(e) {
      const el = e && e.target ? e.target : this.$refs.textarea;
      if (!el || typeof el.selectionStart !== "number") return;
      const before = (this.value || "").slice(0, el.selectionStart);
      const lines = before.split("\n");
      this.cursorLine = lines.length;
      this.cursorCol = lines[lines.length - 1].length + 1;
    },
    onTab(e) {
      const el = e.target;
      const start = el.selectionStart;
      const end = el.selectionEnd;
      const next =
        this.value.substring(0, start) + "  " + this.value.substring(end);
      this.$emit("input", next);
      this.$nextTick(() => {
        el.selectionStart = el.selectionEnd = start + 2;
        this.updateCursor({ target: el });
      });
    },
    formatSql() {
      let sql = (this.value || "").replace(/\s+/g, " ").trim();
      if (!sql) return;
      // 关键字前换行
      for (const kw of BREAK_BEFORE) {
        const re = new RegExp("\\s+(" + kw.replace(/ /g, "\\s+") + ")\\b", "gi");
        const indent = ["AND", "OR", "ON"].includes(kw) ? "\n  " : "\n";
        sql = sql.replace(re, indent + "$1");
      }
      // SELECT 后的字段逗号换行
      sql = sql.replace(/,\s*/g, ",\n  ");
      this.$emit("input", sql);
    },
  },
};
</script>

<style scoped lang="scss">
$mono: var(--font-mono, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace);

.sql-editor {
  border: 1px solid var(--border-color, #e5eaf1);
  border-radius: 12px;
  overflow: hidden;
  background: #fdfefe;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &.is-focused {
    border-color: var(--brand, #2b5cff);
    box-shadow: 0 0 0 3px rgba(43, 92, 255, 0.09),
      0 4px 14px rgba(43, 92, 255, 0.08);
  }
}

.sql-editor__header {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  background: linear-gradient(120deg, #f6f8fc, #f2f6fb);
  border-bottom: 1px solid var(--border-color, #e5eaf1);
}

.sql-editor__dots {
  display: inline-flex;
  gap: 5px;
  margin-right: 2px;

  .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;

    &--r { background: #fca5a5; }
    &--y { background: #fcd34d; }
    &--g { background: #86efac; }
  }
}

.sql-editor__badge {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 7px;
  border-radius: 4px;
  background: var(--accent-gradient, linear-gradient(135deg, #3b82f6, #06b6d4));
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.sql-editor__title {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.sql-editor__meta {
  margin-left: auto;
  font-size: 11px;
  color: #94a3b8;
  font-family: $mono;
}

.sql-editor__meta-sep {
  margin: 0 4px;
}

.sql-editor__format {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  margin-left: 10px;
  border: 1px solid rgba(43, 92, 255, 0.25);
  border-radius: 999px;
  background: linear-gradient(135deg, #f0f5ff, #eef8ff);
  color: var(--brand, #2b5cff);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    background: linear-gradient(135deg, #e4eeff, #e2f4ff);
    box-shadow: 0 3px 8px rgba(43, 92, 255, 0.18);
    transform: translateY(-1px);
  }
}

.sql-editor__body {
  display: flex;
  align-items: stretch;
  min-height: 120px;
}

.sql-editor__gutter {
  flex-shrink: 0;
  min-width: 44px;
  padding: 10px 0;
  background: #f6f8fc;
  border-right: 1px solid var(--border-color, #e5eaf1);
  overflow: hidden;
  user-select: none;
}

.sql-editor__lineno {
  padding: 0 10px 0 6px;
  font-family: $mono;
  font-size: 12.5px;
  line-height: 21px;
  color: #b0bccb;
  text-align: right;
  transition: color 0.15s ease, background 0.15s ease;

  &.is-active {
    color: var(--brand, #2b5cff);
    font-weight: 600;
    background: rgba(43, 92, 255, 0.06);
  }
}

.sql-editor__code {
  position: relative;
  flex: 1;
  min-width: 0;
}

/* 高亮层与输入层字体完全一致以对齐 */
.sql-editor__highlight,
.sql-editor__textarea {
  margin: 0;
  padding: 10px 14px;
  font-family: $mono;
  font-size: 12.5px;
  line-height: 21px;
  tab-size: 2;
  white-space: pre;
  word-wrap: normal;
  overflow-wrap: normal;
  box-sizing: border-box;
}

.sql-editor__highlight {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  color: #334155;
  background: transparent;
}

.sql-editor__textarea {
  position: relative;
  width: 100%;
  height: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: transparent; /* 文字由高亮层渲染 */
  caret-color: var(--brand, #2b5cff);
  overflow: auto;

  &::placeholder {
    color: #b6c2d2;
    -webkit-text-fill-color: #b6c2d2;
  }
  &::selection {
    background: rgba(43, 92, 255, 0.18);
    color: transparent;
  }
}
</style>

<style lang="scss">
/* v-html 内容非 scoped, token 配色(亮色主题) */
.sql-editor__highlight {
  .sql-keyword { color: #7c3aed; font-weight: 700; }
  .sql-function { color: #0e7490; font-weight: 600; }
  .sql-string { color: #16a34a; }
  .sql-number { color: #ea580c; }
  .sql-comment { color: #94a3b8; font-style: italic; }
  .sql-operator { color: #64748b; }
}
</style>
