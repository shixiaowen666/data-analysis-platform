<template>
  <div class="page-root">
    <!-- 头部 -->
    <div class="header">
      <div>
        <h1>问数统计报表</h1>
        <div class="sub">
          统计与分析全体用户在生产环境中的智能问数使用情况 ·
          数据来源标识：source = PRODUCTION（不含模块三批量测试数据）
        </div>
      </div>
      <div class="badges">
        <span class="badge"><span class="dot"></span>当前角色：超级管理员</span>
        <span class="badge"
          >统计时效：近实时（分钟级）[待确认，可切换为T+1]</span
        >
      </div>
    </div>

    <div class="container">
      <!-- 筛选栏 -->
      <div class="card">
        <div class="card-title">筛选条件</div>
        <div class="filter-row">
          <div class="filter-item">
            <label>时间区间</label>
            <div class="quick">
              <button
                v-for="r in quickRanges"
                :key="r.value"
                :class="{ active: quickRange === r.value }"
                @click="setQuickRange(r.value)"
              >
                {{ r.label }}
              </button>
            </div>
            <div style="display: flex; gap: 6px">
              <el-date-picker
                v-model="filters.startDate"
                type="date"
                size="small"
                value-format="yyyy-MM-dd"
                placeholder="开始日期"
                style="width: 150px"
              />
              <el-date-picker
                v-model="filters.endDate"
                type="date"
                size="small"
                value-format="yyyy-MM-dd"
                placeholder="结束日期"
                style="width: 150px"
              />
            </div>
          </div>
          <div class="filter-item">
            <label>提问用户</label>
            <el-select
              v-model="filters.userId"
              placeholder="全部用户"
              clearable
              size="small"
              style="width: 170px"
            >
              <el-option
                v-for="u in users"
                :key="u.id"
                :label="`${u.name}（${u.dept}）`"
                :value="u.id"
              />
            </el-select>
          </div>
          <div class="filter-item">
            <label>部门 [待确认]</label>
            <el-select
              v-model="filters.department"
              placeholder="全部部门"
              clearable
              size="small"
              style="width: 150px"
            >
              <el-option v-for="d in depts" :key="d" :label="d" :value="d" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>结果状态</label>
            <el-select
              v-model="filters.status"
              size="small"
              style="width: 130px"
            >
              <el-option label="全部" value="ALL" />
              <el-option label="有结果" value="SUCCESS" />
              <el-option label="无结果" value="NO_RESULT" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>命中指标/维度</label>
            <el-input
              v-model="filters.metricDim"
              placeholder="如：销售额 / 区域"
              size="small"
              style="width: 170px"
            />
          </div>
          <div class="filter-item">
            <label>问题关键词</label>
            <el-input
              v-model="filters.keyword"
              placeholder="模糊检索问题原文"
              size="small"
              style="width: 170px"
            />
          </div>
          <div class="filter-item">
            <label>&nbsp;</label>
            <el-button type="primary" size="small" @click="applyFilters"
              >查询</el-button
            >
          </div>
          <div class="filter-item">
            <label>&nbsp;</label>
            <el-button size="small" @click="resetFilters">重置</el-button>
          </div>
        </div>
      </div>

      <!-- 概览 -->
      <div class="card">
        <div class="card-title">
          概览统计 <span class="desc">{{ rangeDesc }}</span>
        </div>
        <div class="overview-grid">
          <div class="metric" v-for="(c, i) in overviewCards" :key="i">
            <div class="label">
              {{ c.label }}
              <span v-if="c.tip" class="info" :title="c.tip">i</span>
            </div>
            <div class="value">{{ c.value }}</div>
            <div class="extra" :class="c.cls">{{ c.extra }}</div>
          </div>
        </div>
      </div>

      <!-- 趋势 + 排行 -->
      <div class="two-col">
        <div class="card">
          <div class="card-title">
            提问总量与成功率趋势
            <el-select
              v-model="granularity"
              size="mini"
              style="width: 90px"
              @change="renderTrend"
            >
              <el-option label="按天" value="DAY" />
              <el-option label="按周" value="WEEK" />
              <el-option label="按月" value="MONTH" />
            </el-select>
          </div>
          <div ref="trendChart" class="chart"></div>
        </div>
        <div class="card">
          <div class="card-title">排行榜 TOP10</div>
          <div class="tabs">
            <div
              v-for="t in rankTabs"
              :key="t.value"
              class="tab"
              :class="{ active: activeRankTab === t.value }"
              @click="
                activeRankTab = t.value;
                renderRank();
              "
            >
              {{ t.label }}
            </div>
          </div>
          <ul class="rank-list">
            <li v-for="(item, idx) in rankList" :key="idx">
              <div
                class="rank-no"
                :class="
                  idx === 0 ? 'n1' : idx === 1 ? 'n2' : idx === 2 ? 'n3' : ''
                "
              >
                {{ idx + 1 }}
              </div>
              <div class="rank-name" :title="item.name">{{ item.name }}</div>
              <div class="rank-bar">
                <i :style="{ width: (item.count / rankMax) * 100 + '%' }"></i>
              </div>
              <div class="rank-count">{{ item.count }}次</div>
            </li>
            <li v-if="rankList.length === 0" class="empty">暂无数据</li>
          </ul>
        </div>
      </div>

      <!-- 热力图 + 时段分布 -->
      <div class="two-col">
        <div class="card">
          <div class="card-title">
            指标-维度组合热力图
            <span class="desc">用户最关注的分析视角组合</span>
          </div>
          <div ref="heatmapChart" class="chart"></div>
        </div>
        <div class="card">
          <div class="card-title">提问时段分布（按小时）</div>
          <div ref="hourChart" class="chart"></div>
        </div>
      </div>

      <!-- 无结果原因 -->
      <div class="card">
        <div class="card-title">无结果问题原因分布</div>
        <div class="two-col">
          <div ref="reasonChart" class="chart" style="height: 260px"></div>
          <ul class="rank-list">
            <li style="font-weight: 700">
              <span>无结果问题总数</span><span>{{ noResultTotal }} 条</span>
            </li>
            <li v-for="r in reasonList" :key="r.key">
              <span
                ><span
                  class="tag"
                  :style="{ background: r.color + '22', color: r.color }"
                  >{{ r.label }}</span
                ></span
              >
              <span>{{ r.count }} 条 · {{ r.ratio }}%</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 明细 -->
      <div class="card">
        <div class="card-title">
          问答明细列表
          <div style="display: flex; gap: 8px">
            <el-button size="mini" @click="showSkeletonDemo"
              >模拟统计生成中</el-button
            >
            <el-button size="mini" @click="exportReport"
              >导出Excel（多Sheet）</el-button
            >
          </div>
        </div>
        <div class="skeleton" v-if="skeletonVisible" style="display: flex">
          ⏳ 统计数据正在生成中，请稍后刷新重试
        </div>
    <div style="width:100%;">
