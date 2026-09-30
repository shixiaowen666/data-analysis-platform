<template>
  <div class="chat">
    <chat-top-bar
      :agent-name="agent ? agent.name : ''"
      :user-name="userName"
      :busy="streamParam.busy"
      :busy-text="busyLabel"
      :scrolled="scrolled"
      @menu="showMenu = true"
      @pick-agent="showAgent = true"
      @user="showUser = true"
    />

    <!-- 欢迎页 -->
    <welcome-hero
      v-if="!turns.length"
      class="chat__main"
      :agent-name="agent ? agent.name : ''"
      :questions="topQuestions"
      :loading="loadingQuestions"
      @ask="send"
      @more="openQuestions"
    />

    <!-- 会话流 -->
    <div v-else ref="stream" class="chat__main u-scroll-y u-no-scrollbar" @scroll="onScroll">
      <div class="chat__inner">
        <div v-if="loadingHistory" class="chat__histload">
          <tech-loader :size="30" text="正在载入会话记录" />
        </div>

        <div v-for="(t, ti) in visiableTurns" :key="t.uid" class="chat__turn">
          <!-- 轮次时间分隔线：浅蓝细线 + 居中时间胶囊 -->
          <div v-if="dividerOf(t, ti)" class="chat__sep">
            <span class="chat__septext">{{ dividerOf(t, ti) }}</span>
          </div>

          <user-bubble
            v-if="t.user"
            :text="t.user.text"
            :time="t.user.time"
            :user-name="userName"
            @copy="copy"
            @resend="send"
          />
          <agent-turn
            v-if="t.think || t.steps.length"
            class="chat__agentturn"
            :turn="t"
            :agent-name="agent ? agent.name : '智能体'"
            @view-sql="viewSql"
            @apply="applyNewQuery"
            @page="changePage"
            @toggle-highlight="toggleHighlight"
            @load-candidates="loadCandidates"
            @add-dim="addDim"
            @add-metric="addMetric"
            @remove-dim="removeDim"
            @remove-metric="removeMetric"
            @add-filters="addFilters"
            @remove-filter="removeFilter"
            @load-filter-values="loadFilterValues"
            @set-granularity="setGranularity"
            @set-range="setRange"
            @reset-query="resetQuery"
          />
        </div>
      </div>
    </div>

    <!-- 悬浮：思考状态条 + 回到顶/底 -->
    <div class="chat__floats">
      <thinking-indicator
        :visible="streamParam.busy"
        :label="busyLabel"
        :can-stop="streamParam.currentChatId != ''"
        @stop="stopChat"
      />
      <div v-if="turns.length && !streamParam.busy" class="chat__navs">
        <button v-if="showToTop" class="chat__nav" @click="scrollTo(0)">
          <app-icon name="back-top" :size="15" />
        </button>
        <button v-if="showToBottom" class="chat__nav is-accent" @click="scrollBottom(true)">
          <app-icon name="back-bottom" :size="15" />
        </button>
      </div>
    </div>

    <chat-composer
      ref="composer"
      :busy="streamParam.busy"
      :quick-tips="quickTips"
      :load="loadingHistory"
      @send="send"
      @stop="stopChat"
      @menu="showMenu = true"
      @focus="scrollBottom(true)"
    />

    <!-- ============ 抽屉 ============ -->
    <option-picker
      v-model="showAgent"
      title="选择智能体"
      mode="single"
      :options="agentOptions"
      label-key="name"
      value-key="code"
      :selected="agent ? agent.code : null"
      :loading="loadingAgents"
      :searchable="agentOptions.length > 6"
      height="56vh"
      @confirm="onAgentPick"
    />

    <option-picker
      v-model="showMenu"
      title="操作"
      mode="single"
      :options="menuOptions"
      label-key="name"
      value-key="key"
      :searchable="false"
      height="46vh"
      @confirm="onMenuPick"
    />

    <option-picker
      v-model="showUser"
      title="账号"
      mode="single"
      :options="[{id:'version',name: '查看版本',desc: '查看当前版本号'},{ id: 'logout', name: '退出登录', desc: userName }]"
      label-key="name"
      value-key="id"
      :searchable="false"
      height="32vh"
      @confirm="commandMenu"
    />

    <group-picker
      v-model="showHistory"
      title="历史会话"
      mode="single"
      :items="historyGroups"
      :loading="loadingHistoryList"
      empty-text="当前智能体暂无历史会话"
      @confirm="onHistoryPick"
    />

    <group-picker
      v-model="showQuestions"
      title="推荐问题"
      mode="single"
      :items="questionGroups"
      :loading="loadingQuestionTree"
      empty-text="暂无推荐问题"
      @confirm="onQuestionPick"
    />

    <sql-sheet v-model="showSql" :sql="activeSql" />
  </div>
