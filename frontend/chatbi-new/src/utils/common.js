export const RouterEnum =
{
  DATASOURCEMANAGER: { name: '数据源管理', component: 'dataSourceManager' },
  DATAMODELMANAGER: { name: '数据模型管理', component: 'dataModelManager' },
  FIELDMAPPINGMANAGER: { name: '数据表管理', component: 'fieldMappingManager' },
  METRICDATAMANAGER: { name: '指标管理', component: 'metricDataManager' },
  DIMENSIONMANAGER: { name: '维度管理', component: 'dimensionManager' },
  PORTFOLIOMANAGER: { name: '业务表', component: 'portfolioManager' },
  METRICDATAPREVIEW: { name: '指标数据预览', component: 'metricDataPreview' },
  AILOGMANAGER: { name: '日志管理', component: 'aiLogManager' },
  PROMPTMANAGER: { name: '提示词管理', component: 'promptManager' },
  TASKCONFIG: { name: '定时任务', component: 'taskConfig' },

  NEWBI: { name: '智能问数', component: 'newBI' },
}

export const szLoginLink = 'http://172.23.50.30:18080/ams-collect1-amsma-web/login'

export const toolTipOpenDelay = 500
export const toolTipPlacement = 'top'
export const toolTipEffect = 'dark'


export const RSA_PUBLIC_KEY = `MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArP9YDcoZOEm2sJixPWcL
B9+vxhDHZIajajeBmtm8qrlQVNKp9OtNCdNpZTp4/AjUF6lkp1U8h0rn9CgOGIM3
DFGU/Q1dSTnq1KSDgFv2tB6YX3XMTGTpm4sAFk5dF4GMmhMl3Nk/bF/9vf6sdEGA
c/Ngr9cLeuRjK0N3GgXnSBrsp1rOVX7DdHXrkIHPf4539H58Uc+AlZLFmdCfQpuB
eVT69dSeYbZePb84A3RGN4zZM3cM64A9KRHGgnssokrPwnz6QeVu+76zbQLKOe81
S3Doy+9Q+fD/rk+c+8OsaVJ22kQyb9oW3AEnEF+a3zX5JX2W4Ab9N2ukEHcU8geU
jQIDAQAB`


//request要用的
export function tansParams(params) {
  let result = ''
  for (const propName of Object.keys(params)) {
    const value = params[propName];
    var part = encodeURIComponent(propName) + "=";
    if (value !== null && typeof (value) !== "undefined") {
      if (typeof value === 'object') {
        for (const key of Object.keys(value)) {
          if (value[key] !== null && typeof (value[key]) !== 'undefined') {
            let params = propName + '[' + key + ']';
            var subPart = encodeURIComponent(params) + "=";
            result += subPart + encodeURIComponent(value[key]) + "&";
          }
        }
      } else {
        result += part + encodeURIComponent(value) + "&";
      }
    }
  }
  return result
}

// 验证是否为blob格式
export async function blobValidate(data) {
  try {
    const text = await data.text();
    JSON.parse(text);
    return false;
  } catch (error) {
    return true;
  }
}

//是否是手机端

export function isMobile() {
  // 服务端渲染时返回 false，避免 window 未定义
  if (typeof window === 'undefined') return false;
  const ua = navigator.userAgent.toLowerCase() || '';

  // 1. 通过 User-Agent 检测常见移动设备
  const mobileKeywords = /android|webos|iphone|ipad|ipod|blackberry|iemobile|opera mini|mobile/i;
  if (mobileKeywords.test(ua)) {
    return true;
  }

  // 2. 通过屏幕宽度判断（小于 768px 视为移动端，可根据需求调整阈值）
  if (window.innerWidth < 768) {
    return true;
  }

  // 3. 通过触摸事件支持判断（适用于部分没有移动 UA 的平板）
  if ('ontouchstart' in window || navigator.maxTouchPoints > 0) {
    // 可结合宽度进一步判断，避免误判大屏触摸设备（如 Surface）
    if (window.innerWidth < 1024) {
      return true;
    }
  }
  return false;
}