<el-table
  ref="detailTable"
  :data="detailPage"
  size="mini"
  border
  stripe
  class="detail-el-table"
  :header-cell-style="headerCellStyle"
  :cell-style="cellStyle"
  :row-class-name="rowClassName"
>
  <el-table-column prop="askTime" label="提问时间" width="auto" min-width="130%" />
  <el-table-column label="用户/部门" width="auto" min-width="100%">
    <template slot-scope="scope">
      <div>{{ scope.row.username }}</div>
      <div style="color: #9ca3af; font-size: 11px">
        {{ scope.row.department }}
      </div>
    </template>
  </el-table-column>
  <el-table-column label="问题原文" width="auto" min-width="300%">
    <template slot-scope="scope">
      <span
        class="q-text"
        :title="scope.row.question"
        @click="openModal(scope.row.questionId)"
        >{{ scope.row.question }}</span
      >
    </template>
  </el-table-column>
  <el-table-column prop="parsedIntent" label="意图类型" width="auto" min-width="100%" />
  <el-table-column prop="hitTable" label="命中表" width="auto" min-width="150%" />
  <el-table-column label="命中指标" width="auto" min-width="100%">
    <template slot-scope="scope">
      <span v-for="m in scope.row.hitMetrics" :key="m" class="tag">{{ m }}</span>
      <span v-if="!scope.row.hitMetrics.length">-</span>
    </template>
  </el-table-column>
  <el-table-column label="命中维度" width="auto" min-width="100%">
    <template slot-scope="scope">
      <span v-for="m in scope.row.hitDimensions" :key="m" class="tag">{{ m }}</span>
      <span v-if="!scope.row.hitDimensions.length">-</span>
    </template>
  </el-table-column>
  <el-table-column label="结果状态" width="auto" min-width="80%">
    <template slot-scope="scope">
      <span
        class="status"
        :class="
          scope.row.resultStatus === 'SUCCESS'
            ? 'status-success'
            : 'status-fail'
        "
      >
        {{ scope.row.resultStatus === "SUCCESS" ? "有结果" : "无结果" }}
      </span>
    </template>
  </el-table-column>
  <el-table-column label="无结果原因" width="auto" min-width="100%">
    <template slot-scope="scope">{{
      scope.row.noResultReason
        ? reasonLabelMap[scope.row.noResultReason]
        : "-"
    }}</template>
  </el-table-column>
  <el-table-column prop="responseTimeMs" label="耗时(ms)" width="auto" min-width="80%" />
  <el-table-column label="反馈" width="auto" min-width="60%">
    <template slot-scope="scope">
      {{
        scope.row.feedback === "THUMBS_UP"
          ? "👍"
          : scope.row.feedback === "THUMBS_DOWN"
          ? "👎"
          : "-"
      }}
    </template>
  </el-table-column>
  <el-table-column label="操作" width="auto" min-width="80">
    <template slot-scope="scope">
      <button class="link" @click="openModal(scope.row.questionId)">
        下钻
      </button>
      <button
        v-if="scope.row.resultStatus === 'NO_RESULT'"
        class="link danger"
        @click.stop="openModal(scope.row.questionId)"
      >
        {{ scope.row.hasBeenLocated ? "已定位" : "错误定位" }}
      </button>
    </template>
  </el-table-column>
</el-table>
    </div>
        <div class="pagination">
          <span>共 {{ filteredData.length }} 条记录</span>
          <el-pagination
            background
            layout="prev, pager, next, jumper"
            :total="filteredData.length"
            :page-size="PAGE_SIZE"
            :current-page.sync="currentPage"
          />
        </div>
      </div>
    </div>

    <!-- 下钻弹窗 -->
    <el-dialog
      :visible.sync="modalVisible"
      width="880px"
      top="6vh"
      custom-class="drill-dialog"
      :show-close="true"
      @close="closeModal"
    >
      <div slot="title" class="modal-title-custom">
        问答详情 · 六环节完整过程
        <span class="modal-qid">{{ modalQId }}</span>
      </div>
      <div class="q-box">{{ modalQuestion }}</div>
      <div class="steps">
        <div
          v-for="(step, idx) in stepGrid"
          :key="idx"
          class="step"
          :class="{ fail: idx === failStep }"
        >
          <div class="step-title">
            <span>{{ stepNames[idx] }}</span>
            <span v-if="idx === failStep">✕ 失败环节</span>
          </div>
          <div class="step-content" v-html="step"></div>
        </div>
      </div>
      <div class="sql-box">{{ modalSql }}</div>
      <div
        style="
          margin-top: 16px;
          display: flex;
          justify-content: flex-end;
          gap: 10px;
        "
      >
        <el-button size="small" @click="closeModal">关闭</el-button>
        <el-button
          v-if="modalResultStatus === 'NO_RESULT'"
          type="danger"
          size="small"
          @click="jumpToLocate"
          >{{
            modalHasBeenLocated ? "查看错误定位结果" : "发起错误定位"
          }}</el-button
        >
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from "echarts";

