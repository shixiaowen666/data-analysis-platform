/**
 * 轻量 SQL 语法高亮 (零依赖, 正则实现)
 * 输出带 <span class="sql-*"> 的 HTML, 配合亮色代码主题使用
 */

const KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'GROUP', 'BY', 'ORDER', 'HAVING', 'LIMIT', 'OFFSET',
  'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'FULL', 'CROSS', 'ON', 'AS', 'AND',
  'OR', 'NOT', 'IN', 'IS', 'NULL', 'LIKE', 'BETWEEN', 'EXISTS', 'UNION', 'ALL',
  'DISTINCT', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'INSERT', 'INTO', 'VALUES',
  'UPDATE', 'SET', 'DELETE', 'CREATE', 'TABLE', 'VIEW', 'DROP', 'ALTER', 'ADD',
  'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'INDEX', 'ASC', 'DESC', 'WITH',
  'PARTITION', 'OVER', 'ROW_NUMBER', 'RANK', 'DENSE_RANK', 'CAST', 'CONVERT',
  'TOP', 'PERCENT', 'INTERVAL', 'EXTRACT', 'TRUNCATE', 'DESCRIBE', 'SHOW', 'USE',
];

const FUNCTIONS = [
  'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'ROUND', 'FLOOR', 'CEIL', 'CEILING', 'ABS',
  'COALESCE', 'NULLIF', 'IFNULL', 'NVL', 'CONCAT', 'SUBSTRING', 'SUBSTR', 'TRIM',
  'UPPER', 'LOWER', 'LENGTH', 'REPLACE', 'NOW', 'CURDATE', 'CURRENT_DATE',
  'CURRENT_TIMESTAMP', 'DATE_FORMAT', 'DATE_ADD', 'DATE_SUB', 'DATEDIFF',
  'YEAR', 'MONTH', 'DAY', 'HOUR', 'MINUTE', 'SECOND', 'TO_CHAR', 'TO_DATE',
  'DECODE', 'GREATEST', 'LEAST', 'IF', 'GROUP_CONCAT', 'STDDEV', 'VARIANCE',
];

const KW_SET = new Set(KEYWORDS);
const FN_SET = new Set(FUNCTIONS);

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * 高亮一段 SQL 文本, 返回 HTML 字符串
 */
export function highlightSQL(sql) {
  if (!sql) return '';
  const src = String(sql);
  let out = '';
  let i = 0;
  const n = src.length;

  while (i < n) {
    const ch = src[i];

    // 行注释 --
    if (ch === '-' && src[i + 1] === '-') {
      let j = src.indexOf('\n', i);
      if (j === -1) j = n;
      out += `<span class="sql-comment">${escapeHtml(src.slice(i, j))}</span>`;
      i = j;
      continue;
    }
    // 块注释 /* */
    if (ch === '/' && src[i + 1] === '*') {
      let j = src.indexOf('*/', i + 2);
      j = j === -1 ? n : j + 2;
      out += `<span class="sql-comment">${escapeHtml(src.slice(i, j))}</span>`;
      i = j;
      continue;
    }
    // 字符串 ' 或 "
    if (ch === "'" || ch === '"') {
      let j = i + 1;
      while (j < n) {
        if (src[j] === ch && src[j + 1] === ch) { j += 2; continue; } // 转义 ''
        if (src[j] === ch) break;
        j++;
      }
      j = Math.min(j + 1, n);
      out += `<span class="sql-string">${escapeHtml(src.slice(i, j))}</span>`;
      i = j;
      continue;
    }
    // 反引号标识符
    if (ch === '`') {
      let j = src.indexOf('`', i + 1);
      j = j === -1 ? n : j + 1;
      out += `<span class="sql-ident">${escapeHtml(src.slice(i, j))}</span>`;
      i = j;
      continue;
    }
    // 数字
    if (/[0-9]/.test(ch) && !/[A-Za-z0-9_$\u4e00-\u9fa5]/.test(src[i - 1] || '')) {
      let j = i;
      while (j < n && /[0-9.]/.test(src[j])) j++;
      out += `<span class="sql-number">${escapeHtml(src.slice(i, j))}</span>`;
      i = j;
      continue;
    }
    // 单词 (关键字 / 函数 / 标识符)
    if (/[A-Za-z_$\u4e00-\u9fa5]/.test(ch)) {
      let j = i;
      while (j < n && /[A-Za-z0-9_$\u4e00-\u9fa5]/.test(src[j])) j++;
      const word = src.slice(i, j);
      const up = word.toUpperCase();
      if (KW_SET.has(up)) {
        out += `<span class="sql-keyword">${escapeHtml(word)}</span>`;
      } else if (FN_SET.has(up) && src[j] === '(') {
        out += `<span class="sql-function">${escapeHtml(word)}</span>`;
      } else {
        out += escapeHtml(word);
      }
      i = j;
      continue;
    }

    out += escapeHtml(ch);
    i++;
  }
  return out;
}

/**
 * 高亮并按行拆分, 用于渲染带行号的代码块
 * @returns {Array<string>} 每行的高亮 HTML
 */
export function highlightSQLLines(sql) {
  if (!sql) return [''];
  // 按行分别高亮, 保证行内 span 完整闭合
  return String(sql).replace(/\r\n/g, '\n').split('\n').map((line) => highlightSQL(line) || '&nbsp;');
}

export default { highlightSQL, highlightSQLLines };