</template>

<script>
import {
  getChatHistoryListAPI,
  getChatHistoryListDetailAPI,

  getQuestionAPI,
  getTagListAPI,
 
  getChatStepAPI,
  getAiBodyListAPI,

  isMultiValueOperator,
  sendChatAPI,
  stopChatAPI,
} from "@/api/newBI/newBIAPI.js";

import { getDimensionValuesAPI } from "@/api/dimensionManager/dimensionAPI.js";

import{
  filterTypeEnum,
  getMetricsDataPreviewAPI,
  getMetricsTreeAPI
} from "@/api/metricDataPreview/metricPreviewAPI.js";

import { clearAuth, getUserInfo } from '@/utils/auth';
import toast from '@/utils/toast';
import { copyToClipboard } from "@/utils/clipboard.js";

import {
  PAGE_SIZE,
  buildDataPayload,
  createStep,
  createTurn,
  fillComputeStep,
  fillQueryStep,
  groupHistory,
  nextUid,
  parseTurn,
  turnDivider,
  repaginate,
  openStream,
} from '@/utils/parseChat';

import AppIcon from '@/components/web/AppIcon.vue';
import TechLoader from '@/components/web/TechLoader.vue';
import OptionPicker from '@/components/web/OptionPicker.vue';
import GroupPicker from '@/components/web/GroupPicker.vue';
import ChatTopBar from './components/ChatTopBar.vue';
import WelcomeHero from './components/WelcomeHero.vue';
import UserBubble from './components/UserBubble.vue';
import AgentTurn from './components/AgentTurn.vue';
import ChatComposer from './components/ChatComposer.vue';
import ThinkingIndicator from './components/ThinkingIndicator.vue';
import SqlSheet from './components/SqlSheet.vue';
import { Dialog } from 'vant';

import {szLoginLink} from "@/utils/common.js"

const STEP_LABEL = {
  think: '正在推理思考',
  query: '正在取数',
  compute: '正在计算',
  analyze: '正在分析',
  summarize: '正在归纳结论',
};