/* ========== 模拟数据生成（source: PRODUCTION） ========== */
const users = [
  { id: "u001", name: "张伟", dept: "销售部" },
  { id: "u002", name: "李娜", dept: "财务部" },
  { id: "u003", name: "王强", dept: "运营部" },
  { id: "u004", name: "赵敏", dept: "市场部" },
  { id: "u005", name: "陈晨", dept: "供应链部" },
  { id: "u006", name: "周涛", dept: "财务部" },
];
const depts = [...new Set(users.map((u) => u.dept))];
const metricsPool = [
  "销售额",
  "订单量",
  "客户数",
  "毛利率",
  "库存周转率",
  "GMV",
  "客单价",
  "退款率",
  "活跃用户数",
];
const dimsPool = ["月份", "区域", "产品类别", "客户等级", "部门", "渠道"];
const tablesPool = [
  "dwd_sales_order",
  "dws_sales_monthly_summary",
  "ods_refund_log",
  "dwd_user_behavior",
];
const intentsPool = [
  "指标查询",
  "趋势分析",
  "对比分析",
  "排行查询",
  "明细查询",
];
const questionsPool = [
  "近三个月华东区域的销售额和订单量分别是多少",
  "本季度各产品类别的毛利率排名",
  "查询华南地区去年同期的客户增长情况",
  "库存周转率最低的十个商品是什么",
  "各部门本月费用预算执行率",
  "上周活跃用户数按天分布",
  "哪个渠道的客单价最高",
  "统计所有客户的应收账款余额",
  "2024年各月GMV同比增长趋势",
  "退款率最高的产品类别是哪个",
];
const reasonDefs = [
  { key: "EMPTY_RESULT", label: "查询结果为空", color: "#f59e0b" },
  { key: "SYSTEM_ERROR", label: "系统执行异常", color: "#dc2626" },
  { key: "INTENT_FAIL", label: "意图无法识别", color: "#8b5cf6" },
  { key: "PERMISSION_DENIED", label: "权限不足/拒答", color: "#0891b2" },
  { key: "DATA_COVERAGE", label: "数据覆盖范围不足", color: "#65a30d" },
  { key: "OTHER", label: "其他/未分类", color: "#6b7280" },
];
function randInt(a, b) {
  return Math.floor(Math.random() * (b - a + 1)) + a;
}
function pick(arr) {
  return arr[randInt(0, arr.length - 1)];
}
function fmtDate(d) {
  return d.toISOString().slice(0, 10);
}

const TODAY = new Date();
TODAY.setHours(0, 0, 0, 0);
const RANGE_DAYS = 60;
function genData() {
  const list = [];
  let seq = 1;
  for (let d = RANGE_DAYS - 1; d >= 0; d--) {
    const date = new Date(TODAY);
    date.setDate(TODAY.getDate() - d);
    const count = randInt(18, 42);
    for (let i = 0; i < count; i++) {
      const u = pick(users);
      const isSuccess = Math.random() > 0.16;
      const hour = randInt(0, 23);
      const isSplit = Math.random() > 0.78;
      const rec = {
        questionId: "Q" + String(seq).padStart(6, "0"),
        askTime: `${fmtDate(date)} ${String(hour).padStart(2, "0")}:${String(
          randInt(0, 59)
        ).padStart(2, "0")}:${String(randInt(0, 59)).padStart(2, "0")}`,
        userId: u.id,
        username: u.name,
        department: u.dept,
        question: pick(questionsPool),
        parsedIntent: pick(intentsPool),
        isSplit,
        hitTable: pick(tablesPool),
        hitMetrics: [
          pick(metricsPool),
          Math.random() > 0.55 ? pick(metricsPool) : null,
        ].filter((v, i, a) => v && a.indexOf(v) === i),
        hitDimensions: [
          pick(dimsPool),
          Math.random() > 0.5 ? pick(dimsPool) : null,
        ].filter((v, i, a) => v && a.indexOf(v) === i),
        generatedSql: `SELECT ${pick(dimsPool)}, SUM(${pick(
          metricsPool
        )}) AS val FROM ${pick(tablesPool)} WHERE dt BETWEEN '${fmtDate(
          date
        )}' AND '${fmtDate(date)}' GROUP BY ${pick(
          dimsPool
        )} ORDER BY val DESC LIMIT 10;`,
        resultStatus: isSuccess ? "SUCCESS" : "NO_RESULT",
        noResultReason: isSuccess ? null : pick(reasonDefs).key,
        responseTimeMs: randInt(700, 5200),
        summaryResult: isSuccess
          ? "根据查询结果，相关指标已按维度汇总完成，详情见图表展示。"
          : null,
        feedback:
          Math.random() > 0.68
            ? Math.random() > 0.5
              ? "THUMBS_UP"
              : "THUMBS_DOWN"
            : null,
        hasBeenLocated: !isSuccess && Math.random() > 0.7,
        source: "PRODUCTION",
      };
      list.push(rec);
      seq++;
    }
  }
  return list;
}
const allData = genData();