//错误码
export const errorCode = {
  '401': '认证失败，无法访问系统资源',
  '403': '当前操作没有权限',
  '404': '访问资源不存在',
  'default': '系统未知错误，请反馈给管理员'
}


Math.easeInOutQuad = function (t, b, c, d) {
  t /= d / 2
  if (t < 1) {
    return c / 2 * t * t + b
  }
  t--
  return -c / 2 * (t * (t - 2) - 1) + b
}


var requestAnimFrame = (function () {
  return window.requestAnimationFrame || window.webkitRequestAnimationFrame || window.mozRequestAnimationFrame || function (callback) { window.setTimeout(callback, 1000 / 60) }
})()

/**
 * Because it's so fucking difficult to detect the scrolling element, just move them all
 * @param {number} amount
 */
function move(amount) {
  document.documentElement.scrollTop = amount
  document.body.parentNode.scrollTop = amount
  document.body.scrollTop = amount
}

function position() {
  return document.documentElement.scrollTop || document.body.parentNode.scrollTop || document.body.scrollTop
}

export function scrollTo(to, duration, callback) {
  const start = position()
  const change = to - start
  const increment = 20
  let currentTime = 0
  duration = (typeof (duration) === 'undefined') ? 500 : duration
  var animateScroll = function () {
    // increment the time
    currentTime += increment
    // find the value with the quadratic in-out easing function
    var val = Math.easeInOutQuad(currentTime, start, change, duration)
    // move the document.body
    move(val)
    // do the animation unless its over
    if (currentTime < duration) {
      requestAnimFrame(animateScroll)
    } else {
      if (callback && typeof (callback) === 'function') {
        // the animation is done so lets callback
        callback()
      }
    }
  }
  animateScroll()
}

export function formatDate(date = new Date()) {
  const year = date.getFullYear();
  const month = ("0" + (date.getMonth() + 1)).slice(-2); // 月份是从0开始的，所以加1，并用'0'补齐为两位数
  const day = ("0" + date.getDate()).slice(-2); // 用'0'补齐为两位数
  return `${year}-${month}-${day}`; // YYYY-MM-DD 格式的日期字符串
}

export function formatDateTime(date = new Date()) {
  return (
    date.getFullYear() +
    "" +
    (date.getMonth() + 1).toString().padStart(2, "0") +
    "" +
    date.getDate().toString().padStart(2, "0") +
    "" +
    date.getHours().toString().padStart(2, "0") +
    "" +
    date.getMinutes().toString().padStart(2, "0") +
    "" +
    date.getSeconds().toString().padStart(2, "0")
  );
}

// 格式化日期
/*const formatDate = (d) => {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}*/

export function getCurrentDay(date = new Date()) {
  return {
    startStr: formatDate(date),
    endStr: formatDate(date),
  };
}

export function getLastDay(date = new Date()) {
  const yesterday = new Date(date);
  yesterday.setDate(date.getDate() - 1);

  return {
    startStr: formatDate(yesterday),
    endStr: formatDate(yesterday),
  };
}

export function getCurrentWeek(date = new Date(), startDay = 1) {
  //const now = new Date(date);
  const dayOfWeek = date.getDay();

  // 计算到本周起始日的偏移量
  let diff = dayOfWeek - startDay;
  if (diff < 0) diff += 7;

  // 本周起始日期
  const start = new Date(date);
  start.setDate(date.getDate() - diff);
  return {
    startStr: formatDate(start),
    endStr: formatDate(date),
  };
}

export function getLastWeek(date = new Date(), startDay = 1) {
  // 先获取当前周
  const currentWeek = getCurrentWeek(date, startDay);
  // 上周起始 = 本周起始 - 7天
  const start = new Date(currentWeek.startStr);
  start.setDate(start.getDate() - 7);
  // 上周结束 = 上周起始 + 6天
  const end = new Date(start);
  end.setDate(start.getDate() + 6);

  return {
    startStr: formatDate(start),
    endStr: formatDate(end),
  };
}

