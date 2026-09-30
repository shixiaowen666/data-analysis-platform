// cron-validator.js

/**
 * 校验 Cron 表达式（支持 5、6、7 个字段）
 * 5 字段：分 时 日 月 周        （Linux crontab）
 * 6 字段：秒 分 时 日 月 周      （Spring / Quartz）
 * 7 字段：秒 分 时 日 月 周 年   （Quartz）
 *
 * @param {string} expression Cron 表达式
 * @returns {{ valid: boolean, message: string }}
 */

// 月份英文缩写（Quartz / Linux 通用）
const MONTH_MAP = {
  JAN: 1, FEB: 2, MAR: 3, APR: 4, MAY: 5, JUN: 6,
  JUL: 7, AUG: 8, SEP: 9, OCT: 10, NOV: 11, DEC: 12
};

// 星期英文缩写（Quartz：1=SUN … 7=SAT）
const WEEK_MAP = {
  SUN: 1, MON: 2, TUE: 3, WED: 4, THU: 5, FRI: 6, SAT: 7
};

// 各字段取值范围（Quartz 语义）
const RANGES = {
  second: [0, 59],
  minute: [0, 59],
  hour: [0, 23],
  day: [1, 31],
  month: [1, 12],
  week: [1, 7],      // Quartz：1=SUN … 7=SAT
  year: [1970, 2099]
};

// 字段中文名
const CN = {
  second: '秒', minute: '分', hour: '时',
  day: '日', month: '月', week: '周', year: '年'
};

export function validateCron(expression) {
  if (expression === undefined || expression === null) {
    return { valid: false, message: 'Cron 表达式不能为空' };
  }
  if (typeof expression !== 'string') {
    return { valid: false, message: 'Cron 表达式必须是字符串' };
  }

  const expr = expression.trim().replace(/\s+/g, ' ');
  if (expr === '') {
    return { valid: false, message: 'Cron 表达式不能为空' };
  }

  const fields = expr.split(' ');

  // 字段数量校验
  if (![5, 6, 7].includes(fields.length)) {
    return {
      valid: false,
      message: `Cron 表达式应为 5、6 或 7 个字段，当前为 ${fields.length} 个`
    };
  }

  // 根据字段数量确定各位置对应的字段名
  let fieldNames;
  if (fields.length === 5) {
    fieldNames = ['minute', 'hour', 'day', 'month', 'week'];
  } else if (fields.length === 6) {
    fieldNames = ['second', 'minute', 'hour', 'day', 'month', 'week'];
  } else {
    fieldNames = ['second', 'minute', 'hour', 'day', 'month', 'week', 'year'];
  }

  // 逐字段校验
  for (let i = 0; i < fields.length; i++) {
    const name = fieldNames[i];
    const value = fields[i];
    const [min, max] = RANGES[name];

    const result = validateField(value, min, max, name);
    if (!result.valid) {
      return {
        valid: false,
        message: `${CN[name]}字段（${value}）${result.message}`
      };
    }
  }

  // 日和周不能同时被指定（Quartz 规则）
  if (fields.length >= 6) {
    const dayIndex = fieldNames.indexOf('day');
    const weekIndex = fieldNames.indexOf('week');
    const dayVal = fields[dayIndex];
    const weekVal = fields[weekIndex];
    if (dayVal !== '?' && weekVal !== '?' && dayVal !== '*' && weekVal !== '*') {
      return {
        valid: false,
        message: '“日”和“周”字段不能同时指定具体值，其中一个应使用 ?'
      };
    }
  }

  return { valid: true, message: '校验通过' };
}

/**
 * 校验单个字段
 */