export default {
  name: "QuestionStatsReport",
  data() {
    return {
      filters: {
        startDate: "",
        endDate: "",
        userId: "",
        department: "",
        status: "ALL",
        metricDim: "",
        keyword: "",
      },
      quickRange: 30,
      quickRanges: [
        { label: "今日", value: 1 },
        { label: "近7天", value: 7 },
        { label: "近30天", value: 30 },
        { label: "本月", value: "month" },
      ],
      users,
      depts,
      overviewCards: [],
      rangeDesc: "",
      granularity: "DAY",
      rankTabs: [
        { label: "高频问题", value: "QUESTION" },
        { label: "指标", value: "METRIC" },
        { label: "维度", value: "DIMENSION" },
        { label: "数据表", value: "TABLE" },
        { label: "用户", value: "USER" },
      ],
      activeRankTab: "QUESTION",
      rankList: [],
      rankMax: 1,
      noResultTotal: 0,
      reasonList: [],
      filteredData: [],
      currentPage: 1,
      PAGE_SIZE: 10,
      modalVisible: false,
      modalQId: "",
      modalQuestion: "",
      modalSql: "",
      modalResultStatus: "",
      modalHasBeenLocated: false,
      failStep: -1,
      stepNames: [
        "① 意图识别",
        "② 复合问题拆分判断",
        "③ 选表",
        "④ 选指标维度",
        "⑤ SQL生成与执行",
        "⑥ 结果总结",
      ],
      stepGrid: [],
      reasonLabelMap: {},
      skeletonVisible: false,
      trendChart: null,
      heatmapChart: null,
      hourChart: null,
      reasonChart: null,
    };
  },
  computed: {
    detailPage() {
      const start = (this.currentPage - 1) * this.PAGE_SIZE;
      return this.filteredData.slice(start, start + this.PAGE_SIZE);
    },
  },
  created() {
    reasonDefs.forEach((r) => {
      this.reasonLabelMap[r.key] = r.label;
    });
    this.setRange(30, false);
    this.applyFilters();
  },
  mounted() {
    this.$nextTick(() => {
      this.trendChart = echarts.init(this.$refs.trendChart);
      this.heatmapChart = echarts.init(this.$refs.heatmapChart);
      this.hourChart = echarts.init(this.$refs.hourChart);
      this.reasonChart = echarts.init(this.$refs.reasonChart);
      this.refreshAll();
    });
    window.addEventListener("resize", this.handleResize);
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.handleResize);
    if (this.trendChart) this.trendChart.dispose();
    if (this.heatmapChart) this.heatmapChart.dispose();
    if (this.hourChart) this.hourChart.dispose();
    if (this.reasonChart) this.reasonChart.dispose();
  },
  methods: {
    /* ===== el-table 样式回调：尽量贴合原版 table.detail ===== */
    headerCellStyle() {
      return {
        background: "#fafbfc",
        color: "#6b7280",
        fontWeight: "600",
        fontSize: "12.5px",
        padding: "10px 8px",
        borderBottom: "1px solid #e5e7eb",
      };
    },
    cellStyle() {
      return {
        fontSize: "12.5px",
        padding: "10px 8px",
        color: "#1f2937",
      };
    },
    rowClassName({ rowIndex }) {
      return rowIndex % 2 === 1 ? "detail-row-stripe" : "";
    },

    setRange(days, isMonth) {
      const end = new Date(TODAY);
      let start = new Date(end);
      if (isMonth) start = new Date(end.getFullYear(), end.getMonth(), 1);
      else start.setDate(end.getDate() - days + 1);
      this.filters.startDate = fmtDate(start);
      this.filters.endDate = fmtDate(end);
    },
    setQuickRange(val) {
      this.quickRange = val;
      if (val === "month") this.setRange(0, true);
      else this.setRange(Number(val), false);
    },
    applyFilters() {
      const { startDate, endDate } = this.filters;
      const span = (new Date(endDate) - new Date(startDate)) / 86400000;
      if (span > 180) {
        this.$message.warning(
          "单次查询时间跨度不能超过180天，请调整时间范围后重试"
        );
        return;
      }
      const { userId, department, status, metricDim, keyword } = this.filters;
      this.filteredData = allData.filter((d) => {
        const dOk =
          d.askTime.slice(0, 10) >= startDate &&
          d.askTime.slice(0, 10) <= endDate;
        const uOk = !userId || d.userId === userId;
        const deptOk = !department || d.department === department;
        const sOk = status === "ALL" || d.resultStatus === status;
        const mdOk =
          !metricDim ||
          d.hitMetrics.includes(metricDim) ||
          d.hitDimensions.includes(metricDim);
        const kOk = !keyword || d.question.includes(keyword);
        return dOk && uOk && deptOk && sOk && mdOk && kOk;
      });
      if (userId && this.filteredData.length === 0) {
        this.rangeDesc = "该用户在所选时间范围内暂无提问记录";
      } else {
        this.rangeDesc = `统计区间：${startDate} ~ ${endDate}`;
      }
      this.currentPage = 1;
      this.refreshAll();
    },
    resetFilters() {
      this.filters = {
        startDate: "",
        endDate: "",
        userId: "",
        department: "",
        status: "ALL",
        metricDim: "",
        keyword: "",
      };
      this.quickRange = 30;
      this.setRange(30, false);
      this.applyFilters();
    },
    getPrevPeriodData(startDate, endDate) {
      const s = new Date(startDate);
      const e = new Date(endDate);
      const spanDays = Math.round((e - s) / 86400000) + 1;
      const prevEnd = new Date(s);
      prevEnd.setDate(s.getDate() - 1);
      const prevStart = new Date(prevEnd);
      prevStart.setDate(prevEnd.getDate() - spanDays + 1);
      return allData.filter((d) => {
        const day = d.askTime.slice(0, 10);
        return day >= fmtDate(prevStart) && day <= fmtDate(prevEnd);
      });
    },
    renderOverview() {
      const { startDate, endDate } = this.filters;
      const total = this.filteredData.length;
      const success = this.filteredData.filter(
        (d) => d.resultStatus === "SUCCESS"
      ).length;
      const noResult = total - success;
      const successRate = total ? ((success / total) * 100).toFixed(1) : "0.0";
      const uniqueQuestion = new Set(this.filteredData.map((d) => d.question))
        .size;
      const uniqueUsers = new Set(this.filteredData.map((d) => d.userId)).size;
      const avgPerUser = uniqueUsers ? (total / uniqueUsers).toFixed(1) : "0.0";
      const avgResp = total
        ? Math.round(
            this.filteredData.reduce((s, d) => s + d.responseTimeMs, 0) / total
          )
        : 0;
      const sortedRt = [...this.filteredData].sort(
        (a, b) => a.responseTimeMs - b.responseTimeMs
      );
      const p95 = sortedRt.length
        ? sortedRt[
            Math.min(sortedRt.length - 1, Math.floor(sortedRt.length * 0.95))
          ].responseTimeMs
        : 0;
      const splitCount = this.filteredData.filter((d) => d.isSplit).length;
      const splitRatio = total
        ? ((splitCount / total) * 100).toFixed(1)
        : "0.0";
      const up = this.filteredData.filter(
        (d) => d.feedback === "THUMBS_UP"
      ).length;
      const down = this.filteredData.filter(
        (d) => d.feedback === "THUMBS_DOWN"
      ).length;
      const satisfaction =
        up + down ? ((up / (up + down)) * 100).toFixed(1) : "--";

      const prev = this.getPrevPeriodData(startDate, endDate);
      const prevTotal = prev.length;
      const prevSuccess = prev.filter(
        (d) => d.resultStatus === "SUCCESS"
      ).length;
      const totalGrowth = prevTotal
        ? (((total - prevTotal) / prevTotal) * 100).toFixed(1)
        : "--";
      const prevRate = prevTotal ? (prevSuccess / prevTotal) * 100 : 0;
      const rateGrowth = prevTotal
        ? (parseFloat(successRate) - prevRate).toFixed(1)
        : "--";

      this.overviewCards = [
        {
          label: "提问总数",
          tip: "按用户实际提交次数统计，反映问数活跃度",
          value: total,
          extra: `环比 ${
            totalGrowth === "--"
              ? "--"
              : (totalGrowth >= 0 ? "▲" : "▼") + " " + totalGrowth + "%"
          }`,
          cls: totalGrowth >= 0 ? "up" : "down",
        },
        {
          label: "去重问题数",
          tip: "按标准化文本/指标/维度/时间去重，反映用户真实关注的问题种类",
          value: uniqueQuestion,
          extra: `占比 ${
            total ? ((uniqueQuestion / total) * 100).toFixed(1) : 0
          }%`,
        },
        {
          label: "有结果 / 无结果",
          value: `${success} / ${noResult}`,
          extra: `成功率 ${successRate}%`,
        },
        {
          label: "问数成功率",
          value: successRate + "%",
          extra: `环比 ${
            rateGrowth === "--"
              ? "--"
              : (rateGrowth >= 0 ? "▲" : "▼") + " " + rateGrowth + "%"
          }`,
          cls: rateGrowth >= 0 ? "up" : "down",
        },
        {
          label: "独立提问用户数",
          value: uniqueUsers,
          extra: `人均提问 ${avgPerUser} 次`,
        },
        {
          label: "平均响应耗时",
          value: avgResp + " ms",
          extra: `P95 ≈ ${p95} ms`,
        },
        {
          label: "复合问题占比",
          value: splitRatio + "%",
          extra: "触发拆分环节问题占比",
        },
        {
          label: "用户满意度",
          value: satisfaction + (satisfaction === "--" ? "" : "%"),
          extra: `👍${up} · 👎${down}`,
        },
      ];
    },
    renderTrend() {
      if (!this.trendChart) return;
      const g = this.granularity;
      const byDay = {};
      this.filteredData.forEach((d) => {
        const k = d.askTime.slice(0, 10);
        if (!byDay[k]) byDay[k] = { total: 0, success: 0 };
        byDay[k].total++;
        if (d.resultStatus === "SUCCESS") byDay[k].success++;
      });
      const days = Object.keys(byDay).sort();
      let labels = [];
      let totals = [];
      let rates = [];
      if (g === "DAY") {
        labels = days;
        totals = days.map((d) => byDay[d].total);
        rates = days.map(
          (d) => +((byDay[d].success / byDay[d].total) * 100).toFixed(1)
        );
      } else if (g === "WEEK") {
        const weekStart = (dateStr) => {
          const dt = new Date(dateStr);
          const day = (dt.getDay() + 6) % 7;
          dt.setDate(dt.getDate() - day);
          return fmtDate(dt);
        };
        const map = {};
        days.forEach((d) => {
          const w = weekStart(d);
          if (!map[w]) map[w] = { total: 0, success: 0 };
          map[w].total += byDay[d].total;
          map[w].success += byDay[d].success;
        });
        labels = Object.keys(map).sort();
        totals = labels.map((l) => map[l].total);
        rates = labels.map(
          (l) => +((map[l].success / map[l].total) * 100).toFixed(1)
        );
        labels = labels.map((l) => l + "~");
      } else {
        const map = {};
        days.forEach((d) => {
          const m = d.slice(0, 7);
          if (!map[m]) map[m] = { total: 0, success: 0 };
          map[m].total += byDay[d].total;
          map[m].success += byDay[d].success;
        });
        labels = Object.keys(map).sort();
        totals = labels.map((l) => map[l].total);
        rates = labels.map(
          (l) => +((map[l].success / map[l].total) * 100).toFixed(1)
        );
      }
      this.trendChart.setOption({
        tooltip: { trigger: "axis" },
        legend: {
          data: ["提问总量", "成功率(%)"],
          bottom: 0,
          textStyle: { fontSize: 11 },
        },
        grid: { top: 24, right: 44, bottom: 40, left: 44 },
        xAxis: {
          type: "category",
          data: labels,
          axisLabel: { fontSize: 10, rotate: labels.length > 15 ? 45 : 0 },
        },
        yAxis: [
          { type: "value", name: "提问量", axisLabel: { fontSize: 10 } },
          {
            type: "value",
            name: "成功率",
            min: 0,
            max: 100,
            axisLabel: { formatter: "{value}%", fontSize: 10 },
            splitLine: { show: false },
          },
        ],
        series: [
          {
            name: "提问总量",
            type: "bar",
            data: totals,
            itemStyle: { color: "#93c5fd", borderRadius: [4, 4, 0, 0] },
          },
          {
            name: "成功率(%)",
            type: "line",
            yAxisIndex: 1,
            smooth: true,
            data: rates,
            itemStyle: { color: "#16a34a" },
            lineStyle: { width: 3 },
          },
        ],
      });
    },
    renderRank() {
      const type = this.activeRankTab;
      const counter = {};
      this.filteredData.forEach((d) => {
        let keys = [];
        if (type === "QUESTION") keys = [d.question];
        if (type === "METRIC") keys = d.hitMetrics;
        if (type === "DIMENSION") keys = d.hitDimensions;
        if (type === "TABLE") keys = [d.hitTable];
        if (type === "USER") keys = [d.username];
        keys.forEach((k) => {
          if (k) counter[k] = (counter[k] || 0) + 1;
        });
      });
      const arr = Object.entries(counter)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10);
      this.rankMax = arr.length ? arr[0][1] : 1;
      this.rankList = arr.map(([name, count]) => ({ name, count }));
    },
    renderHeatmap() {
      if (!this.heatmapChart) return;
      const topM = metricsPool.slice(0, 6);
      const topD = dimsPool;
      const matrix = {};
      this.filteredData.forEach((d) => {
        d.hitMetrics.forEach((m) => {
          d.hitDimensions.forEach((dim) => {
            const k = m + "|" + dim;
            matrix[k] = (matrix[k] || 0) + 1;
          });
        });
      });
      const data = [];
      let max = 1;
      topM.forEach((m, i) =>
        topD.forEach((dim, j) => {
          const v = matrix[m + "|" + dim] || 0;
          if (v > max) max = v;
          data.push([j, i, v]);
        })
      );
      this.heatmapChart.setOption({
        tooltip: { position: "top" },
        grid: { top: 10, right: 20, bottom: 30, left: 80 },
        xAxis: {
          type: "category",
          data: topD,
          splitArea: { show: true },
          axisLabel: { fontSize: 10 },
        },
        yAxis: {
          type: "category",
          data: topM,
          splitArea: { show: true },
          axisLabel: { fontSize: 10 },
        },
        visualMap: {
          min: 0,
          max,
          calculable: false,
          show: false,
          inRange: { color: ["#f8fafc", "#bfdbfe", "#2f6fed"] },
        },
        series: [
          { type: "heatmap", data, label: { show: true, fontSize: 10 } },
        ],
      });
    },
    renderHourChart() {
      if (!this.hourChart) return;
      const arr = new Array(24).fill(0);
      this.filteredData.forEach((d) => {
        arr[parseInt(d.askTime.slice(11, 13))]++;
      });
      this.hourChart.setOption({
        tooltip: { trigger: "axis" },
        grid: { top: 20, right: 20, bottom: 30, left: 36 },
        xAxis: {
          type: "category",
          data: arr.map((_, i) => i + "时"),
          axisLabel: { fontSize: 9 },
        },
        yAxis: { type: "value", axisLabel: { fontSize: 10 } },
        series: [
          {
            type: "bar",
            data: arr,
            itemStyle: { color: "#60a5fa", borderRadius: [4, 4, 0, 0] },
          },
        ],
      });
    },
    renderReason() {
      if (!this.reasonChart) return;
      const noResultList = this.filteredData.filter(
        (d) => d.resultStatus === "NO_RESULT"
      );
      const total = noResultList.length;
      this.noResultTotal = total;
      const counter = {};
      reasonDefs.forEach((r) => {
        counter[r.key] = 0;
      });
      noResultList.forEach((d) => {
        counter[d.noResultReason] = (counter[d.noResultReason] || 0) + 1;
      });
      this.reasonChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}: {c} 次 ({d}%)" },
        legend: { bottom: 0, textStyle: { fontSize: 10 } },
        series: [
          {
            type: "pie",
            radius: ["42%", "70%"],
            itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
            label: { show: false },
            data: reasonDefs.map((r) => ({
              value: counter[r.key],
              name: r.label,
              itemStyle: { color: r.color },
            })),
          },
        ],
      });
      this.reasonList = reasonDefs.map((r) => {
        const c = counter[r.key];
        const ratio = total ? ((c / total) * 100).toFixed(1) : 0;
        return { key: r.key, label: r.label, color: r.color, count: c, ratio };
      });
    },
    openModal(qid) {
      const d = allData.find((x) => x.questionId === qid);
      if (!d) return;
      this.modalQId = `记录ID：${d.questionId} ｜ source: ${d.source}`;
      this.modalQuestion = `用户问题：${d.question}`;
      this.modalResultStatus = d.resultStatus;
      this.modalHasBeenLocated = d.hasBeenLocated;
      const reasonStepMap = {
        INTENT_FAIL: 0,
        PERMISSION_DENIED: 0,
        DATA_COVERAGE: 2,
        EMPTY_RESULT: 4,
        SYSTEM_ERROR: 4,
        OTHER: 5,
      };
      this.failStep =
        d.resultStatus === "NO_RESULT"
          ? reasonStepMap[d.noResultReason] ?? 5
          : -1;
      const contents = [
        `识别意图：${d.parsedIntent}<br>提问用户：${d.username}（${d.department}）`,
        `是否复合问题：${
          d.isSplit
            ? "是（已触发拆分环节）"
            : "否（原子问题，直接进入下一环节）"
        }`,
        `命中数据表：${d.hitTable}`,
        `命中指标：${d.hitMetrics.join("、") || "无"}<br>命中维度：${
          d.hitDimensions.join("、") || "无"
        }`,
        `执行状态：${
          d.resultStatus === "SUCCESS" ? "执行成功" : "执行失败/无结果"
        }${
          d.noResultReason
            ? `<br>失败原因：${this.reasonLabelMap[d.noResultReason]}`
            : ""
        }`,
        `${d.summaryResult || "无（本次问答未生成有效结果）"}<br>响应耗时：${
          d.responseTimeMs
        } ms ｜ 用户反馈：${
          d.feedback === "THUMBS_UP"
            ? "👍 点赞"
            : d.feedback === "THUMBS_DOWN"
            ? "👎 点踩"
            : "无反馈"
        }`,
      ];
      this.stepGrid = contents;
      this.modalSql = d.generatedSql;
      this.modalVisible = true;
    },
    closeModal() {
      this.modalVisible = false;
    },
    jumpToLocate() {
      this.$message.info(
        `已关联问题记录 ${this.modalQId}，正在跳转至【模块四：错误定位】...`
      );
    },
    exportReport() {
      if (this.filteredData.length > 50000) {
        this.$message.warning(
          "当前筛选条件下数据量超过50,000条上限，请缩小筛选范围"
        );
        return;
      }
      const rMap = {};
      reasonDefs.forEach((r) => {
        rMap[r.key] = r.label;
      });
      const rows = [
        [
          "提问时间",
          "用户",
          "部门",
          "问题原文",
          "意图类型",
          "命中表",
          "命中指标",
          "命中维度",
          "结果状态",
          "无结果原因",
          "响应耗时(ms)",
          "用户反馈",
        ],
        ...this.filteredData.map((d) => [
          d.askTime,
          d.username,
          d.department,
          d.question,
          d.parsedIntent,
          d.hitTable,
          d.hitMetrics.join("、"),
          d.hitDimensions.join("、"),
          d.resultStatus === "SUCCESS" ? "有结果" : "无结果",
          d.noResultReason ? rMap[d.noResultReason] : "",
          d.responseTimeMs,
          d.feedback || "",
        ]),
      ];
      const csv = rows
        .map((r) =>
          r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(",")
        )
        .join("\n");
      const blob = new Blob(["\ufeff" + csv], {
        type: "text/csv;charset=utf-8;",
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "问数统计报表_问答明细.csv";
      a.click();
      URL.revokeObjectURL(url);
      this.$message.success(
        '已下载"问答明细"CSV。完整生产环境实现应对接后端生成 Excel，包含6个Sheet。'
      );
    },
    showSkeletonDemo() {
      this.skeletonVisible = true;
      setTimeout(() => {
        this.skeletonVisible = false;
      }, 1800);
    },
    refreshAll() {
      this.renderOverview();
      this.renderTrend();
      this.renderRank();
      this.renderHeatmap();
      this.renderHourChart();
      this.renderReason();
    },
    handleResize() {
      if (this.trendChart) this.trendChart.resize();
      if (this.heatmapChart) this.heatmapChart.resize();
      if (this.hourChart) this.hourChart.resize();
      if (this.reasonChart) this.reasonChart.resize();
    },
  },
};
</script>