export function getCurrentMonth(date = new Date()) {
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  //const totalDays = new Date(year, month, 0).getDate();
  const start = new Date(`${year}-${String(month).padStart(2, "0")}-01`)
  return {
    startStr: formatDate(start),
    endStr: formatDate(date)
  };
}

export function getLastMonth(date = new Date()) {
  const now = new Date(date);

  // 获取上个月
  const lastMonth = new Date(now);
  lastMonth.setMonth(now.getMonth() - 1);

  const year = lastMonth.getFullYear();
  const month = lastMonth.getMonth() + 1; // 1-12

  // 上月最后一天
  const endmonth = new Date(year, month, 0);
  const totalDays = endmonth.getDate();

  // 格式化
  const pad = n => String(n).padStart(2, '0');
  const start = new Date(`${year}-${pad(month)}-01`)
  const end = new Date(`${year}-${pad(month)}-${pad(totalDays)}`)

  return {
    startStr: formatDate(start),
    endStr: formatDate(end)
  };
}

export function getCurrentMonths(date = new Date(), months = 3) {
  const now = new Date(date);
  const endDate = new Date(now);

  // 结束日期：当前月的最后一天
  const endYear = endDate.getFullYear();
  const endMonth = endDate.getMonth() + 1;
  const endDay = new Date(endYear, endMonth, 0).getDate();
  const endStr = `${endYear}-${String(endMonth).padStart(2, '0')}-${String(endDay).padStart(2, '0')}`;

  // 开始日期：往前推 months 个月的第一天
  const startDate = new Date(now);
  startDate.setMonth(now.getMonth() - (months - 1));
  startDate.setDate(1);

  const startYear = startDate.getFullYear();
  const startMonth = startDate.getMonth() + 1;
  const start = new Date(`${startYear}-${String(startMonth).padStart(2, '0')}-01`)

  return {
    startStr: formatDate(start),
    endStr: formatDate(date),
  };
}

export function getLastMonths(date = new Date(), months = 3) {
  const now = new Date(date);

  // 结束日期：上个月的最后一天
  const endDate = new Date(now);
  endDate.setMonth(now.getMonth());
  endDate.setDate(0); // 上个月最后一天
  const endYear = endDate.getFullYear();
  const endMonth = endDate.getMonth() + 1;
  const endDay = endDate.getDate();

  // 开始日期：往前推 months 个月的第一天
  const startDate = new Date(now);
  startDate.setMonth(now.getMonth() - months);
  startDate.setDate(1);

  const startYear = startDate.getFullYear();
  const startMonth = startDate.getMonth() + 1;

  // 格式化
  const pad = n => String(n).padStart(2, '0');
  const start = new Date(`${startYear}-${pad(startMonth)}-01`)
  const end = new Date(`${endYear}-${pad(endMonth)}-${pad(endDay)}`)

  return {
    startStr: formatDate(start),
    endStr: formatDate(end),
  };
}

export function getCurrentYear(date = new Date()) {
  const year = date.getFullYear();
  return {
    startStr: `${year}-01-01`,
    endStr: formatDate(date),
  };
}

export function getLastYear(date = new Date()) {
  const year = date.getFullYear() - 1;
  return {
    startStr: `${year}-01-01`,
    endStr: `${year}-12-31`,
  };
}

export function getCurrentThreeYear(date = new Date()) {
  const year = date.getFullYear();
  return {
    startStr: `${year - 2}-01-01`,
    endStr: `${year}-12-31`,
  };
}

export function getLastThreeYear(date = new Date()) {
  const year = date.getFullYear();
  return {
    startStr: `${year - 3}-01-01`,
    endStr: `${year - 1}-12-31`,
  };
}

