/**
 * 通用 SQL 格式化工具(零依赖)
 * - 括号深度感知:子查询 `( select ... )` 换行缩进,函数括号保持行内
 * - 主要子句关键字(FROM/WHERE/GROUP BY/ORDER BY/...)换行
 * - SELECT 字段逗号换行;AND/OR 缩进换行
 * - 字符串 / 反引号标识符 / 注释安全,不会被拆断
 * - ORDER BY 后面的内容（包括 DESC/ASC）保持在同一行
 * - LIMIT/OFFSET 单独成行
 */

const CLAUSE_KEYWORDS = [
  'select distinct',
  'select',
  'from',
  'where',
  'group by',
  'order by',
  'having',
  'limit',
  'offset',
  'union all',
  'union',
  'left outer join',
  'right outer join',
  'full outer join',
  'left join',
  'right join',
  'inner join',
  'cross join',
  'join',
];

const SUB_KEYWORDS = ['and', 'or', 'on'];

const INDENT = '  ';

/** 词法切分:字符串/反引号/注释/括号/逗号/单词 */
function tokenize(sql) {
  const tokens = [];
  let i = 0;
  const n = sql.length;
  while (i < n) {
    const ch = sql[i];
    // 行注释
    if (ch === '-' && sql[i + 1] === '-') {
      let j = i;
      while (j < n && sql[j] !== '\n') j++;
      tokens.push({ type: 'comment', text: sql.slice(i, j) });
      i = j;
      continue;
    }
    // 块注释
    if (ch === '/' && sql[i + 1] === '*') {
      let j = sql.indexOf('*/', i + 2);
      j = j === -1 ? n : j + 2;
      tokens.push({ type: 'comment', text: sql.slice(i, j) });
      i = j;
      continue;
    }
    // 字符串
    if (ch === "'" || ch === '"') {
      let j = i + 1;
      while (j < n) {
        if (sql[j] === '\\') { j += 2; continue; }
        if (sql[j] === ch) { j++; break; }
        j++;
      }
      tokens.push({ type: 'string', text: sql.slice(i, j) });
      i = j;
      continue;
    }
    // 反引号标识符
    if (ch === '`') {
      let j = sql.indexOf('`', i + 1);
      j = j === -1 ? n : j + 1;
      tokens.push({ type: 'ident', text: sql.slice(i, j) });
      i = j;
      continue;
    }
    if (ch === '(') { tokens.push({ type: 'lparen', text: '(' }); i++; continue; }
    if (ch === ')') { tokens.push({ type: 'rparen', text: ')' }); i++; continue; }
    if (ch === ',') { tokens.push({ type: 'comma', text: ',' }); i++; continue; }
    if (/\s/.test(ch)) { i++; continue; }
    // 普通词 / 运算符
    let j = i;
    while (j < n && !/[\s(),'"`]/.test(sql[j]) && !(sql[j] === '-' && sql[j + 1] === '-')) j++;
    tokens.push({ type: 'word', text: sql.slice(i, j) });
    i = j;
  }
  return tokens;
}

/** 向前看:匹配多词关键字,返回匹配到的关键字与消费的 token 数 */
function matchKeyword(tokens, idx, list) {
  const lower = (k) => tokens[k] && tokens[k].type === 'word' ? tokens[k].text.toLowerCase() : null;
  for (const kw of list) {
    const parts = kw.split(' ');
    let ok = true;
    for (let p = 0; p < parts.length; p++) {
      if (lower(idx + p) !== parts[p]) { ok = false; break; }
    }
    if (ok) return { kw, consumed: parts.length };
  }
  return null;
}

/** 下一个非注释 token */
function nextMeaningful(tokens, idx) {
  for (let k = idx; k < tokens.length; k++) {
    if (tokens[k].type !== 'comment') return tokens[k];
  }
  return null;
}

/**
 * 格式化 SQL(若已含较多换行则视为已手工排版,原样返回)
 * @param {string} sql
 * @param {boolean} force 强制重排
 */
export function formatSQL(sql, force = false) {
  if (!sql || typeof sql !== 'string') return sql || '';
  const trimmed = sql.trim();
  if (!force) {
    // 已有多行且无超长行,认为已手工排版,原样返回
    const lines = trimmed.split('\n').filter((l) => l.trim());
    const maxLen = lines.reduce((m, l) => Math.max(m, l.length), 0);
    if (lines.length >= 4 && maxLen <= 120) return trimmed;
  }

  const tokens = tokenize(trimmed.replace(/\s+/g, ' '));
  const out = [];
  let line = '';
  // 括号栈:'sub' 子查询 / 'fn' 函数
  const stack = [];
  const depth = () => stack.filter((s) => s === 'sub').length;
  const inFn = () => stack.length > 0 && stack[stack.length - 1] === 'fn';

  const pushLine = () => {
    if (line.trim()) out.push(line.replace(/\s+$/, ''));
    line = '';
  };
  const newline = (extra = 0) => {
    pushLine();
    line = INDENT.repeat(depth() + extra);
  };
  const append = (text) => {
    if (line === '' || /^\s*$/.test(line)) {
      line += text;
    } else if (/[(.]$/.test(line) || text === '.' || text.startsWith('.')) {
      line += text;
    } else if (text === ')' || text === ',') {
      line += text;
    } else {
      line += ' ' + text;
    }
  };

  let i = 0;
  let started = false;
  while (i < tokens.length) {
    const t = tokens[i];

    if (t.type === 'lparen') {
      const nxt = nextMeaningful(tokens, i + 1);
      const isSub = nxt && nxt.type === 'word' && nxt.text.toLowerCase() === 'select';
      if (isSub) {
        append('(');
        stack.push('sub');
        newline();
      } else {
        line += line === '' || /[\s(]$/.test(line) ? '(' : '(';
        stack.push('fn');
      }
      i++;
      continue;
    }

    if (t.type === 'rparen') {
      const kind = stack.pop();
      if (kind === 'sub') {
        newline();
        line += ')';
      } else {
        line += ')';
      }
      i++;
      continue;
    }

    if (t.type === 'comma') {
      if (inFn()) {
        line += ',';
      } else {
        line += ',';
        newline(1);
      }
      i++;
      continue;
    }

    if (t.type === 'word') {
      const clause = matchKeyword(tokens, i, CLAUSE_KEYWORDS);
      if (clause) {
        if (started) newline();
        append(clause.kw.toUpperCase());
        started = true;
        
        // 根据不同关键字处理后续内容的换行
        if (clause.kw.startsWith('select')) {
          // SELECT 后字段起新行缩进
          newline(1);
        } else if (clause.kw === 'limit' || clause.kw === 'offset') {
          // limit/offset 值保持同一行，不额外换行
          // 这里什么都不做
        } else if (clause.kw === 'order by') {
          // ORDER BY 后面的内容（字段名、ASC/DESC）保持在同一行
          // 不额外换行，让后续内容自然追加到同一行
        }
        // 其他关键字（FROM, WHERE, GROUP BY, HAVING 等）默认换行后追加内容
        
        i += clause.consumed;
        continue;
      }
      
      const sub = matchKeyword(tokens, i, SUB_KEYWORDS);
      if (sub && !inFn()) {
        newline(1);
        append(sub.kw.toUpperCase());
        i += sub.consumed;
        continue;
      }
    }

    append(t.text);
    started = true;
    i++;
  }
  pushLine();
  return out.join('\n');
}

export default formatSQL;