<style>
/* ====== 与原版保持一致的全局样式 ====== */
:root {
  --primary: #2f6fed;
  --primary-light: #eef4ff;
  --success: #16a34a;
  --success-light: #f0fdf4;
  --warning: #d97706;
  --warning-light: #fffbeb;
  --danger: #dc2626;
  --danger-light: #fef2f2;
  --text: #1f2937;
  --muted: #6b7280;
  --border: #e5e7eb;
  --bg: #f4f6fb;
  --card: #ffffff;
  --radius: 12px;
  --shadow: 0 2px 10px rgba(15, 23, 42, 0.05);
}
.page-root {
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Microsoft YaHei", Arial, sans-serif;
  font-size: 14px;
  min-height: 100vh;
}
.page-root * {
  box-sizing: border-box;
}
.header {
  background: linear-gradient(90deg, #1e3a8a, #2f6fed);
  color: #fff;
  padding: 16px 26px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.header h1 {
  margin: 0;
  font-size: 19px;
}
.header .sub {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}
.badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.badge {
  background: rgba(255, 255, 255, 0.18);
  padding: 5px 11px;
  border-radius: 14px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4ade80;
  display: inline-block;
}
.container {
  
  margin: 0 auto;
  padding: 20px 24px 60px;
        overflow-y: auto;
    height: 100vh;
}
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px 20px;
  margin-bottom: 18px;
}
.card-title {
  font-size: 15px;
  font-weight: 700;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.card-title .desc {
  font-size: 12px;
  color: var(--muted);
  font-weight: 400;
}
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-item label {
  font-size: 12px;
  color: var(--muted);
}
.quick {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
}
.quick button {
  padding: 6px 11px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 7px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
}
.quick button.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}
.metric {
  background: var(--primary-light);
  border-radius: 9px;
  padding: 14px 16px;
  position: relative;
}
.metric .label {
  font-size: 12px;
  color: var(--muted);
  display: flex;
  gap: 4px;
  align-items: center;
}
.metric .value {
  font-size: 23px;
  font-weight: 800;
  margin-top: 6px;
}
.metric .extra {
  font-size: 11px;
  color: var(--muted);
  margin-top: 6px;
}
.up {
  color: var(--success);
}
.down {
  color: var(--danger);
}
.info {
  display: inline-flex;
  width: 13px;
  height: 13px;
  border-radius: 50%;
  background: #c7d7fb;
  color: #1d4ed8;
  font-size: 10px;
  align-items: center;
  justify-content: center;
  cursor: help;
}
.two-col {
  display: grid;
  grid-template-columns: 1.7fr 1fr;
  gap: 18px;
}
.chart {
  height: 280px;
}
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.tab {
  padding: 7px 13px;
  font-size: 12.5px;
  color: var(--muted);
  border-bottom: 2px solid transparent;
  cursor: pointer;
}
.tab.active {
  color: var(--primary);
  border-color: var(--primary);
  font-weight: 700;
}
.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.rank-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
  font-size: 13px;
}
.rank-list li:last-child {
  border: 0;
}
.rank-no {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: #f0f2f5;
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.rank-no.n1 {
  background: #fee2e2;
  color: #dc2626;
}
.rank-no.n2 {
  background: #ffedd5;
  color: #d97706;
}
.rank-no.n3 {
  background: #fef9c3;
  color: #ca8a04;
}
.rank-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-bar {
  width: 70px;
  height: 6px;
  background: #eef0f4;
  border-radius: 3px;
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  background: var(--primary);
}
.rank-count {
  width: 56px;
  text-align: right;
  color: var(--muted);
  font-size: 12px;
}
.q-text {
  max-width: 230px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  cursor: pointer;
  color: var(--primary);
}
.tag {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  margin: 1px;
}
.status {
  padding: 2px 9px;
  border-radius: 12px;
  font-size: 12px;
}
.status-success {
  background: var(--success-light);
  color: var(--success);
}
.status-fail {
  background: var(--danger-light);
  color: var(--danger);
}
.link {
  color: var(--primary);
  background: none;
  border: 0;
  font-size: 12px;
  padding: 0;
  cursor: pointer;
}
.link.danger {
  color: var(--danger);
}
.empty {
  text-align: center;
  padding: 36px 0;
  color: #9ca3af;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  align-items: center;
  margin-top: 14px;
  font-size: 12px;
  color: var(--muted);
}
.skeleton {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  padding: 9px 14px;
  border-radius: 7px;
  font-size: 12px;
  margin-bottom: 12px;
  align-items: center;
  gap: 8px;
}
.q-box {
  padding: 13px 15px;
  border-left: 3px solid var(--primary);
  background: var(--primary-light);
  border-radius: 6px;
  margin-bottom: 16px;
  color: #1e3a8a;
  line-height: 1.6;
}
.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 6px;
}
.step {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 11px;
  background: #fafbfc;
}
.step.fail {
  border-color: #fca5a5;
  background: #fff5f5;
}
.step-title {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
}
.step.fail .step-title {
  color: #b91c1c;
}
.step-content {
  font-size: 12.5px;
  color: #1f2937;
  line-height: 1.55;
  word-break: break-all;
}
.sql-box {
  margin-top: 12px;
  background: #111827;
  color: #e5e7eb;
  padding: 12px;
  border-radius: 8px;
  font-family: Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow: auto;
}
.modal-title-custom {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}
.modal-qid {
  font-size: 12px;
  color: var(--muted);
  font-weight: 400;
  margin-left: 6px;
}

/* ====== el-table 微调：尽量贴合原版 table.detail 观感 ====== */
.detail-el-table.el-table {
  font-size: 12.5px;
  color: var(--text);
}
.detail-el-table.el-table th.el-table__cell {
  background: #fafbfc !important;
  color: var(--muted);
  font-weight: 600;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.detail-el-table.el-table td.el-table__cell {
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}
/* hover 行背景 */
.detail-el-table.el-table--enable-row-hover
  .el-table__body
  tr:hover
  > td.el-table__cell {
  background: #f8fafe;
}
/* 斑马纹微调，避免与 hover 冲突 */
.detail-el-table.el-table--striped
  .el-table__body
  tr.el-table__row--striped
  td.el-table__cell {
  background: #fcfdff;
}
/* 去掉 el-table 默认底边框，让整表更接近原版 */
.detail-el-table.el-table::before {
  display: none;
}

/* el-pagination 微调 */
.pagination .el-pagination {
  padding: 0;
}
.pagination .el-pagination.is-background .el-pager li:not(.disabled).active {
  background-color: var(--primary);
}

@media (max-width: 1200px) {
  .overview-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .two-col {
    grid-template-columns: 1fr;
  }
  .steps {
    grid-template-columns: 1fr;
  }
}
</style>