export default {
  name: 'ChatPage',
  components: {
    AppIcon,
    TechLoader,
    OptionPicker,
    GroupPicker,
    ChatTopBar,
    WelcomeHero,
    UserBubble,
    AgentTurn,
    ChatComposer,
    ThinkingIndicator,
    SqlSheet,
  },
  data() {
    return {
      user: getUserInfo() || {},
      agents: [],
      agent: null,
      loadingAgents: false,

      turns: [],
      sessionId: null,
      //currentChatId: null,
      //stream: null,
      //busy: false,
      //canStop: false,
      //currentStepType: '',
      loadingHistory: false,

      topQuestions: [],
      loadingQuestions: false,
      questionGroups: [],
      loadingQuestionTree: false,

      historyGroups: [],
      loadingHistoryList: false,

      showAgent: false,
      showMenu: false,
      showUser: false,
      showHistory: false,
      showQuestions: false,
      showSql: false,
      activeSql: '',

      scrolled: false,
      showToTop: false,
      showToBottom: false,
      followBottom: true,
      scrollTimer: null,

      streamParam: {
        //canStop: false,

        chatWs: null,
        busy: false,
        currentChatId: '',

        currentStepType:'',
      },

      //queryParams:null,
     //内容太多后做的优化，显示pageSize*2+1项
      currentIndex: -1,
      pageSize: 5,
      visiableTurns: [],
      top: false,
      bottom: false,
      scrollLoading: false,
    };
  },
  computed: {
    userName() {
      return (this.user && (this.user.name || this.user.nickName)) || '用户';
    },
    busyLabel() {
      return STEP_LABEL[this.streamParam.currentStepType] || '正在思考';
    },
    agentOptions() {
      return this.agents.map((a) => ({ code: a.code, name: a.name, desc: a.desc, raw: a }));
    },
    menuOptions() {
      return [
        { key: 'new', name: '开启新会话', desc: '清空当前对话', disabled: this.streamParam.busy },
        { key: 'question', name: '推荐问题', desc: '按标签浏览示例问题' },
        { key: 'history', name: '历史会话', desc: '按时间查看往期分析' },
        //{ key: 'agent', name: '切换智能体', desc: this.agent ? this.agent.name : '' },
      ];
    },
    quickTips() {
      if (!this.turns.length) return [];
      //return ['按周聚合看看', '和上个月对比', '换成各市对比', '看看同比增速'];
      return [];
    },
  },
    watch: {
    // 监听 turns 变化
    "turns.length"(newLen, oldLen) {
      this.updateVisibleTurns();
    },
  },
  mounted() {
     this.user = getUserInfo() || {};
     //console.log(this.user)
    this.loadAgents();
  },
  beforeDestroy() {
    this.closeStream();
    if (this.scrollTimer) clearTimeout(this.scrollTimer);
  },
  methods: {
    /* ---------------- 智能体 ---------------- */
    async loadAgents() {
      this.loadingAgents = true;
      try {
        const res = await getAiBodyListAPI({ page: 1, pageSize: 20 });
        if (res && res.code === 200) {
          this.agents = (res.data && res.data.list) || [];
          if (this.agents.length && !this.agent) this.setAgent(this.agents[0]);
        }
      } catch (e) {
        /* handled */
      } finally {
        this.loadingAgents = false;
      }
    },
    setAgent(a) {
      this.resetSession();
      this.agent = a;
      this.loadTopQuestions();
    },
    onAgentPick(v) {
      const a = (v.items[0] && v.items[0].raw) || null;
      if (!a || (this.agent && a.code === this.agent.code)) return;
      this.setAgent(a);
      toast.info(`已切换到「${a.name}」`);
    },

    /* ---------------- 推荐问 ---------------- */
    async loadTopQuestions() {
      if (!this.agent) return;
      this.loadingQuestions = true;
      try {
        // 取 12 条作为「换一批」的候选池，欢迎页内部按 3 条一屏轮换
        const res = await getQuestionAPI({ aiBodyCode: this.agent.code, pageSize: 12, status: 1 });
        if (res && res.code === 200) this.topQuestions = ((res.data && res.data.list) || []).slice(0, 12);
      } catch (e) {
        /* handled */
      } finally {
        this.loadingQuestions = false;
      }
    },
    async openQuestions() {
      this.showQuestions = true;
      if (this.questionGroups.length) return;
      this.loadingQuestionTree = true;
      try {
        const [tagRes, qRes] = await Promise.all([
          getTagListAPI(),
          getQuestionAPI({ aiBodyCode: this.agent && this.agent.code, pageSize: 200, status: 1 }),
        ]);
        const tags = (tagRes && tagRes.data) || [];
        const list = (qRes && qRes.data && qRes.data.list) || [];
        this.questionGroups = tags
          .map((t) => ({
            id: t.id,
            text: t.name,
            children: list
              .filter((q) => (q.tags || []).some((x) => x.id === t.id))
              .map((q) => ({ id: q.id, text: q.question })),
          }))
          .filter((g) => g.children.length);
      } catch (e) {
        /* handled */
      } finally {
        this.loadingQuestionTree = false;
      }
    },
    onQuestionPick(v) {
      const item = v.items[0];
      if (item) this.send(item.text);
    },

    /* ---------------- 历史 ---------------- */
    async openHistory() {
      this.showHistory = true;
      this.loadingHistoryList = true;
      try {
        const res = await getChatHistoryListAPI({ aiBodyCode: this.agent && this.agent.code });
        if (res && res.code === 200) {
          this.historyGroups = groupHistory(res.data || {}, this.agent && this.agent.code);
        }
      } catch (e) {
        /* handled */
      } finally {
        this.loadingHistoryList = false;
      }
    },

    /*sleep(ms) {
      return new Promise(resolve => setTimeout(resolve, ms));
    },*/

    async onHistoryPick(v) {
      const id = v.ids[0];
      if (!id) return;
      this.loadingHistory = true;
      //this.turns = [{ uid: nextUid(), user: null, think: null, steps: [], status: 'done' }];
      try {
        const res = await getChatHistoryListDetailAPI({ chatSessionId: id });
        if (!res || res.code !== 200) return;
        
          const agent = this.agent;
          this.resetSession();
          //await this.sleep(100);
    await new Promise((resolve) => {
      requestAnimationFrame(async () => {
        try{
          this.agent = agent;
          this.sessionId = res.data.chatSessionId;
          //this.loadingHistory = true;

          const list = res.data.chatInfo || [];
          // 分帧插入，避免长会话阻塞主线程
          for (let i = 0; i < list.length; i++) {
            const turn = parseTurn(list[i]);
            this.turns.push(turn);
            //await this.sleep(100);//需要加大于80ms的等待，markdown有80s的节流

            if (i % 2 === 1){
               await this.$nextTick();  
                await this.nextFrame();
            }
          }

          this.$nextTick(() => this.scrollBottom(false));
          toast.success(`已载入 ${list.length} 轮对话`);

        } finally {
          resolve();   // 保证外层一定解除等待
        }
      });
    });
  } catch (e) {
    // handled
  } finally {
    this.loadingHistory = false; 
  }
    },
    nextFrame() {
      return new Promise((r) => requestAnimationFrame(() => setTimeout(r, 0)));
    },

    /* ---------------- 菜单 ---------------- */
    onMenuPick(v) {
      const key = v.ids[0];
      if (key === 'new') {
        if (this.streamParam.busy) return toast.warn('请先停止当前分析');
        const a = this.agent;
        this.resetSession();
        this.agent = a;
        toast.info('已开启新会话');
      } else if (key === 'question') this.openQuestions();
      else if (key === 'history') this.openHistory();
      else if (key === 'agent') this.showAgent = true;
    },

    commandMenu(v){
      //console.log(v)
      const key = v.ids[0];
      if (key === 'version') {
        this.showVersion()
      }else if(key === 'logout'){
        this.logout()
      }
    },

    showVersion(){
      Dialog.alert({
        title: '提示',
        message: "版本号:" + process.env.VUE_APP_VERSION
      }).then(() => {
      }).catch(() => {});
    },

    logout() {
      clearAuth();
      //this.$router.push('/web/login').catch(() => {});
      if(szLoginLink) window.location.href = szLoginLink;
      else this.$router.push('/web/login').catch(() => {});
      
    },

    /* ---------------- 会话核心 ---------------- */
    resetSession() {
      this.closeStream();
      this.turns = [];
      this.sessionId = null;
      this.streamParam.currentChatId = null;
      this.streamParam.busy = false;
      //this.streamParam.canStop = false;
      this.streamParam.currentStepType = '';
      this.loadingHistory = false;
      this.followBottom = true;
    },
    closeStream() {
      if (this.streamParam.chatWs) {
        this.streamParam.chatWs.close();
        this.streamParam.chatWs = null;
      }
    },
    async send(text) {
      const q = String(text || '').trim();
      if (!q) return;
      if (this.streamParam.busy) return toast.warn('正在分析中，请稍候');
      if (!this.agent) return toast.warn('请先选择智能体');

      // 用户气泡
      const turn = createTurn(null);
      turn.user = { text: q, time: this.nowText() };
      this.turns.push(turn);
      this.followBottom = true;
      this.$nextTick(() => this.scrollBottom(true));

      this.streamParam.busy = true;
      this.streamParam.currentStepType = 'think';

      try {
        const res = await sendChatAPI({
          aicode: this.agent.code,
          question: q,
          chatSessionId: this.sessionId || undefined,
        });
        if (res && res.code === 200) {
          const { chatSessionId, chatId, host } = res.data;
          this.sessionId = chatSessionId;
          this.streamParam.currentChatId = chatId;
          turn.chatId = chatId;
          openStream(chatSessionId, chatId, host, turn, this.streamParam, this.maybeFollow);
        } else {
          this.streamParam.busy = false;
          toast.error((res && res.message) || '发送失败');
        }
      } catch (e) {
        this.streamParam.busy = false;
      }
    },
  

    /*openStream(sid, chatId, host, turn) {
      this.canStop = true;
      this.chatWs = openStream({
        chatSessionId: sid,
        chatId,
        host,
        onMessage: (msg) => this.onStreamMessage(msg, sid, turn),
        onClose: () => {
          console.log('stop')
          this.chatWs = null;
          this.busy = false;
          this.canStop = false;
          this.currentStepType = '';
          if (turn.think) turn.think.done = true;
          turn.steps.forEach((s) => {
            if (s.status === 'running') s.status = 'done';
          });
          turn.status = turn.status === 'stopped' ? 'stopped' : 'done';
          this.$nextTick(() => this.scrollBottom(true));
        },
        onError: (e) => {
          this.chatWs = null;
          this.busy = false;
          this.canStop = false;
          toast.error(e && e.type === 'WebSocket timed out' ? '思考过程超时，请刷新重试' : '思考过程连接异常');
        },
      });
    },
    onStreamMessage(msg, sid, turn) {
     
      if (!msg || typeof msg !== 'object') return;
      if (msg.metadata && msg.metadata.close) return;

      const rid = msg.request_id;
      if (!rid || rid.split('@')[0] !== sid) return;

      const stepType = msg.step_type;
      const eventType = msg.event_type;
      const message = msg.message;
      if (!stepType || stepType === 'close') return;

      this.currentStepType = stepType;

      if (stepType === 'think') {
        if (!turn.think) turn.think = { title: '', text: '', done: false, open: true };
        if (eventType === 'title') turn.think.title = message;
        else if (eventType === 'line') turn.think.text += message + '\n\n';
        else if (eventType === 'token') turn.think.text += message;
        else if (eventType === 'done') {
          turn.think.done = true;
          turn.think.open = false; // 完成后自动收起
        }
        this.maybeFollow();
        return;
      }

      const itemId = Number(String(msg.step_id || '').split('_')[1] || 0);
      let step = turn.steps.find((s) => s.itemId === itemId && s.stepType === stepType);
      if (!step) {
        step = createStep(stepType, itemId);
        if (stepType === 'summarize' || stepType === 'analyze'){
          step.open = true;
        }else{
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
          this.fetchStepDetail(step, sid, turn.chatId, itemId);
          //step.open = false;
        } else if (stepType === 'analyze') {
          step.open = false; // 分析完成后收起，聚焦总结
        }
      }
      this.maybeFollow();
    },
    async fetchStepDetail(step, sid, chatId, itemId) {
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
        this.maybeFollow();
      }
    },*/

    async stopChat() {
      /*if (!this.streamParam.canStop) return;
      this.streamParam.canStop = false;
      const last = this.turns[this.turns.length - 1];
      if (last) last.status = 'stopped';
      try {
        if (this.sessionId && this.streamParam.currentChatId) {
          await stopChatAPI({ requestId: `${this.sessionId}@${this.streamParam.currentChatId}` });
        }
      } catch (e) {

      }
      this.streamParam.busy = false;
      this.streamParam.currentStepType = '';
      toast.info('已停止分析');*/

        if (this.sessionId != null && this.streamParam.currentChatId != null) {

              const last = this.turns[this.turns.length - 1];
      if (last) last.status = 'stopped';

        let params = {
          requestId: `${this.sessionId}@${this.streamParam.currentChatId}`,
        };
        await stopChatAPI(params)
          .then((response) => {
            if (response.code == 200) {
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {
          });
          toast.info('已停止分析');
      }
    },

    /* ---------------- 查询改写 ---------------- */
    async loadCandidates(step, type) {
      step.loadingCandidate = true;
      try {
        const res = await getMetricsTreeAPI({
          type: type === 'metric' ? 'metric' : 'dim',
          metricIds: step.metrics.map((m) => m.id),
          dimensionIds: step.dims.map((d) => d.id),
        });
        if (res && res.code === 200) {
          if (type === 'metric') step.candidateMetrics = res.data || [];
          else step.candidateDims = res.data || [];
        }
      } catch (e) {
        /* handled */
      } finally {
        step.loadingCandidate = false;
      }
    },
    addDim(step, v) {
      const it = v.items[0];
      if (!it) return;
      if (step.dims.some((d) => d.key === it.key)) return toast.warn('该维度已存在');
      step.dims.push({ id: it.id, key: it.key, name: it.rawName || it.name });
      step.dirty = true;
    },
    addMetric(step, v) {
      const it = v.items[0];
      if (!it) return;
      if (step.metrics.some((m) => m.key === it.key)) return toast.warn('该指标已存在');
      step.metrics.push({ id: it.id, key: it.key, name: it.rawName || it.name });
      step.dirty = true;
    },
    removeDim(step, d) {
      step.dims = step.dims.filter((x) => x.key !== d.key);
      step.filters = step.filters.filter((f) => f.key !== d.key);
      step.dirty = true;
    },
    removeMetric(step, m) {
      step.metrics = step.metrics.filter((x) => x.key !== m.key);
      step.filters = step.filters.filter((f) => f.key !== m.key);
      step.dirty = true;
    },
    addFilters(step, v) {
      (v.ids || []).forEach((raw) => {
        const [kind, key] = String(raw).split(':');
        if (step.filters.some((f) => f.key === key)) return;
        const isDim = kind === 'dim';
        const meta = (isDim ? step.dims : step.metrics).find((x) => x.key === key);
        if (!meta) return;
        step.filters.push({
          uid: nextUid(),
          type: isDim ? filterTypeEnum.dim.value : filterTypeEnum.metric.value,
          id: meta.id,
          key: meta.key,
          name: meta.name,
          operator: isDim ? 5 : 0,
          value: isDim ? [] : '',
          options: [],
          loadingOptions: false,
        });
      });
      step.dirty = true;
    },
    removeFilter(step, i) {
      step.filters.splice(i, 1);
      step.dirty = true;
    },
    async loadFilterValues(step, f) {
      if (f.options && f.options.length) return;
      if (f.type !== filterTypeEnum.dim.value || !f.id) return;
      f.loadingOptions = true;
      try {
        const res = await getDimensionValuesAPI(f.id);
        if (res && res.code === 200) f.options = res.data || [];
      } catch (e) {
        /* handled */
      } finally {
        f.loadingOptions = false;
      }
    },
    setGranularity(step, g) {
      step.granularity = g;
      step.dirty = true;
    },
    setRange(step, r) {
      step.dateRange = r;
      step.dirty = true;
    },
    resetQuery(step) {
      step.filters = [];
      step.highlightKeys = [];
      step.dirty = true;
      toast.info('已清空筛选条件');
    },
    toggleHighlight(step, key) {
      const i = step.highlightKeys.indexOf(key);
      if (i > -1) step.highlightKeys.splice(i, 1);
      else step.highlightKeys.push(key);
    },

    async applyNewQuery(step,page){
      step.queryParams = buildDataPayload(step, {
        page: page || step.data.page,
        pageSize: PAGE_SIZE,
      })
      const r = await this.applyQuery(step,page)
      if(r == false) return

      step.dirty = false;
    },

    async applyQuery(step, page) {
      if (!step.dims.length && !step.metrics.length) {
         toast.warn('请至少选择一个维度或指标');
         return false
      }

      step.loadingData = true;
      step.error = '';

      if(step.queryParams == null){
        step.queryParams = buildDataPayload(step, {
          page: page.page,
          pageSize: step.data.pageSize,
        })
      }else{
      //step.data.page = 1
        step.queryParams.page = page.page || step.data.page
      }

      //this.queryParams.page = page || step.data.page

      try {
        const res = await getMetricsDataPreviewAPI(
          step.queryParams//buildDataPayload(step, { page: page || step.data.page, pageSize: PAGE_SIZE })
        );
        if (res && res.code === 200) {
          const d = res.data;
          step.data.columns = d.columns || [];
          step.data.records = d.records || [];
          step.data.page = d.page || 1;
          step.data.pageSize = d.pageSize || PAGE_SIZE;
          step.data.total = d.total || 0;
          step.data.totalPage = Math.max(1, Math.ceil((d.total || 0) / (d.pageSize || PAGE_SIZE)));
          step.viewSql = d.sql || step.viewSql;
          //step.dirty = false;
        } else {
          step.error = (res && res.message) || '查询失败';
        }
      } catch (e) {
        step.error = '查询失败';
      } finally {
        step.loadingData = false;
      }
    },
    changePage(step, page) {
      const p = Math.min(Math.max(1, page), step.data.totalPage || 1);
      step.data.page = p;
      if (step.stepType === 'compute') repaginate(step);
      else this.applyQuery(step, p);
    },

    /* ---------------- 工具 ---------------- */
    dividerOf(turn, index) {
      return turnDivider(turn, index > 0 ? this.visiableTurns[index - 1] : null);
    },
    viewSql(step) {
      this.activeSql = step.viewSql || '';
      this.showSql = true;
    },
    async copy(text) {
      try {
        await copyToClipboard(text);
        toast.success('已复制');
      } catch (e) {
        toast.error('复制失败');
      }
    },
    nowText() {
      const d = new Date();
      const p = (n) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
    },

    /* ---------------- 滚动 ---------------- */
    onScroll() {
      /*const el = this.$refs.stream;
      if (!el) return;
      this.scrolled = el.scrollTop > 6;
      if (this.scrollTimer) clearTimeout(this.scrollTimer);
      this.scrollTimer = setTimeout(() => {
        const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 90;
        this.followBottom = nearBottom;
        this.showToTop = el.scrollTop > 420;
        this.showToBottom = !nearBottom && el.scrollHeight > el.clientHeight + 200;
      }, 90);*/

     const el = this.$refs.stream;
    if (!el) return;

      this.scrolled = el.scrollTop > 6;
      if (this.scrollTimer) clearTimeout(this.scrollTimer);

      this.$nextTick(() => {
   
        const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 90;
        this.followBottom = nearBottom;

        /*if (el.scrollHeight < 2000) {
          this.visiableTop = false;
          this.visiableBottom = false;
          return;
        }*/

        this.showToTop = !this.top;
        this.showToBottom = !this.bottom;

        if (this.top) {
          this.showToTop = el.scrollTop > 420;
        }

        if (this.bottom) {
          this.showToBottom = !nearBottom && el.scrollHeight > el.clientHeight + 200;
        }
      });

      this.optimizeMainContent();
    },
    maybeFollow() {
      if (this.followBottom) this.$nextTick(() => this.scrollBottom(false));
    },
    /*scrollBottom(smooth) {
      this.$nextTick(() => {
        const el = this.$refs.stream;
        if (!el) return;
        el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
        this.followBottom = true;
      });
    },
    scrollTo(top) {
      const el = this.$refs.stream;
      if (el) el.scrollTo({ top, behavior: 'smooth' });
    },*/

    scrollTo(top) {
      if (this.turns.length === 0) return;
      if (this.top) {
        this.$nextTick(() => {
          const el = this.$refs.stream;
          if (!el) return;
          el.scrollTo({
            top: top,
            behavior: "smooth",
          });
        });
        return;
      }

      const el = this.$refs.stream;
      if (!el) return;

      this.currentIndex = this.pageSize;
      const start = 0;
      const end = Math.min(this.pageSize * 2 + 1, this.turns.length);

      this.top = true;
      this.bottom = false;
      this.visiableTop = false;
      this.visiableBottom = false;
      this.scrollLoading = true;

      this.visiableTurns = this.turns.slice(start, end);

      this.$nextTick(() => {
        const doScroll = () => {
          const currentEl = this.$refs.stream;
          if (currentEl) {
            currentEl.scrollTop = top;
          }
        };

        doScroll();

        let retryCount = 0;
        const maxRetries = 5;

        const retryScroll = () => {
          if (retryCount >= maxRetries) {
            this.scrollLoading = false;
            return;
          }

          retryCount++;
          requestAnimationFrame(() => {
            const currentEl = this.$refs.stream;
            if (currentEl && currentEl.scrollTop > 10) {
              currentEl.scrollTop = top;
            }
            setTimeout(retryScroll, 100);
          });
        };

        setTimeout(retryScroll, 50);
      });
    },

    scrollBottom(smooth) {
      if (this.turns.length === 0) return;

      if (this.bottom) {
        this.$nextTick(() => {
          const el = this.$refs.stream;
          if (!el) return;
          el.scrollTo({
            top: el.scrollHeight,
            behavior: smooth ? "smooth" : "auto",
          });
          this.followBottom = true;
        });
        return;
      }

      const el = this.$refs.stream;
      if (!el) return;

      const totalPages = Math.ceil(this.turns.length / (this.pageSize * 2 + 1));
      this.currentIndex = Math.max(0, (totalPages - 1) * this.pageSize);
      
      const start = Math.max(0, this.currentIndex - this.pageSize);
      const end = this.turns.length;

      this.bottom = true;
      this.top = false;
      this.visiableTop = false;
      this.visiableBottom = false;
      this.scrollLoading = true;
      this.followBottom = true;

      this.visiableTurns = this.turns.slice(start, end);

      this.$nextTick(() => {
        const doScroll = () => {
          const currentEl = this.$refs.stream;
          if (currentEl) {
            currentEl.scrollTop = currentEl.scrollHeight;
          }
        };

        doScroll();

        let retryCount = 0;
        const maxRetries = 5;

        const retryScroll = () => {
          if (retryCount >= maxRetries) {
            this.scrollLoading = false;
            return;
          }

          retryCount++;
          requestAnimationFrame(() => {
            const currentEl = this.$refs.stream;
            if (currentEl) {
              const targetTop = currentEl.scrollHeight;

              if (currentEl.scrollTop < targetTop - 10) {
                currentEl.scrollTop = targetTop;
              }
            }

            setTimeout(retryScroll, 100);
          });
        };

        // 延迟开始重试，让内容先渲染一部分
        setTimeout(retryScroll, 50);
      });
    },

    optimizeMainContent() {
      if (this.scrollLoading) return;

      if (this.turns.length > this.pageSize * 2 + 1) {
        const el = this.$refs.stream;
        if (!el) return;

        const scrollTop = el.scrollTop;
        const clientHeight = el.clientHeight;
        const scrollHeight = el.scrollHeight;

        // 向上滚动加载更多
        if (scrollTop < 20 && !this.top) {
          this.scrollLoading = true;
          this.bottom = true;

          const oldFirstTurn = this.visiableTurns[0];
          const oldFirstIndex = this.turns.indexOf(oldFirstTurn);
          const oldScrollTop = scrollTop;

          this.currentIndex = Math.max(
            this.pageSize,
            this.currentIndex - this.pageSize
          );
          const start = Math.max(0, this.currentIndex - this.pageSize);
          const end =
            Math.min(this.currentIndex + this.pageSize, this.turns.length - 1) +
            1;
          this.top = start === 0;

          try {
            this.visiableTurns = this.turns.slice(start, end);
          } catch (e) {
            this.scrollLoading = false;
            return;
          }

          this.$nextTick(() => {
            try {
              const targetIndex = Math.max(0, oldFirstIndex - start);
              let newScrollTop = oldScrollTop;
              if(el.children.length>0){
                const children = el.children[0].children;
                for (let i = 0; i < targetIndex && i < children.length; i++) {
                  newScrollTop += children[i].offsetHeight || 0;
                }
                //newScrollTop += targetIndex * 20 - 40;
                el.scrollTop = Math.max(0, newScrollTop);
              }
            } catch (e) {
              // 出错时保底
            } finally {
              this.bottom = false;
              this.scrollLoading = false;
            }
          });

          return;
        }

        // 向下滚动加载更多
        if (scrollTop + clientHeight >= scrollHeight - 50 && !this.bottom) {
          this.scrollLoading = true;
          this.top = true;

          const lastVisibleTurn =
            this.visiableTurns[this.visiableTurns.length - 1];
          const lastIndex = this.turns.indexOf(lastVisibleTurn);

          this.currentIndex = Math.min(
            this.turns.length - this.pageSize - 1,
            this.currentIndex + this.pageSize + 1
          );

          const start = Math.max(0, this.currentIndex - this.pageSize);
          const end =
            Math.min(
              this.currentIndex + this.pageSize + 1,
              this.turns.length - 1
            ) + 1;
          this.bottom = end === this.turns.length;
 
          try {
            this.visiableTurns = this.turns.slice(start, lastIndex + 1);
          } catch (e) {
            this.scrollLoading = false;
            return;
          }

          this.$nextTick(() => {
            try {
              const remaining = this.turns.slice(lastIndex + 1, end);
              if (remaining.length) {
                this.visiableTurns.push(...remaining);
              }
            } catch (e) {
              // 出错时保底
            } finally {
              this.top = start === 0;
              this.scrollLoading = false;
            }
          });

          return;
        }
      }
    },

    updateVisibleTurns() {
      if (!this.turns || this.turns.length === 0) {
        this.visiableTurns = [];
        return;
      }

        this.currentIndex = Math.max(0, this.turns.length - this.pageSize - 1);


      const start = Math.max(0, this.currentIndex - this.pageSize);
      const end = Math.min(
        this.currentIndex + this.pageSize + 1,
        this.turns.length
      );
      this.visiableTurns = this.turns.slice(start, end);

      this.top = start === 0;
      this.bottom = end === this.turns.length;

      this.$nextTick(() => {
      this.scrollBottom(true)
      })
    
    },

  },
};
</script>

<style lang="scss">
@use "@/styles/web/tokens.scss" as *;
</style>
<style scoped lang="scss">
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__main {
    flex: 1;
    min-height: 0;
    position: relative;
  }

  &__inner {
    padding: 14px 12px 20px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  &__turn {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  /* 轮次时间分隔线：浅蓝细线 + 居中时间胶囊 */
  &__sep {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 2px 0 -2px;

    &::before,
    &::after {
      content: '';
      flex: 1;
      height: 1px;
      background: linear-gradient(90deg, rgba(199, 220, 251, 0), var(--c-brand-200));
    }
    &::after {
      background: linear-gradient(90deg, var(--c-brand-200), rgba(199, 220, 251, 0));
    }
  }

  &__septext {
    flex-shrink: 0;
    padding: 2px 10px;
    border-radius: var(--r-full);
    font-size: var(--fs-11);
    color: var(--c-ink-400);
    background: rgba(255, 255, 255, 0.76);
    border: 1px solid var(--bd-light);
  }

  &__histload {
    padding: 26px 0;
    display: flex;
    justify-content: center;
  }

  &__floats {
    position: absolute;
    left: 12px;
    right: 12px;
    bottom: calc(var(--safe-bottom) + 70px);
    z-index: var(--z-float);
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 9px;
    pointer-events: none;

    > * {
      pointer-events: auto;
    }
  }

  &__navs {
    display: flex;
    flex-direction: column;
    gap: 7px;
  }

  &__nav {
    width: 38px;
    height: 38px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: var(--c-ink-500);
    background: rgba(255, 255, 255, 0.88);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    border: 1px solid var(--bd-light);
    box-shadow: var(--sh-2);
    transition: all var(--dur-fast) var(--ease-out);

    &.is-accent {
      color: var(--c-brand-500);
      border-color: var(--c-brand-100);
    }
    &:active {
      transform: scale(0.93);
      background: #fff;
    }
  }
}
</style>