export function getCurrentSeason(date = new Date()) {
  const year = date.getFullYear();
  const month = date.getMonth() + 1;

  let startMonth;
  if (month >= 1 && month <= 3) {
    startMonth = 1;
  } else if (month >= 4 && month <= 6) {
    startMonth = 4;
  } else if (month >= 7 && month <= 9) {
    startMonth = 7;
  } else if (month >= 10 && month <= 12) {
    startMonth = 10;
  }

  return {
    startStr: `${year}-${String(startMonth).padStart(2, "0")}-01`,
    endStr: formatDate(date)
  };
}


export function getLastSeason(date = new Date()) {
  const year = date.getFullYear();
  const month = date.getMonth() + 1;

  let startMonth, endMonth;
  if (month >= 1 && month <= 3) {
    startMonth = 10;
    endMonth = 12;
  } else if (month >= 4 && month <= 6) {
    startMonth = 7;
    endMonth = 9;
  } else if (month >= 7 && month <= 9) {
    startMonth = 4;
    endMonth = 6;
  } else if (month >= 10 && month <= 12) {
    startMonth = 1;
    endMonth = 3;
  }
  // 获取结束月份的最后一天
  const endDay = new Date(year, endMonth, 0).getDate();

  return {
    startStr: `${year}-${String(startMonth).padStart(2, "0")}-01`,
    endStr: `${year}-${String(endMonth).padStart(2, "0")}-${String(
      endDay
    ).padStart(2, "0")}`,
  };
}

export const shortcutDate = {
  day: [
    {
      key: "day1",
      text: "今天",
      type: "day",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("day"));
      },
    },
    {
      key: "day2",
      text: "昨天",
      type: "lastday",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastday"));
      },
    },
    {
      key: "day3",
      text: "本周",
      type: "currentweek",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentweek"));
      },
    },
    {
      key: "day4",
      text: "上周",
      type: "lastweek",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastweek"));
      },
    },
    {
      key: "day5",
      text: "本月",
      type: "currentmonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentmonth"));
      },
    },
    {
      key: "day6",
      text: "上个月",
      type: "lastmonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastmonth"));
      },
    },
  ],
  week: [
    {
      key: "week1",
      text: "本周",
      type: "currentweek",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentweek"));
      },
    },
    {
      key: "week2",
      text: "上周",
      type: "lastweek",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastweek"));
      },
    },
    {
      key: "week3",
      text: "本月",
      type: "currentmonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentmonth"));
      },
    },
    {
      key: "week4",
      text: "上个月",
      type: "lastmonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastmonth"));
      },
    },
    {
      key: "week5",
      text: "近三个月",
      type: "currentthreemonth",
      onClick: (picker) => {
        picker.$emit(
          "pick",
          handleShortcutDate("currentthreemonth")
        );
      },
    },
    {
      key: "week6",
      text: "前三个月",
      type: "lastthreemonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastthreemonth"));
      },
    },
  ],
  month: [
    {
      key: "month1",
      text: "本月",
      type: "currentmonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentmonth"));
      },
    },
    {
      key: "month2",
      text: "上个月",
      type: "lastmonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastmonth"));
      },
    },
    {
      key: "month3",
      text: "近三个月",
      type: "currentthreemonth",
      onClick: (picker) => {
        picker.$emit(
          "pick",
          handleShortcutDate("currentthreemonth")
        );
      },
    },
    {
      key: "month4",
      text: "前三个月",
      type: "lastthreemonth",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastthreemonth"));
      },
    },
    {
      key: "month5",
      text: "本年",
      type: "currentyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentyear"));
      },
    },
    {
      key: "month6",
      text: "去年",
      type: "lastyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastyear"));
      },
    },
  ],
  quarter: [
    {
      key: "quarter1",
      text: "本季度",
      type: "currentseason",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentseason"));
      },
    },
    {
      key: "quarter2",
      text: "上个季度",
      type: "lastseason",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastseason"));
      },
    },
    {
      key: "quarter3",
      text: "本年",
      type: "currentyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentyear"));
      },
    },
    {
      key: "quarter4",
      text: "去年",
      type: "lastyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastyear"));
      },
    },
  ],
  year: [
    {
      key: "year1",
      text: "本年",
      type: "currentyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentyear"));
      },
    },
    {
      key: "year2",
      text: "去年",
      type: "lastyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastyear"));
      },
    },
    {
      key: "year3",
      text: "近三年",
      type: "currentthreeyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("currentthreeyear"));
      },
    },
    {
      key: "year4",
      text: "前三年",
      type: "lastthreeyear",
      onClick: (picker) => {
        picker.$emit("pick", handleShortcutDate("lastthreeyear"));
      },
    },
  ],
}

