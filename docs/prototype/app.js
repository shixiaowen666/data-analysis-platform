/* eslint-disable */
new Vue({
  el: '#app',
  data() {
    const dict = window.ERROR_DICT;
    return {
      page: (location.hash || '#chat').slice(1),
      adminTab: ['feedback','sessions','stats','tuning','settings'].includes((location.hash||'').slice(1)) ? location.hash.slice(1) : 'feedback',
      dict,
      sessions: window.SESSIONS,
      current: window.SESSIONS[0],
      openSteps: ['step_1'],
      // 用户反馈
      fbDialog: false,
      mSheet: false,
      fbForm: { errorTypes: [], description: '' },
      // 反馈列表筛选
      fq: { agent: '', type: '', status: '', range: '', kw: '' },
      // 会话筛选
      sq: { quick: 'all' },
      // 诊断
      diagChatId: window.SESSIONS[0].chatId,
      diagForm: this.emptyDiagForm(),
      metaDialog: false, jsonDialog: false, logDialog: false,
      mockMeta: window.MOCK_META, mockLog: window.MOCK_LOG,
      allTables: window.ALL_TABLES,
      statRange: '30',
      // 调优
      tasks: window.TASKS,
      assetMeta: window.ASSET_META,
      taskId: window.TASKS[0].id,
      tq: { quick: 'all' },
      verifyCfg: { similar: 10, regression: 15, manual: [], manualInput: '', judgeTable: true, judgeEntity: true, judgeStatus: true, judgeAnswer: true, judgeLlm: true },
      publishDialog: false, publishNote: '', publishAddRegression: true,
      rollbackDialog: false, rollbackReason: '', rollbackMode: 'PARTIAL',
      manualDialog: false, manualForm: { assetType: 'TABLE_DESC', target: '', after: '', reason: '' },
      vq: { type: 'all' }, vcase: null,
      // 设置（默认值）
      settings: { maxCases: 30, similarLimit: 10, regressionLimit: 15, concurrency: 3, caseTimeout: 120, totalTimeout: 30, judgeAnswer: true, judgeLlm: true, temperature: 0, beforeSource: 'TRACE',
        approvalRequired: true, approvers: ['赵超管', '钱超管'], notifySite: true, notifyEmail: false, rollbackApproval: false, postVerify: true,
        observeDays: 7, alertWindow: 24, alertThreshold: 2, alertSite: true, alertEmail: false, alertAutoRollback: false, minioRetain: 90, caseRetain: 180 },
      settingsScope: 'GLOBAL',
      // 审批
      apTab: 'pending', apType: 'TUNING_PUBLISH', apSel: null, apComment: '',
      // 编辑器
      editorOpen: false,
      editor: { change: null, groupName: '', baseVersion: '', baseContent: '', content: '', version: '', description: '', find: '', replace: '', versions: [], verifying: false, quickResult: null },
      statBars: [
        { code:'TABLE_SELECT', name:'选表错误', n:13, pct:100, color:'#ef4444' },
        { code:'METRIC_DIM', name:'指标/维度提取错误', n:11, pct:85, color:'#f59e0b' },
        { code:'DECOMPOSE', name:'拆分错误', n:8, pct:62, color:'#3b82f6' },
        { code:'SUMMARY', name:'总结错误', n:4, pct:31, color:'#06b6d4' },
        { code:'INTENT', name:'意图识别错误', n:1, pct:8, color:'#94a3b8' },
      ],
      fixBars: [
        { name:'修改表描述 / 业务口径', n:10, pct:100 }, { name:'补充指标/维度别名', n:7, pct:70 }, { name:'调整拆解提示词', n:6, pct:60 },
        { name:'修正 agg_type', n:3, pct:30 }, { name:'调整总结提示词', n:2, pct:20 }, { name:'无需修复', n:1, pct:10 },
      ],
    };
  },
  computed: {
    userErrorTypes() { return this.dict.filter(d => d.user); },
    adminErrorTypes() { return this.dict.filter(d => d.admin && !d.parent); },
    dictTable() { return this.dict.map(d => ({ ...d, name: d.parent ? '└ ' + d.name : d.name })); },
    myFeedback() { return this.current.feedback || { rating: 0, errorTypes: [] }; },
    feedbacks() {
      return this.sessions.filter(s => s.feedback && s.feedback.rating === -1).map(s => ({
        chatId: s.chatId, time: s.feedback.time, user: s.feedback.user, agent: s.agent, question: s.question,
        errorTypes: s.feedback.errorTypes, description: s.feedback.description, status: s.feedback.status, diagnosis: s.diagnosis,
        _s: s,
      }));
    },
    pendingCount() { return this.feedbacks.filter(f => f.status === 0).length; },
    filteredFeedbacks() {
      return this.feedbacks.filter(f =>
        (this.fq.type === '' || f.errorTypes.includes(this.fq.type)) &&
        (this.fq.status === '' || f.status === this.fq.status) &&
        (!this.fq.kw || f.question.includes(this.fq.kw) || f.user.includes(this.fq.kw) || (f.description||'').includes(this.fq.kw))
      );
    },
    filteredSessions() {
      const q = this.sq.quick;
      return this.sessions.filter(s => {
        if (q === 'fb') return s.feedback && s.feedback.rating === -1;
        if (q === 'fail') return s.status !== 'success';
        if (q === 'undiag') return !s.diagnosis && ((s.feedback && s.feedback.rating === -1) || s.status !== 'success');
        if (q === 'unfix') return s.diagnosis && s.diagnosis.fixStatus === 0;
        return true;
      });
    },
    diag() { return this.sessions.find(s => s.chatId === this.diagChatId); },
    task() { return this.tasks.find(t => t.id === this.taskId); },
    filteredTasks() {
      const q = this.tq.quick;
      return this.tasks.filter(t => {
        if (q === 'todo') return ['DRAFT', 'VERIFIED'].includes(t.status);
        if (q === 'verifying') return t.status === 'VERIFYING';
        if (q === 'verified') return t.status === 'VERIFIED';
        if (q === 'pending') return t.status === 'PENDING_APPROVAL';
        if (q === 'published') return t.status === 'PUBLISHED';
        if (q === 'rolled') return t.status === 'ROLLED_BACK';
        return t.status !== 'CANCELLED';
      }).filter(t => !this.fq.kw || t.taskNo.includes(this.fq.kw) || t.question.includes(this.fq.kw) || t.changes.some(c => c.targetLabel.includes(this.fq.kw)));
    },
    verifyTotal() { return 1 + this.verifyCfg.similar + this.verifyCfg.regression + this.verifyCfg.manual.length; },
    pendingApprovals() { return this.tasks.filter(t => t.status === 'PENDING_APPROVAL'); },
    doneApprovals() { return this.tasks.filter(t => t.approval && t.approval.result); },
    editorLines() {
      const base = this.editor.baseContent.split('\n'); const cur = this.editor.content.split('\n');
      return cur.map((l, i) => ({ text: l, status: base.includes(l) ? 'same' : 'add' }));
    },
    editorDiff() {
      const base = this.editor.baseContent.split('\n'); const cur = this.editor.content.split('\n');
      const baseSet = new Set(base), curSet = new Set(cur);
      const lines = []; let add = 0, del = 0;
      // 简易 LCS-free diff：按行集合判断（原型足够）
      let bi = 0;
      cur.forEach(l => {
        if (baseSet.has(l)) {
          while (bi < base.length && base[bi] !== l) { if (!curSet.has(base[bi])) { lines.push({ type: 'del', text: base[bi] }); del++; } bi++; }
          lines.push({ type: 'same', text: l }); bi++;
        } else { lines.push({ type: 'add', text: l }); add++; }
      });
      while (bi < base.length) { if (!curSet.has(base[bi])) { lines.push({ type: 'del', text: base[bi] }); del++; } bi++; }
      return { lines: lines.filter(l => l.type !== 'same' || true).slice(0, 400), add, del };
    },
    editorLint() {
      const out = [];
      const req = this.editor.change && this.editor.change.assetType === 'PROMPT_SUMMARY' ? ['{{question}}', '{{data}}'] : ['{{current_date}}', '{{database_meta}}'];
      req.forEach(ph => { if (!this.editor.content.includes(ph)) out.push('缺少必需占位符 ' + ph); });
      const open = (this.editor.content.match(/\{/g) || []).length, close = (this.editor.content.match(/\}/g) || []).length;
      if (open !== close) out.push('花括号不配对：{ ' + open + ' 个，} ' + close + ' 个');
      if (this.editor.version && this.editor.versions.some(v => v.v === this.editor.version)) out.push('版本号 ' + this.editor.version + ' 已存在');
      return out;
    },
    editorRuleHits() {
      if (!this.task) return [];
      const kws = (this.task.diagnosis.rootCause || '').match(/累计|快照|同比|环比|比率|排序|排名|总结|时间|对比期|聚合|sum/g) || [];
      const lines = this.editor.content.split('\n'); const hits = [];
      lines.forEach((l, i) => { if (/^规则|^【|^- 【/.test(l.trim()) && kws.some(k => l.includes(k))) hits.push({ line: i + 1, text: l.trim().slice(0, 40) + (l.length > 40 ? '…' : '') }); });
      return hits.slice(0, 8);
    },
    filteredCases() {
      if (!this.task || !this.task.verify) return [];
      const t = this.vq.type;
      return this.task.verify.cases.filter(c => t === 'all' || c.verdict === t || (t === 'FIXED' && c.verdict === 'IMPROVED'));
    },
    decomposeJson() {
      if (!this.diag) return '';
      const d = this.diag;
      return JSON.stringify(d.steps.map(s => {
        const st = d.trace.stepTraces.find(q => q.stepId === s.stepId) || { columns: [] };
        let params = {};
        if (s.type === 'query') params = { expected_table: s.table, expected_columns: st.columns.map(c => ({ name: c.name, role: c.role, filter: c.filter || undefined })) };
        else if (s.type === 'compute') params = { operations: [{ 'function': s.func, inputs: s.params }] };
        return { step_id: s.stepId, step_type: s.type, description: s.description, depends_on: s.dependsOn || [], params };
      }), null, 2);
    },
    focusStepTrace() {
      if (!this.diag) return null;
      return this.diag.trace.stepTraces.find(q => q.stepId === this.diagForm.errorStepId) || this.diag.trace.stepTraces[0];
    },
  },
  watch: {
    page(v) { location.hash = v; if (['feedback','sessions','stats','tuning','settings'].includes(v)) this.adminTab = v; },
  },
  mounted() {
    window.addEventListener('hashchange', () => { const h = location.hash.slice(1); if (h && h !== this.page) this.page = h; });
  },
  methods: {
    go(p) { this.page = p; },
    onAdminTab(tab) { this.page = tab.name; },
    emptyDiagForm() {
      return { primary: '', sub: '', secondary: [], errorStepId: '', expectedTable: '', entityFix: [{ actual: '', expected: '' }], rootCause: '', fixType: '', fixDetail: '', fixStatus: 0, addToRegression: true };
    },
    dictName(code) { const d = this.dict.find(x => x.code === code); return d ? d.name : code; },
    dictHint(code) { const d = this.dict.find(x => x.code === code); return d ? d.hint : ''; },
    stepTypeZh(t) { return { query: '查询', compute: '计算', analyze: '分析', summarize: '总结' }[t] || t; },
    stepTagType(t) { return { query: '', compute: 'warning', analyze: 'info', summarize: 'success' }[t] || 'info'; },
    statusName(s) { return ['待处理', '已定位', '已修复', '已忽略'][s]; },
    statusType(s) { return ['danger', 'warning', 'success', 'info'][s]; },
    // ---------- 用户反馈 ----------
    thumbUp() {
      this.$set(this.current, 'feedback', { user: '张工', time: this.now(), rating: 1, status: 2, errorTypes: [], description: '' });
      this.$message.success('感谢反馈');
    },
    openFeedback() {
      const fb = this.current.feedback;
      this.fbForm = { errorTypes: fb && fb.rating === -1 ? [...fb.errorTypes] : [], description: fb && fb.rating === -1 ? fb.description : '' };
      this.fbDialog = true;
    },
    toggleType(c) { const i = this.fbForm.errorTypes.indexOf(c); i >= 0 ? this.fbForm.errorTypes.splice(i, 1) : this.fbForm.errorTypes.push(c); },
    submitFeedback(mobile) {
      this.$set(this.current, 'feedback', { user: '张工', time: this.now(), rating: -1, status: 0, errorTypes: [...this.fbForm.errorTypes], description: this.fbForm.description });
      this.fbDialog = false; this.mSheet = false;
      this.$message.success('反馈已提交，管理员将进行错误定位');
      // 模拟接口
      console.log('POST /api/v1/chat/feedback', { chatSessionId: this.current.sessionId, chatId: this.current.chatId, rating: -1, errorTypes: this.fbForm.errorTypes, description: this.fbForm.description });
    },
    // ---------- 诊断 ----------
    openDiagnosis(chatId) {
      this.diagChatId = chatId;
      const s = this.diag;
      this.diagForm = this.emptyDiagForm();
      if (s.diagnosis) {
        Object.assign(this.diagForm, { primary: s.diagnosis.primary, secondary: [...(s.diagnosis.secondary || [])], errorStepId: s.diagnosis.errorStepId, rootCause: s.diagnosis.rootCause, fixType: s.diagnosis.fixType, fixStatus: s.diagnosis.fixStatus });
      } else {
        const hints = this.autoHints(s);
        if (hints.length) this.diagForm.primary = hints[0];
        const failed = s.steps.find(x => x.status === 'failed');
        if (failed) this.diagForm.errorStepId = failed.stepId;
      }
      this.page = 'diagnosis';
    },
    pick(code) {
      if (this.diagForm.primary && this.diagForm.primary !== code && !this.diagForm.secondary.includes(code)) {
        this.diagForm.secondary.push(code);
        this.$message({ message: `已加入次要错误类型：${this.dictName(code)}`, type: 'info', duration: 1500 });
      } else {
        this.diagForm.primary = code;
        this.diagForm.secondary = this.diagForm.secondary.filter(c => c !== code);
      }
    },
    autoHints(s) {
      const h = new Set();
      if (s.trace.execLog.some(e => e.status === 'failed')) h.add('DECOMPOSE');
      if (s.trace.stepTraces.some(q => q.columns.some(c => !c.resolved))) h.add('METRIC_DIM');
      if (s.trace.stepTraces.some(q => q.expectedTable === 'UNKNOWN' || q.expectedTable !== q.resolvedTable)) h.add('TABLE_SELECT');
      if (s.trace.decomposeRetries >= 2) h.add('DECOMPOSE');
      // 用户明确说选表错 + 召回里存在更贴近关键词的表：仅示意
      if (s.feedback && s.feedback.errorTypes.includes('TABLE_SELECT') && s.question.includes('分压') && !s.trace.usedTables.some(t => t.includes('voltage'))) h.add('TABLE_SELECT');
      return [...h];
    },
    autoHintWhy(s) {
      const why = [];
      if (s.trace.execLog.some(e => e.status === 'failed')) why.push('存在执行失败的步骤');
      if (s.trace.stepTraces.some(q => q.columns.some(c => !c.resolved))) why.push('存在未映射到指标/维度的拆解列');
      if (s.trace.decomposeRetries >= 2) why.push(`拆解重试 ${s.trace.decomposeRetries} 次`);
      if (s.question.includes('分压') && !s.trace.usedTables.some(t => t.includes('voltage'))) why.push('问题含「分压」但未使用分压线损表，且召回结果中无该表');
      return why.join('；');
    },
    saveDiagnosis(withTuning) {
      const s = this.diag;
      const primary = this.diagForm.primary === 'METRIC_DIM' && this.diagForm.sub && this.diagForm.sub !== 'METRIC_DIM.BOTH' ? this.diagForm.sub : this.diagForm.primary;
      this.$set(s, 'diagnosis', { primary, secondary: [...this.diagForm.secondary], errorStepId: this.diagForm.errorStepId, fixStatus: this.diagForm.fixStatus, by: '王管理员', at: this.now(), rootCause: this.diagForm.rootCause, fixType: this.diagForm.fixType, expectedTable: this.diagForm.expectedTable });
      if (s.feedback && s.feedback.rating === -1 && s.feedback.status === 0) s.feedback.status = 1;
      if (this.diagForm.fixStatus >= 1 && s.feedback) s.feedback.status = 2;
      console.log('POST /api/v1/chat/diagnosis/save', s.diagnosis);
      if (withTuning) {
        const existing = this.taskOfChat(s.chatId);
        if (existing) { this.$message.info('该问题已有调优任务 ' + existing.taskNo); this.openTask(existing.id); return; }
        this.createTaskFromDiagnosis(s, primary);
      } else {
        this.$message.success('定位结论已保存');
        this.page = 'sessions';
      }
    },
    // ---------- 调优 ----------
    taskOfChat(chatId) { return this.tasks.find(t => t.chatId === chatId && t.status !== 'CANCELLED'); },
    taskStatusName(st) { return { DRAFT: '草稿', APPLIED: '已应用', VERIFYING: '验证中', VERIFIED: '待提交审批', PENDING_APPROVAL: '待审批', PUBLISHED: '已发布', ROLLED_BACK: '已回退', FAILED: '失败', CANCELLED: '已取消' }[st] || st; },
    taskStatusType(st) { return { DRAFT: 'info', APPLIED: '', VERIFYING: '', VERIFIED: 'warning', PENDING_APPROVAL: 'warning', PUBLISHED: 'success', ROLLED_BACK: 'danger', FAILED: 'danger', CANCELLED: 'info' }[st] || ''; },
    taskStepIndex(st) { return { DRAFT: 0, APPLIED: 1, VERIFYING: 2, VERIFIED: 3, PENDING_APPROVAL: 4, PUBLISHED: 6, ROLLED_BACK: 6, FAILED: 2, CANCELLED: 0 }[st] || 0; },
    assetName(t) { return (this.assetMeta[t] || {}).name || t; },
    assetColor(t) { return (this.assetMeta[t] || {}).color || '#64748b'; },
    openTask(id, action) {
      this.taskId = id; this.page = 'tuningDetail';
      const t = this.task;
      if (t && t.status === 'DRAFT') { this.verifyCfg.similar = this.settings.similarLimit; this.verifyCfg.regression = this.settings.regressionLimit; }
      if (action === 'rollback') this.$nextTick(() => { this.rollbackDialog = true; });
    },
    openVerify(id) { this.taskId = id; this.vcase = null; this.vq.type = 'all'; this.page = 'verify'; this.$nextTick(() => { const t = this.task; if (t && t.verify) this.vcase = t.verify.cases.find(c => c.verdict === 'DEGRADED') || t.verify.cases[0]; }); },
    createTaskFromDiagnosis(s, primary) {
      // 简化版规则引擎：按主因生成建议
      const changes = [];
      let id = Date.now();
      const st = s.trace.stepTraces.find(q => q.stepId === this.diagForm.errorStepId) || s.trace.stepTraces[0];
      if (primary === 'TABLE_SELECT' && this.diagForm.expectedTable) {
        const inRecall = s.trace.recall.tables.includes(this.diagForm.expectedTable);
        changes.push({ id: id++, source: 'SUGGEST', accepted: true, confidence: '高', assetType: 'TABLE_DESC', targetModule: 'bi-manager', targetLabel: this.diagForm.expectedTable, field: 'tb_description', applyStatus: null, effect: this.assetMeta.TABLE_DESC.effect,
          reason: inRecall ? 'R2 · 应选表已被召回但模型未选：增加区分性描述' : 'R1 · 应选表不在召回结果中：追加原问题关键词到表描述', before: '（当前描述）', after: '（当前描述）；' + s.question.replace(/[？?，,。]/g, ' ').split(' ').filter(w => w.length >= 2).slice(0, 3).join('、') + ' 相关问题查本表' });
        changes.push({ id: id++, source: 'SUGGEST', accepted: true, confidence: '高', assetType: 'KNOWLEDGE', targetModule: 'chat-server', targetLabel: s.agent + ' · 知识库', field: 'knowledge_element', applyStatus: null, effect: this.assetMeta.KNOWLEDGE.effect, reason: 'R2 · 追加区分规则', before: '', after: this.diagForm.rootCause });
        if (!inRecall) changes.push({ id: id++, source: 'SUGGEST', accepted: false, confidence: '中', assetType: 'RECALL_CONFIG', targetModule: 'recall', targetLabel: 'path_a / table · top_k', field: 'top_k', applyStatus: null, effect: this.assetMeta.RECALL_CONFIG.effect, reason: 'R4 · 召回表数偏少（' + s.trace.recall.metaAfter + '）', before: '10', after: '15' });
      }
      if (primary.startsWith('METRIC_DIM')) {
        this.diagForm.entityFix.filter(e => e.expected).forEach(e => changes.push({ id: id++, source: 'SUGGEST', accepted: true, confidence: '高', assetType: 'ENTITY_ALIAS', targetModule: 'bi-manager', targetLabel: e.expected, field: 'alias', applyStatus: null, effect: this.assetMeta.ENTITY_ALIAS.effect, reason: 'R5/R6 · 为应选实体追加原问题中的指代词', before: '（当前别名）', after: '（当前别名）, ' + (e.actual || '…') }));
        if (st && st.columns.some(c => !c.resolved)) changes.push({ id: id++, source: 'SUGGEST', accepted: true, confidence: '中', assetType: 'ENTITY_ALIAS', targetModule: 'bi-manager', targetLabel: '最接近实体（embedding top1）', field: 'alias', applyStatus: null, effect: this.assetMeta.ENTITY_ALIAS.effect, reason: 'R7 · 未映射列 ' + st.columns.filter(c => !c.resolved).map(c => c.name).join(',') + ' 作为别名加入', before: '', after: st.columns.filter(c => !c.resolved).map(c => c.name).join(', ') });
      }
      if (primary === 'DECOMPOSE') {
        if (/快照|累计|率|sum/i.test(this.diagForm.rootCause)) changes.push({ id: id++, source: 'SUGGEST', accepted: true, confidence: '高', assetType: 'AGG_TYPE', targetModule: 'bi-manager', targetLabel: (st ? st.resolvedTable : '表') + '.（相关指标列）', field: 'summary', applyStatus: null, effect: this.assetMeta.AGG_TYPE.effect, reason: 'R8 · 根因提到口径类问题，修正聚合类型', before: '（空）', after: /快照/.test(this.diagForm.rootCause) ? 'snapshot' : /累计/.test(this.diagForm.rootCause) ? 'cumulative' : 'avg' });
        changes.push({ id: id++, source: 'SUGGEST', accepted: false, confidence: '低', assetType: 'PROMPT_DECOMPOSE', targetModule: 'System B', targetLabel: 'prompt-main', field: 'content', applyStatus: null, effect: this.assetMeta.PROMPT_DECOMPOSE.effect, reason: 'R9 · 需人工编辑：参考失败步骤 ' + this.diagForm.errorStepId + ' 与根因修改规则', before: 'v1.0.5', after: '（基于 v1.0.5 新建，待编辑）' });
      }
      if (primary === 'INTENT') changes.push({ id: id++, source: 'SUGGEST', accepted: true, confidence: '中', assetType: 'KNOWLEDGE', targetModule: 'chat-server', targetLabel: s.agent + ' · 知识库', field: 'knowledge_element', applyStatus: null, effect: this.assetMeta.KNOWLEDGE.effect, reason: 'R10 · 追加业务知识', before: '', after: this.diagForm.rootCause });
      if (primary === 'SUMMARY') changes.push({ id: id++, source: 'SUGGEST', accepted: false, confidence: '低', assetType: 'PROMPT_SUMMARY', targetModule: 'System B', targetLabel: 'prompt-summary', field: 'content', applyStatus: null, effect: this.assetMeta.PROMPT_SUMMARY.effect, reason: 'R11 · 需人工编辑总结提示词', before: 'v2.0.2', after: '（基于 v2.0.2 新建，待编辑）' });

      const task = { id: id++, taskNo: 'TN' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-' + String(this.tasks.length + 1).padStart(3, '0'), chatId: s.chatId, question: s.question, errorType: primary, status: 'DRAFT', round: 1, by: '王管理员', at: this.now(),
        diagnosis: { errorStepId: this.diagForm.errorStepId, actualTable: st ? st.resolvedTable : '', expectedTable: this.diagForm.expectedTable, rootCause: this.diagForm.rootCause },
        impact: { agents: [s.agent], chats: 12 + Math.floor(Math.random() * 40), negative: 1 + Math.floor(Math.random() * 3), regression: 4 + Math.floor(Math.random() * 8) },
        changes, snapshots: [], verify: null, audit: [{ at: this.now(), by: '王管理员', text: '保存定位结论并生成 ' + changes.length + ' 条调优建议' }] };
      this.tasks.unshift(task);
      this.$message.success('已生成 ' + changes.length + ' 条调优建议');
      this.openTask(task.id);
    },
    executeTask() {
      const t = this.task;
      t.snapshots = t.changes.filter(c => c.accepted).map((c, i) => ({ id: i + 1, assetType: c.assetType, label: c.targetLabel, field: c.field, at: this.now(), version: c.assetType.startsWith('PROMPT') ? c.before : undefined }));
      t.changes.forEach(c => { if (c.accepted) c.applyStatus = 'APPLIED'; });
      const total = 1 + this.verifyCfg.similar + this.verifyCfg.regression + this.verifyCfg.manual.length;
      t.verify = { conclusion: null, total, done: 0, originFixed: null, regTotal: this.verifyCfg.regression, regPass: 0, degraded: 0, improved: 0, cases: [] };
      t.status = 'VERIFYING';
      t.audit.push({ at: this.now(), by: '王管理员', text: '执行并验证（拍摄 ' + t.snapshots.length + ' 个资产快照）', type: 'primary' });
      this.$message.success('已应用到草稿态，开始验证');
      // 模拟进度
      const timer = setInterval(() => {
        if (t.status !== 'VERIFYING') { clearInterval(timer); return; }
        t.verify.done = Math.min(t.verify.total, t.verify.done + 1);
        if (t.verify.done >= t.verify.total) {
          clearInterval(timer);
          const reg = this.verifyCfg.regression; const degraded = Math.random() < 0.3 ? 1 : 0;
          Object.assign(t.verify, { conclusion: degraded ? 'PASS_WITH_WARN' : 'PASS', originFixed: true, regPass: reg - degraded, degraded, improved: Math.min(2, this.verifyCfg.similar), elapsedBefore: 18.4, elapsedAfter: 17.6, tokens: total * 13, startedAt: '--', finishedAt: '--',
            cases: [window.mkCaseProto({ caseType: 'ORIGIN', sourceChatId: t.chatId, question: t.question, expected: { table: t.diagnosis.expectedTable || '—', metrics: ['—'] }, before: { table: t.diagnosis.actualTable, metrics: ['…'], dims: ['…'], steps: '…', status: 'success', answer: '（调优前答案）' }, after: { table: t.diagnosis.expectedTable || t.diagnosis.actualTable, metrics: ['…'], dims: ['…'], steps: '…', status: 'success', answer: '（调优后答案）' }, beforePass: false, afterPass: true, verdict: 'FIXED', judge: { table: { ok: true, msg: '命中' }, metrics: { ok: true, msg: '✓' }, dims: { ok: true, msg: '✓' }, status: { ok: true, msg: 'success' }, answer: { ok: true, msg: '跳过' } } })]
              .concat(Array.from({ length: total - 1 }, (_, i) => window.mkCaseProto({ caseType: i < this.verifyCfg.similar ? 'SIMILAR' : 'REGRESSION', question: '（验证题 ' + (i + 2) + '）', expected: { table: '—', metrics: ['—'] }, before: { table: '—', metrics: ['…'], dims: ['…'], steps: '…', status: 'success', answer: '…' }, after: { table: '—', metrics: ['…'], dims: ['…'], steps: '…', status: 'success', answer: '…' }, beforePass: true, afterPass: !(degraded && i === total - 2), verdict: degraded && i === total - 2 ? 'DEGRADED' : i < Math.min(2, this.verifyCfg.similar) ? 'IMPROVED' : 'PASS', judge: { table: { ok: true, msg: '命中' }, metrics: { ok: !(degraded && i === total - 2), msg: degraded && i === total - 2 ? '指标不一致' : '✓' }, dims: { ok: true, msg: '✓' }, status: { ok: true, msg: 'success' }, answer: { ok: true, msg: '✓' } } }))) });
          t.status = 'VERIFIED';
          t.audit.push({ at: this.now(), by: '系统', text: '验证完成：' + total + ' 题，原题修复，回归 ' + t.verify.regPass + '/' + reg + '，退化 ' + degraded, type: degraded ? 'warning' : 'success' });
        }
      }, 400);
    },
    submitApproval() {
      const t = this.task;
      t.status = 'PENDING_APPROVAL';
      t.approval = { submittedAt: this.now(), result: null, by: null, at: null, comment: '', confirmNote: this.publishNote };
      t.audit.push({ at: this.now(), by: '王管理员', text: '提交超级管理员审批' + (this.publishNote ? '（确认说明：' + this.publishNote + '）' : ''), type: 'primary' });
      t.audit.push({ at: this.now(), by: '系统', text: '已通知审批人：' + this.settings.approvers.join('、') });
      this.publishDialog = false; this.publishNote = '';
      this.$message.success('已提交审批，等待超级管理员处理');
    },
    withdrawTask() {
      const t = this.task;
      t.status = 'VERIFIED'; t.approval = null;
      t.audit.push({ at: this.now(), by: '王管理员', text: '撤回审批申请' });
      this.$message.info('已撤回');
    },
    approve(ok) {
      const t = this.apSel;
      t.approval.result = ok ? 'APPROVED' : 'REJECTED'; t.approval.by = '赵超管'; t.approval.at = this.now(); t.approval.comment = this.apComment;
      if (ok) {
        t.audit.push({ at: this.now(), by: '赵超管', text: '审批通过' + (this.apComment ? '：' + this.apComment : '') + ' → 系统自动发布', type: 'success' });
        this.doPublish(t);
        this.$message.success('已通过并发布');
      } else {
        t.status = 'VERIFIED';
        t.audit.push({ at: this.now(), by: '赵超管', text: '审批驳回：' + this.apComment, type: 'danger' });
        this.$message.warning('已驳回，任务回到待提交状态');
      }
      this.apComment = ''; this.apSel = null;
    },
    doPublish(t) {
      t.status = 'PUBLISHED'; t.publishedAt = this.now(); t.observe = { days: 0, negative: 0 };
      t.changes.forEach(c => { if (c.accepted) c.applyStatus = 'PUBLISHED'; });
      t.audit.push({ at: this.now(), by: '系统', text: '写入线上 · recall 同步 · prompts/switch（如有）', type: 'success' });
      if (this.settings.postVerify) t.audit.push({ at: this.now(), by: '系统', text: '线上复验原问题：通过', type: 'success' });
      t.audit.push({ at: this.now(), by: '系统', text: '进入 ' + this.settings.observeDays + ' 天观察期（告警阈值 ' + this.settings.alertWindow + 'h 内同类 👎 ≥ ' + this.settings.alertThreshold + '）' });
      const s = this.sessions.find(x => x.chatId === t.chatId);
      if (s) { if (s.diagnosis) s.diagnosis.fixStatus = 1; if (s.feedback) s.feedback.status = 2; }
    },
    rollbackTask() {
      const t = this.task;
      const wasPublished = t.status === 'PUBLISHED';
      t.status = 'ROLLED_BACK'; t.rolledBackAt = this.now(); t.rollbackReason = this.rollbackReason;
      t.changes.forEach(c => { if (c.accepted) c.applyStatus = 'ROLLED_BACK'; });
      t.audit.push({ at: this.now(), by: '王管理员', text: (wasPublished ? '回退已发布（无需审批）' : '回退草稿') + '：恢复 ' + t.snapshots.length + ' 个资产快照（' + this.rollbackReason + '）', type: 'warning' });
      if (wasPublished) t.audit.push({ at: this.now(), by: '系统', text: '已通知超级管理员：' + t.taskNo + ' 已回退' });
      const s = this.sessions.find(x => x.chatId === t.chatId);
      if (s && wasPublished) { if (s.diagnosis) s.diagnosis.fixStatus = 0; if (s.feedback) s.feedback.status = 1; }
      this.rollbackDialog = false; this.rollbackReason = '';
      this.$message.warning('已回退，快照已恢复');
    },
    cloneTask() {
      const t = this.task;
      const n = JSON.parse(JSON.stringify(t));
      n.id = Date.now(); n.taskNo = t.taskNo + '-R'; n.status = 'DRAFT'; n.round = 1; n.verify = null; n.snapshots = []; n.publishedAt = null; n.rolledBackAt = null; n.rollbackReason = null; n.approval = null;
      n.changes.forEach(c => { c.applyStatus = null; }); n.at = this.now();
      n.audit = [{ at: this.now(), by: '王管理员', text: '从 ' + t.taskNo + ' 复制' }];
      this.tasks.unshift(n); this.openTask(n.id);
    },
    addManualChange() { this.manualForm = { assetType: 'TABLE_DESC', target: '', after: '', reason: '' }; this.manualDialog = true; },
    confirmManual() {
      const m = this.assetMeta[this.manualForm.assetType];
      this.task.changes.push({ id: Date.now(), source: 'MANUAL', accepted: true, assetType: this.manualForm.assetType, targetModule: m.module, targetLabel: this.manualForm.target || '（未指定）', field: '—', applyStatus: null, effect: m.effect, reason: this.manualForm.reason || '手动追加', before: '（当前值）', after: this.manualForm.after });
      this.manualDialog = false;
    },
    editChange(c) { this.$set(c, 'editing', true); },
    openEditor(c) {
      const isSum = c.assetType === 'PROMPT_SUMMARY';
      const base = c.before && /^v\d/.test(c.before) ? c.before : (isSum ? 'v2.0.2' : 'v1.0.5');
      const content = c.draftContent || (isSum ? window.PROMPT_SUMMARY_V202 : window.PROMPT_MAIN_V105);
      const bump = v => { const p = v.replace(/^v/, '').split('.').map(Number); p[p.length - 1]++; return 'v' + p.join('.'); };
      this.editor = { change: c, groupName: isSum ? 'prompt-summary 总结提示词' : 'prompt-main 拆解提示词', baseVersion: base, baseContent: isSum ? window.PROMPT_SUMMARY_V202 : window.PROMPT_MAIN_V105, content, version: c.draftVersion || bump(base), description: c.draftDesc || '', find: '', replace: '', verifying: false, quickResult: null,
        versions: isSum ? [{ v: 'v2.0.2', desc: '当前', active: true }, { v: 'v2.0.1', desc: '修正单位' }, { v: 'v2.0.0', desc: '初版' }] : [{ v: 'v1.0.5', desc: '当前', active: true }, { v: 'v1.0.4', desc: '节假日函数' }, { v: 'v1.0.3', desc: '快照规则' }] };
      this.editorOpen = true;
    },
    jumpToLine(n) {
      const ta = this.$refs.edTextarea; if (!ta) return;
      const lines = this.editor.content.split('\n'); let pos = 0; for (let i = 0; i < n - 1; i++) pos += lines[i].length + 1;
      ta.focus(); ta.setSelectionRange(pos, pos + lines[n - 1].length);
      const lh = 18; ta.scrollTop = Math.max(0, (n - 5) * lh);
    },
    doReplace() { if (!this.editor.find) return; this.editor.content = this.editor.content.split(this.editor.find).join(this.editor.replace); },
    quickVerify() {
      this.editor.verifying = true; this.editor.quickResult = null;
      setTimeout(() => { this.editor.verifying = false; const pass = this.editorDiff.add > 0; this.editor.quickResult = { pass, elapsed: 14.2, msg: pass ? '以草稿提示词重跑原问题：拆解 4 步，对比期区间 2025-01-01~2025-09-28 与本期对齐，增长率 5.8%' : '草稿与基线无差异，结果与调优前一致' }; }, 1500);
    },
    saveEditor() {
      const c = this.editor.change;
      this.$set(c, 'draftVersion', this.editor.version); this.$set(c, 'draftContent', this.editor.content); this.$set(c, 'draftDesc', this.editor.description);
      this.$set(c, 'diffAdd', this.editorDiff.add); this.$set(c, 'diffDel', this.editorDiff.del);
      c.before = this.editor.baseVersion; c.after = this.editor.version; c.accepted = true;
      c.reason = (c.reason.split(' | ')[0]) + ' | ' + this.editor.description;
      this.editorOpen = false;
      this.$message.success('已保存为 ' + this.editor.version + '（未激活），执行验证时将以该版本运行');
      console.log('POST /api/v1/prompts/version/create', { group: this.editor.groupName, base_version: this.editor.baseVersion, version: this.editor.version, description: this.editor.description });
    },
    highlightDiff(before, after) {
      const esc = x => String(x || '').replace(/[&<>]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[ch]));
      if (!before || before === '（空）' || before === '（当前描述）' || before === '（当前别名）' || before === '（当前值）') return '<span class="ins">' + esc(after) + '</span>';
      if (after.startsWith(before)) return esc(before) + '<span class="ins">' + esc(after.slice(before.length)) + '</span>';
      return '<span class="ins">' + esc(after) + '</span>';
    },
    now() { const d = new Date(); return d.toISOString().slice(0, 16).replace('T', ' '); },
  },
});