function validateField(field, min, max, name) {
  if (field === '' || field === undefined) {
    return { valid: false, message: '不能为空' };
  }

  // 允许：数字、字母（英文月份/星期）、* ? , - / L W #
  if (!/^[A-Za-z\d*?,\-/LW#]+$/.test(field)) {
    return { valid: false, message: '包含非法字符' };
  }

  // 拆成用逗号分隔的多个部分
  const parts = field.split(',');
  for (const part of parts) {
    if (part === '') {
      return { valid: false, message: '存在空的枚举项（逗号分隔异常）' };
    }
    const res = validatePart(part, min, max, name);
    if (!res.valid) return res;
  }
  return { valid: true };
}

/**
 * 把英文缩写或数字统一转成数字
 */
function toNum(value, name) {
  const upper = String(value).toUpperCase();
  if (name === 'month' && MONTH_MAP[upper] !== undefined) {
    return MONTH_MAP[upper];
  }
  if (name === 'week' && WEEK_MAP[upper] !== undefined) {
    return WEEK_MAP[upper];
  }
  return parseInt(value, 10);
}

/**
 * 校验逗号分隔后的单个部分
 */
function validatePart(part, min, max, name) {
  // * 和 ?
  if (part === '*' || part === '?') {
    return { valid: true };
  }

  // 英文月份 / 星期
  const upper = part.toUpperCase();
  if (name === 'month' && MONTH_MAP[upper] !== undefined) {
    return { valid: true };
  }
  if (name === 'week' && WEEK_MAP[upper] !== undefined) {
    return { valid: true };
  }

  // L / W（仅日和周允许）
  if (/^L$/i.test(part)) {
    if (name !== 'day' && name !== 'week') {
      return { valid: false, message: '不允许使用 L' };
    }
    return { valid: true };
  }
  if (/^\d+W$/i.test(part)) {
    if (name !== 'day') {
      return { valid: false, message: 'W 只能用于“日”字段' };
    }
    const num = parseInt(part, 10);
    if (num < 1 || num > 31) {
      return { valid: false, message: 'W 前的数字应在 1-31 之间' };
    }
    return { valid: true };
  }
  if (/^L-\d+$/i.test(part)) {
    if (name !== 'day') {
      return { valid: false, message: 'L-n 只能用于“日”字段' };
    }
    return { valid: true };
  }

  // # 用于周：如 6#3、MON#3
  if (part.includes('#')) {
    if (name !== 'week') {
      return { valid: false, message: '# 只能用于“周”字段' };
    }
    const [w, n] = part.split('#');
    const wn = toNum(w, 'week');
    const nn = parseInt(n, 10);
    if (isNaN(wn) || wn < 1 || wn > 7) {
      return { valid: false, message: '星期值应在 1-7 之间' };
    }
    if (isNaN(nn) || nn < 1 || nn > 5) {
      return { valid: false, message: '# 后的序号应在 1-5 之间' };
    }
    return { valid: true };
  }

  // 步长：如 0/5、*/5、1-10/2
  if (part.includes('/')) {
    const segs = part.split('/');
    if (segs.length !== 2) {
      return { valid: false, message: '步长格式错误，应为 起始/步长' };
    }
    const [startStr, stepStr] = segs;
    const step = parseInt(stepStr, 10);
    if (isNaN(step) || step <= 0) {
      return { valid: false, message: '步长必须是正整数' };
    }
    // 起始部分可以是 * 或数值或范围
    if (startStr === '*') {
      return { valid: true };
    }
    if (startStr.includes('-')) {
      return validateRange(startStr, min, max, name);
    }
    const start = toNum(startStr, name);
    if (isNaN(start) || start < min || start > max) {
      return { valid: false, message: `起始值应在 ${min}-${max} 之间` };
    }
    return { valid: true };
  }

  // 范围：如 1-5、MON-FRI
  if (part.includes('-')) {
    return validateRange(part, min, max, name);
  }

  // 纯数字或英文缩写
  const num = toNum(part, name);
  if (!isNaN(num)) {
    if (num < min || num > max) {
      return { valid: false, message: `数值应在 ${min}-${max} 之间` };
    }
    return { valid: true };
  }

  return { valid: false, message: '格式不正确' };
}

/**
 * 校验范围，如 1-5、MON-FRI、JAN-MAR
 */
function validateRange(rangeStr, min, max, name) {
  const [a, b] = rangeStr.split('-');
  const start = toNum(a, name);
  const end = toNum(b, name);
  if (isNaN(start) || isNaN(end)) {
    return { valid: false, message: '范围格式不正确' };
  }
  if (start < min || start > max || end < min || end > max) {
    return { valid: false, message: `范围应在 ${min}-${max} 之间` };
  }
  if (start > end) {
    return { valid: false, message: '范围的起始值不能大于结束值' };
  }
  return { valid: true };
}