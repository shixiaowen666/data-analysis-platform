import { filterTypeEnum } from '@/api/metricDataPreview/metricPreviewAPI.js';
import { isMultiValueOperator, getChatStepAPI } from "@/api/newBI/newBIAPI.js";
import { connectChatWebSocket } from "@/utils/chatWebSocket.js";
import toast from '@/utils/toast';
import {shortcutDate} from '@/utils/common';
import { getDimensionValuesAPI } from "@/api/dimensionManager/dimensionAPI.js";

/**
 * 会话数据解析（重写原 praseAnswer / praseQueryAnswer / praseComputeAnswer）
 * 输出「视图模型」，与模板解耦：
 *
 * Turn = {
 *   chatId,
 *   user: { text, time },
 *   think: { title, text, done } | null,
 *   steps: [ QueryStep | ComputeStep | AnalyzeStep | SummaryStep ]
 * }
 */

let seq = 0;
export const nextUid = () => `u${Date.now().toString(36)}${(seq += 1)}`;
export const PAGE_SIZE = 10;

/* ------------------------------------------------------------------ */
/* 空模型构造                                                          */
/* ------------------------------------------------------------------ */

export function createTurn(chatId, startedAt) {
  return {
    uid: nextUid(),
    chatId,
    user: null,
    think: null,
    steps: [],
    /** 轮次发生时间（毫秒），用于会话流中的日期/时间分隔线 */
    startedAt: startedAt || Date.now(),
    status: 'running', // running | done | error | stopped
    roundNumber: 1,
    seconds: 0,
  };
}

/* ------------------------------------------------------------------ */
/* 时间分隔线                                                          */
/* ------------------------------------------------------------------ */

const DAY = 86400000;