export const logShortcutDate = [
  {
    key: "day1",
    text: "今天",
    type: "day",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("day"));
    },
  },
  {
    key: "day2",
    text: "昨天",
    type: "lastday",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("lastday"));
    },
  },
  {
    key: "day3",
    text: "本周",
    type: "currentweek",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("currentweek"));
    },
  },
  {
    key: "day4",
    text: "上周",
    type: "lastweek",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("lastweek"));
    },
  },
  {
    key: "month1",
    text: "本月",
    type: "currentmonth",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("currentmonth"));
    },
  },
  {
    key: "month2",
    text: "上个月",
    type: "lastmonth",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("lastmonth"));
    },
  },
  {
    key: "month3",
    text: "近三个月",
    type: "currentthreemonth",
    onClick: (picker) => {
      picker.$emit(
        "pick",
        handleShortcutDate("currentthreemonth")
      );
    },
  },
  {
    key: "month4",
    text: "前三个月",
    type: "lastthreemonth",
    onClick: (picker) => {
      picker.$emit("pick", handleShortcutDate("lastthreemonth"));
    },
  },
]

export function handleShortcutDate(type) {
  if (type == "day") {
    const day = getCurrentDay();
    return [new Date(day.startStr), new Date(day.endStr)];
  } else if (type == "lastday") {
    const day = getLastDay();
    return [new Date(day.startStr), new Date(day.endStr)];
  } else if (type == "currentweek") {
    const week = getCurrentWeek();
    return [new Date(week.startStr), new Date(week.endStr)];
  } else if (type == "lastweek") {
    const week = getLastWeek();
    return [new Date(week.startStr), new Date(week.endStr)];
  } else if (type == "currentmonth") {
    const month = getCurrentMonth();
    return [new Date(month.startStr), new Date(month.endStr)];
  } else if (type == "lastmonth") {
    const month = getLastMonth();
    return [new Date(month.startStr), new Date(month.endStr)];
  } else if (type == "currentthreemonth") {
    const month = getCurrentMonths();
    return [new Date(month.startStr), new Date(month.endStr)];
  } else if (type == "lastthreemonth") {
    const month = getLastMonths();
    return [new Date(month.startStr), new Date(month.endStr)];
  } else if (type == "currentyear") {
    const year = getCurrentYear();
    return [new Date(year.startStr), new Date(year.endStr)];
  } else if (type == "lastyear") {
    const year = getLastYear();
    return [new Date(year.startStr), new Date(year.endStr)];
  } else if (type == "currentthreeyear") {
    const year = getCurrentThreeYear();
    return [new Date(year.startStr), new Date(year.endStr)];
  } else if (type == "lastthreeyear") {
    const year = getLastThreeYear();
    return [new Date(year.startStr), new Date(year.endStr)];
  } else if (type == "currentseason") {
    const season = getCurrentSeason();
    return [new Date(season.startStr), new Date(season.endStr)];
  } else if (type == "lastseason") {
    const season = getLastSeason();
    return [new Date(season.startStr), new Date(season.endStr)];
  } else {
    return [new Date(), new Date()];
  }
}