function dayStart(ts) {
  const d = new Date(ts);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function pad(n) {
  return String(n).padStart(2, '0');
}

/**
 * 生成轮次上方的时间胶囊文案。
 * 规则：首轮必显；跨天必显；同一天内间隔 > 10 分钟才显示（避免每条都插分隔线）。
 * @returns {string} 空字符串表示不显示分隔线
 */
export function turnDivider(turn, prev) {
  const ts = turn && turn.startedAt;
  if (!ts) return '';

  const d = new Date(ts);
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;

  if (prev && prev.startedAt) {
    const sameDay = dayStart(ts) === dayStart(prev.startedAt);
    if (sameDay) {
      // 同天且间隔较近 → 不插分隔线，保持会话流紧凑
      return ts - prev.startedAt < 10 * 60 * 1000 ? '' : hm;
    }
  }

  const today = dayStart(Date.now());
  const day = dayStart(ts);
  if (day === today) return `今天 ${hm}`;
  if (day === today - DAY) return `昨天 ${hm}`;
  if (d.getFullYear() === new Date().getFullYear()) {
    return `${d.getMonth() + 1}月${d.getDate()}日 ${hm}`;
  }
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${hm}`;
}

function defaultStart() {
  const d = new Date();
  d.setDate(d.getDate() - 29);
  return fmt(d);
}
function defaultEnd() {
  return fmt(new Date());
}
export function fmt(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

//创建空的每一步
export function createStep(stepType, itemId) {
  const base = {
    uid: nextUid(),
    itemId: Number(itemId) || 0,
    stepType,
    title: '',
    status: 'running', // running | done | error
    open: true,//移动端用
    openPanel: [],
    error: '',
  };

  if (stepType === 'query') {
    return {
      ...base,
      viewSql: '',
      loadingData: false,//加载数据的进度
      loadingCandidate: false,//加载可选的进度
      loadingFilterMeta: false,//添加筛选器时，维度是下拉时候的进度
      dirty: false,//是否有变化
      toolbarOpen: false,
      dims: [], // { id, key, name }//已选维度--初始化时可能会有
      metrics: [],//已选指标--初始化时可能会有
      candidateDims: [],//可选维度--每次要后台查询
      candidateMetrics: [],//可选指标--每次要后台查询
      chartType: 1,//展现形式，目前只有表格，其他待开发
      filters: [], // { uid, type, key, name, operator, value, options, loadingOptions }
      granularity: 'day',
      dateRange: [defaultStart(), defaultEnd()],
      data: emptyData(),
      highlightKeys: [],//移动端专用
      queryParams: null,
      pickerOptions: {
        shortcuts: []
      },//日期选择快捷键
    };
  }
  if (stepType === 'compute') {
    return { ...base, data: emptyData() };
  }
  if (stepType === 'analyze' || stepType === 'summarize') {
    return { ...base, answer: '', streaming: false, expanded: false };
  }
  return base;
}

function emptyData() {
  return { columns: [], records: [], page: 1, pageSize: PAGE_SIZE, total: 0, totalPage: 0 };
}

/* ------------------------------------------------------------------ */
/* 解析 query 步骤                                                     */
/* ------------------------------------------------------------------ */

export async function fillQueryStep(step, itemInfo) {
  step.title = itemInfo.question || step.title;

  const chart = itemInfo.chartData || {};
  const req = chart.dataRequestDTO || {};
  const vo = chart.dataVO || {};

  // 维度 / 指标
  step.dims = (req.dimList || []).map((d) => ({
    id: d.id,
    key: d.dimKey,
    name: d.dimName,
  }));
  step.metrics = (req.indexList || []).map((m) => ({
    id: m.id,
    key: m.indKey,
    name: m.indName,
  }));

  // 时间
  if (req.timeRange && req.timeRange.start) {
    step.dateRange = [req.timeRange.start, req.timeRange.end];
  }
  step.granularity = req.dateGranularity || 'day';
  step.pickerOptions.shortcuts = shortcutDate[step.granularity];


  // 筛选器（把后端 filters 转为可编辑模型）
  //step.filters = (req.filters || []).map((f) => {
  step.filters = [];
  for (const f of (req.filters || [])) {
    const field = f.filterField || {};
    const isDim = field.fieldClazz === 0 || field.fieldClazz === undefined;
    const dimMeta = step.dims.find((d) => d.key === field.key);
    const metMeta = step.metrics.find((m) => m.key === field.key);
    const meta = dimMeta || metMeta;
    let value = f.filterValue;
    if (isMultiValueOperator(f.operator)) {
      value = Array.isArray(value) ? value : value ? [value] : [];
    } else {
      value = Array.isArray(value) ? value[0] || '' : value == null ? '' : value;
    }
    const options = await loadFilterValues(
      isDim ? filterTypeEnum.dim.value : filterTypeEnum.metric.value, 
      meta ? meta.id : null
    );

    step.filters.push ({
      uid: nextUid(),
      type: isDim ? filterTypeEnum.dim.value : filterTypeEnum.metric.value,
      id: meta ? meta.id : null,
      key: field.key,
      name: field.name || (meta ? meta.name : field.key),
      operator: f.operator,
      value,
      options,
      loadingOptions: false,
    });
  };

  // 数据
  const d = vo.data;
  if (vo.code === 200 && d) {
    const records = d.records || [];
    const columns = (d.columns || []).map((c) => ({
      key: c.key,
      name: c.name,//prettyColName(c),
      unit: c.unit || '',
      type: c.type,// === 'measure' ? 'indicator' : 'dimension',
    }));
    step.data = {
      columns: columns,//d.columns || [],
      records: records.slice(0, PAGE_SIZE),
      allRecords: records,
      page: 1,
      pageSize: PAGE_SIZE,
      total: d.total != null ? d.total : records.length,
      totalPage: Math.max(1, Math.ceil((d.total != null ? d.total : records.length) / PAGE_SIZE)),
    };
    step.viewSql = d.sql || '';
  } else {
    step.error = (vo.message && String(vo.message)) || '数据查询失败';
    step.data = emptyData();
  }
  step.dirty = false;
  return step;
}

export async function loadFilterValues(type,id) {
      
      if (type !== filterTypeEnum.dim.value || !id) return;
      
      try {
        const res = await getDimensionValuesAPI(id);
        if (res && res.code === 200) return res.data || [];
      } catch (e) {
        /* handled */
      } finally {

      }
      return []
    }
/* ------------------------------------------------------------------ */
/* 解析 compute 步骤                                                   */
/* ------------------------------------------------------------------ */

export function fillComputeStep(step, itemInfo) {
  step.title = itemInfo.question || step.title;
  const vo = (itemInfo.chartData || {}).dataVO || {};
  const rows = vo.rows || [];
  const columns = (vo.columns || []).map((c) => ({
    key: c.name,
    name: c.name,//prettyColName(c),
    cn_name: c.cn_name,
    unit: c.unit || '',
    type: c.role === 'measure' ? 'indicator' : 'dimension',
  }));

  step.data = {
    columns,
    records: rows.slice(0, PAGE_SIZE),
    allRecords: rows,
    page: 1,
    pageSize: PAGE_SIZE,
    total: rows.length,
    totalPage: Math.max(1, Math.ceil(rows.length / PAGE_SIZE)),
  };
  return step;
}


function prettyColName(c) {
  if (c.description && /[\u4e00-\u9fa5]/.test(c.description) && c.description.length <= 10) {
    return c.description;
  }
  return c.name;
}

export function repaginate(step) {
  const { page, pageSize } = step.data;
  const src = step.allRecords || step.data.allRecords || [];
  const start = (page - 1) * pageSize;
  step.data.records = src.slice(start, start + pageSize);
}

/* ------------------------------------------------------------------ */
/* 解析完整历史（一个 chatInfo → 一个 Turn）                            */
/* ------------------------------------------------------------------ */

export function parseTurn(chatInfo) {
  const turn = createTurn(chatInfo.chatId);
  turn.status = 'done';

  (chatInfo.chatItemInfo || []).forEach((item) => {
    if (item.type === 'user') {
      turn.user = { text: item.question, time: item.updateTime };
      // 历史轮次的时间以用户提问时间为准（用于时间分隔线）
      const ts = Date.parse(String(item.updateTime || '').replace(/-/g, '/'));
      if (!Number.isNaN(ts)) turn.startedAt = ts;
      return;
    }

    // think 挂在 ai item 上
    if (item.think && item.think.message && !turn.think) {
      turn.think = {
        title: `已推理思考(${item.think.speed || 0}s)`,
        text: item.think.message,
        done: true,
        open: false,
        openPanel: [],//电脑端用
      };
    }
    
    if (item.think && item.think.message && turn.think){
      if (item.think.message.endsWith('\n')) {
        turn.think.text = item.think.message.slice(0, -1);
      }else{
        turn.think.text = item.think.message
      }
    }

    const stepType = item.stepType;
    if (!['query', 'compute', 'analyze', 'summarize'].includes(stepType)) return;

    const step = createStep(stepType, item.itemId);
    step.status = 'done';
    step.open = stepType === 'summarize'; // 历史默认只展开总结

    if (stepType === 'query') {
      fillQueryStep(step, item);
    } else if (stepType === 'compute') {
      fillComputeStep(step, item);
    } else if (stepType === 'analyze') {
      step.title = item.question;
      const vo = (item.chartData || {}).dataVO || {};
      const rows = vo.rows || [];
      step.answer = rows.length ? rows[0].analysis_result || '' : '';
    } else if (stepType === 'summarize') {
      step.title = item.question;
      const vo = (item.chartData || {}).dataVO || {};
      const rows = vo.rows || [];
      step.answer = rows.length ? rows[0].answer || '' : '';
    }

    turn.steps.push(step);
  });

  turn.steps.sort((a, b) => a.itemId - b.itemId);
  return turn;
}

/* ------------------------------------------------------------------ */
/* 构造 getdata 请求体                                                 */
/* ------------------------------------------------------------------ */

export function buildDataPayload(step, { page, pageSize, downloadFlag = 0 } = {}) {
  return {
    downloadFlag,
    dimList: step.dims.map((d) => ({ id: d.id, dimKey: d.key, dimName: d.name })),
    indexList: step.metrics.map((m) => ({ id: m.id, indKey: m.key, indName: m.name })),
    filters: step.filters
      .filter((f) => f.operator !== '' && f.operator !== null && f.operator !== undefined)
      .map((f) => ({
        filterField: { fieldClazz: f.type, name: f.name, key: f.key },
        filterValue: f.value,
        operator: f.operator,
        logicType: 0,
      })),

        timeRange: { start: step.dateRange?step.dateRange[0]:'', end: step.dateRange? step.dateRange[1]:'' },
      
    dateGranularity: step.granularity,
    chartType: 1,
    page: page || step.data.page,
    pageSize: pageSize || step.data.pageSize,
    total: step.data.total,
  };
}

/* ------------------------------------------------------------------ */
/* 历史会话分组（重写原 6 段复制粘贴）                                  */
/* ------------------------------------------------------------------ */

export const HISTORY_BUCKETS = [
  { key: 'today', text: '今天' },
  { key: 'yesterday', text: '昨天' },
  { key: 'lastWeek', text: '近一周' },
  { key: 'lastMonth', text: '近一月' },
  { key: 'lastSixMonth', text: '近半年' },
  { key: 'moreThanSixMonth', text: '更早' },
];

export function groupHistory(data, agentCode) {
  return HISTORY_BUCKETS.map((b) => {
    const records = (data[b.key] || []).filter((r) => !agentCode || r.aiBodyCode === agentCode);
    return {
      id: b.key,
      text: b.text,
      children: records.map((r) => ({
        id: r.chatSessionId,
        text: r.chatName,
        desc: r.updatedAt,
      })),
    };
  }).filter((g) => g.children.length);
}

function clearSpace(s){
  return s.replace(/ /g, '')
}

export function onStreamMessage(msg, sid, turn, maybeFollow) {
  if (!msg || typeof msg !== 'object') return;
  if (msg.metadata && msg.metadata.close) return;

  const rid = msg.request_id;
  if (!rid || rid.split('@')[0] !== sid) return;

  //新轮次，删除之前的step
  let roundNumber = 1
  if(msg.event_index) roundNumber = Number(msg.event_index);
  if(roundNumber > turn.roundNumber){
    turn.roundNumber = roundNumber
    turn.steps = []
    //turn.think = null
  }

  const stepType = msg.step_type;
  const eventType = msg.event_type;
  const message = msg.message;
  if (!stepType || stepType === 'close') return;

  //this.currentStepType = stepType;

  /* ---- think ---- */
  if (stepType === 'think') {
    if (!turn.think) turn.think = { title: '', text: '', done: false, open: true, openPanel: [] };
    if (eventType === 'title') turn.think.title = message;
    else if (eventType === 'line') turn.think.text += message;
    else if (eventType === 'token') turn.think.text += message;
    else if (eventType === 'done') {
      turn.think.done = true;
      turn.think.open = false; // 完成后自动收起
      turn.think.openPanel = []
      if(turn.think.text) turn.think.text = turn.think.text.slice(0, -1);
    }
    
    maybeFollow()

    //this.maybeFollow();
    return;
  }

  /* ---- 业务步骤 ---- */
  const itemId = Number(String(msg.step_id || '').split('_')[1] || 0);
  let step = turn.steps.find((s) => s.itemId === itemId && s.stepType === stepType);
  if (!step) {
    step = createStep(stepType, itemId);
    if (stepType === 'summarize' || stepType === 'analyze') {
      step.openPanel = [`${stepType}${turn.chatId}${step.itemId}`]
      step.open = true;
    } else {
      step.open = false;
    }

    turn.steps.push(step);
    turn.steps.sort((a, b) => a.itemId - b.itemId);
  }

  if (eventType === 'title') step.title = message;
  else if (eventType === 'token') step.answer = (step.answer || '') + message;
  else if (eventType === 'error') {
    step.status = 'error';
    step.error = message || '执行失败';
  } else if (eventType === 'done') {
    step.status = 'done';
    if (stepType === 'query' || stepType === 'compute') {
      fetchStepDetail(step, sid, turn.chatId, itemId, maybeFollow);
      //step.open = false;
    } else if (stepType === 'analyze') {
      step.open = false; // 分析完成后收起，聚焦总结
    }
  }

  if(stepType === 'analyze'){
      const container = document.getElementById(`analyzePanel${turn.chatId}${step.itemId}`);
      if(container) {
        container.scrollTop = container.scrollHeight;
      }
  }
  if(stepType === 'summarize'){
      const container = document.getElementById(`summarizePanel${turn.chatId}${step.itemId}`);
      if(container) {
        container.scrollTop = container.scrollHeight;
      }
  }
  maybeFollow()
  //this.maybeFollow();
}

export async function fetchStepDetail(step, sid, chatId, itemId, maybeFollow) {
  step.loadingData = true;
  try {
    const res = await getChatStepAPI({ chatSessionId: sid, chatId, itemId });
    if (res && res.code === 200) {
      if (step.stepType === 'query') fillQueryStep(step, res.data);
      else fillComputeStep(step, res.data);
    }
  } catch (e) {
    step.error = '结果获取失败';
  } finally {
    step.loadingData = false;
    maybeFollow()
  }
}


export function openStream(sid, chatId, host, turn, streamParam, maybeFollow, onBusyChange) {
  streamParam.canStop = true;
  streamParam.chatWs = connectChatWebSocket({
    chatSessionId: sid,
    chatId,
    host,
    onMessage: (msg) => onStreamMessage(msg, sid, turn, maybeFollow),
    onClose: () => {
      streamParam.chatWs = null;
      streamParam.busy = false;
      onBusyChange && onBusyChange(streamParam.busy, turn);
      //streamParam.canStop = false;
      streamParam.currentChatId = ''
      streamParam.currentStepType = '';
      turn.loading = false

      if (turn.think) turn.think.done = true;
          turn.steps.forEach((s) => {
            if (s.status === 'running') s.status = 'done';
          });
          turn.status = turn.status === 'stopped' ? 'stopped' : 'done';
    },
    onError: (e) => {
      streamParam.chatWs = null;
      streamParam.busy = false;
      onBusyChange && onBusyChange(streamParam.busy, turn);
      //streamParam.canStop = false;
      streamParam.currentChatId = ''
      turn.loading = false
      toast.error(e && e.type === 'WebSocket timed out' ? '思考过程超时，请刷新重试' : '思考过程连接异常');
    },
  });
}