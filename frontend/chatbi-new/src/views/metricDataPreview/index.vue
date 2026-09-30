<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <!--<div class="tablebg" style="display: flex; flex-direction: row">-->
      <div class="tablebg">
        <el-row style="display: flex; flex-wrap: nowrap; width: 100%">
          <el-col
            :span="7"
            style="
              min-width: 200px;
              max-width: 200px;
              flex-shrink: 0;
              flex-grow: 0;
            "
          >
            <div
              style="
                background-color: white;
                border-right-style: solid;
                border-right-width: 1px;
                border-right-color: #ccc;
              "
            >
              <div>
                <div style="text-align: left; padding: 10px 20px 5px 20px">
                  <el-input
                    placeholder="请输入内容..."
                    v-model="queryTreeParams.keyword"
                    suffix-icon="el-icon-search"
                    @keyup.enter.native="handleEnter"
                  >
                  </el-input>
                </div>

                <div style="display: flex">
                  <div
                    class="one-bgdiv"
                    style="
                      display: flex;
                      align-items: center;
                      padding: 0px 30px 5px 20px;
                    "
                  >
                    <div class="vertical-line"></div>
                    <div class="title16-one-bgdiv">&nbsp;&nbsp;维度选择</div>
                  </div>
                </div>

                <div
                  style="height: 23vh; overflow-y: auto"
                  class="no-scrollbar"
                >
                  <el-tree
                    :data="dimensionsData"
                    show-checkbox
                    node-key="id"
                    :default-expanded-keys="[]"
                    :default-checked-keys="[]"
                    :props="defaultDimensionsProps"
                    @check-change="selectDimension"
                    ref="dimensionTree"
                  >
                  </el-tree>
                </div>

                <div style="display: flex">
                  <div
                    class="one-bgdiv"
                    style="
                      display: flex;
                      align-items: center;
                      padding: 10px 30px 5px 20px;
                    "
                  >
                    <div class="vertical-line"></div>
                    <div class="title16-one-bgdiv">&nbsp;&nbsp;指标选择</div>
                  </div>
                </div>

                <div
                  style="height: 23vh; overflow-y: auto"
                  class="no-scrollbar"
                >
                  <el-tree
                    :data="metricsData"
                    show-checkbox
                    node-key="id"
                    :default-expanded-keys="[]"
                    :default-checked-keys="[]"
                    :props="defaultMetricsProps"
                    @check-change="selectMetrics"
                    ref="metricsTree"
                  >
                  </el-tree>
                </div>
              </div>

              <div class="horizontal-line" style="margin: 10px 0 0px 0"></div>

              <div
                style="
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding: 0px 10px 0px 20px;
                  font-size: 14px;
                  height: 30px;
                "
              >
                <div>{{ showText }}</div>
                <div>
                  <el-button type="text" @click="clear()">清空</el-button>
                </div>
              </div>

              <div
                style="
                  display: flex;
                  flex-wrap: wrap;
                  overflow-y: auto;
                  min-height: 12vh;
                  max-height: 20vh;
                  justify-content: flex-start;
                  align-content: flex-start;
                  padding: 10px 10px 10px 20px;
                "
                class="no-scrollbar"
              >
                <el-tag
                  v-for="tag in tags"
                  :key="tag.id"
                  closable
                  type="info"
                  @close="closeTags(tag)"
                  style="margin-right: 10px; display: flex; align-items: center"
                  :style="
                    tag.type == 'dim'
                      ? 'background: #e6f1ff;'
                      : 'background: #e6f8ea;'
                  "
                  class="custom-tag"
                >
                  <el-dropdown
                    style="margin-right: 2px; margin-top: 3px"
                    v-if="tag.type == 'metrics'"
                    :ref="`more${tag.id}`"
                    trigger="manual"
                    popper-append-to-body
                    @visible-change="handleVisibleChange"
                    class="more"
                    :class="{ 'is-active': currentTagID == tag.id }"
                    placement="top-start"
                  >
                    <span
                      style="cursor: pointer"
                      @click.stop="openMore(`more${tag.id}`, tag.id)"
                    >
                      <base-icon name="more" :size="16" />
                    </span>

                    <el-dropdown-menu slot="dropdown" style="max-height: 200px">
                      <div style="min-width: 150px">
                        <div
                          style="
                            display: flex;
                            justify-content: space-between;
                            padding: 10px;
                          "
                        >
                          <div>同比</div>
                          <div>
                            <el-switch
                              v-model="tag.YoY"
                              :active-value="1"
                              :inactive-value="0"
                              @change="(val) => yoyChange(val, tag)"
                            >
                            </el-switch>
                          </div>
                        </div>
                        <div
                          style="
                            display: flex;
                            justify-content: space-between;
                            padding: 10px;
                          "
                        >
                          <div>环比</div>
                          <div>
                            <el-switch
                              v-model="tag.PoP"
                              :active-value="1"
                              :inactive-value="0"
                              @change="(val) => popChange(val, tag)"
                            >
                            </el-switch>
                          </div>
                        </div>
                      </div>
                    </el-dropdown-menu>
                  </el-dropdown>

                  <truncate-tip :text="tag.name" style="max-width: 120px" />
                </el-tag>
              </div>
            </div>
          </el-col>

          <el-col :span="17" style="flex: 1">
            <div style="width: 100%; height: 100%" ref="mainContent">
              <div
                class="searchBox"
                style="display: flex; flex-direction: column"
                ref="searchContent"
              >
                <div
                  style="
                    display: flex;
                    justify-content: space-between;
                    flex-wrap: wrap;
                    gap: 10px;
                  "
                >
                  <div style="display: flex;gap: 10px;">
                    <div style="display: flex; align-items: center;gap: 10px;">
                      <div style=" text-align: left;text-wrap: nowrap;">日期:</div>

                      <el-date-picker
                        v-model="metricDateRange"
                        type="daterange"
                        range-separator="至"
                        start-placeholder="开始日期"
                        end-placeholder="结束日期"
                        :value-format="'yyyy-MM-dd'"
                        @change="changeDateRange"
                        style="width: 250px"
                        :picker-options="pickerOptions"
                      >
                        <template slot="suffix">
                          <i class="el-input__icon el-icon-date"></i>
                        </template>
                      </el-date-picker>
                    </div>

                    <div
                      style="
                        display: flex;
                        align-items: center;
                        
                        gap: 10px;
                      "
                    >
                      <div style=" text-align: left;text-wrap: nowrap;">时间粒度:</div>

                      <el-select
                        placeholder="选择时间粒度"
                        v-model="queryParams.dateGranularity"
                        style="width: 120px"
                        @change="setGranularity"
                      >
                        <el-option
                          v-for="item in granularityEnum"
                          :key="item.value"
                          :label="item.name"
                          :value="item.value"
                        >
                        </el-option>
                      </el-select>
                    </div>
                  </div>

                  <div style="display: flex;gap: 10px;">
                    <div>
                      <el-button
                        class="toolbar-btn"
                        @click="getMetricsPreview()"
                        v-if="!dirty"
                        ><base-icon
                          name="search"
                          :size="14"
                          
                        />&nbsp;刷新</el-button
                      >
                      
                      <el-button
                        v-else
                        class="toolbar-btn"
                        @click="getNewMetricsPreview()"
                        type="warning"
                        ><base-icon
                          name="search"
                          :size="14"
                          
                        />&nbsp;应用修改</el-button
                      >
                    </div>
                    <div>
                      <el-button class="toolbar-btn" @click="reset()"
                        ><base-icon
                          name="refresh"
                          :size="14"
                        />&nbsp;重置</el-button
                      >
                    </div>
                  </div>
                </div>

                <div style="display: flex; justify-content: space-between;flex-wrap: wrap;gap: 10px;">
                  <div style="display: flex; justify-content: space-between;gap: 10px;"> 
                  <div>
                    <el-button @click="openTopNPageClick">TopN</el-button>
                  </div>
                  <div>
                    <el-button
                      @click="openFilterPageClick"
                      >过滤</el-button
                    >
                  </div>
                  <div>
                    <el-button
                      @click="openSortPageClick"
                      >排序</el-button
                    >
                  </div>

                  <div>
                    <el-button
                      @click="isSqlPage = true"
                      :disabled="metricPreviewData.sql == ''"
                      class="toolbar-btn"
                    >
                      <base-icon
                        name="file-code"
                        :size="13"
                      />&nbsp;SQL</el-button
                    >
                  </div>

                  <div>
                    <el-button
                      @click="downFile"
                      :disabled="metricPreviewData.sql == ''"
                      class="toolbar-btn"
                      ><base-icon
                        name="download"
                        :size="13"
                      />&nbsp;下载</el-button
                    >
                  </div>
                </div>
                  <div>
                    <el-button
                      type="primary"
                      @click="openProtfolioPageClick"
                      class="toolbar-btn"
                      :disabled="metricPreviewData.sql == ''"
                      ><base-icon name="save" :size="13" />&nbsp;
                      <span v-if="groupID"> 保存指标组合 </span>
                      <span v-else> 新建指标组合 </span>
                    </el-button>
                  </div>

                  <div style="width: 100%"></div>

                  <div style="display: none">
                    <el-select
                      placeholder="展示方式:表格"
                      style="width: 100px; text-align: right"
                      v-model="queryParams.chartType"
                      @change="showChart"
                    >
                      <el-option
                        v-for="item in chartTypeEnum"
                        :key="item.value"
                        :label="item.name"
                        :value="item.value"
                      >
                      </el-option>
                    </el-select>
                  </div>
                </div>
              </div>

              <div >
                <div
                  class="table-container"
                  v-if="queryParams.chartType == chartTypeEnum[1].value"
                >
                  <div class="drag-table">
                    <el-table
                      empty-text="请选择条件"
                      :data="metricPreviewData.records"
                      border
                      v-loading="loading"
                      element-loading-text="加载中..."
                      element-loading-background="rgb(248 248 248 / 50%)"
                      stripe
                    >
                      <el-table-column-with-tooltip
                        width="auto"
                        v-for="column in metricPreviewData.columns"
                        :key="column.key"
                        :prop="column.key"
                        :label="
                          column.unit
                            ? column.name + '(' + column.unit + ')'
                            : column.name
                        "
                        resizable
                        sortable
                        :render-header="renderHeader"
                      />
                    </el-table>

                    <pagination
                      v-show="queryParams.total > 0"
                      :total="queryParams.total"
                      :page.sync="queryParams.page"
                      :limit.sync="queryParams.pageSize"
                      @pagination="getMetricsPreview"
                    />
                  </div>
                </div>

                <div
                  v-if="queryParams.chartType == chartTypeEnum[2].value"
                  style="overflow-x: auto"
                >
                  <bar-chart
                    ref="chart"
                    :chartHeight="chartHeight"
                    :previewData="metricPreviewData"
                  ></bar-chart>
                </div>

                <div
                  v-if="queryParams.chartType == chartTypeEnum[3].value"
                  style="overflow-x: auto"
                >
                  <line-chart
                    ref="chart"
                    :chartHeight="chartHeight"
                    :previewData="metricPreviewData"
                  ></line-chart>
                </div>

                <div
                  v-if="queryParams.chartType == chartTypeEnum[4].value"
                  style="overflow-x: auto"
                >
                  <pie-chart
                    ref="chart"
                    :chartHeight="chartHeight"
                    :previewData="metricPreviewData"
                  ></pie-chart>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="10vh"
      title="topN配置"
      :visible.sync="isTOPNPage"
    >
      <topN-Page
        :topNData="queryParams.top"
        :dimensionsTagItem="dimensionsTag"
        :metricsTagItem="metricsTag"
        @close="closeTopNPage"
        @sureTopN="sureTopNPage"
        v-if="isTOPNPage"
      />
    </el-dialog>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="10vh"
      title="过滤配置"
      :visible.sync="isFiltersPage"
    >
      <filter-Page
        :filterData="queryParams.filters"
        :dimensionsTagItem="dimensionsTag"
        :metricsTagItem="metricsTag"
        @close="closeFilterPage"
        @sureFilter="sureFilterPage"
        v-if="isFiltersPage"
      />
    </el-dialog>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="10vh"
      title="排序配置"
      :visible.sync="isSortPage"
    >
      <sort-Page
        :sortData="queryParams.orderList"
        :dimensionsTagItem="dimensionsTag"
        :metricsTagItem="metricsTag"
        @close="closeSortPage"
        @sureSort="sureSortPage"
        v-if="isSortPage"
      />
    </el-dialog>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="20vh"
      :title="groupID ? '保存指标组合' : '新建指标组合'"
      :visible.sync="isProtfolioPage"
    >
      <newProtfolio-Page
        :query="tempQuery"
        :groupItem="groupItem"
        :metricList="metricsTag"
        @close="closeProtfolioPage"
        @sureProtfolio="sureProtfolioPage"
        v-if="isProtfolioPage"
      />
    </el-dialog>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="10vh"
      title="SQL"
      width="60%"
      :modal="false"
      :visible.sync="isSqlPage"
    >
      <div class="table-container">
        <sql-panel :sql="metricPreviewData.sql" title="本次查询语句" />
      </div>

 

      <div class="horizontal-line"></div>
      <div style="display: flex;">
        <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
          <el-button @click="isSqlPage = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  getMetricsTreeAPI,
  getMetricsDataPreviewAPI,
  granularityEnum,
  chartTypeEnum,
} from "@/api/metricDataPreview/metricPreviewAPI.js";
import { getGroupDetailAPI } from "@/api/portfolioManager/portfolioAPI.js";

import topNPage from "@/views/metricDataPreview/topNPage";
import filterPage from "@/views/metricDataPreview/filterPage";
import sortPage from "@/views/metricDataPreview/sortPage";
import newProtfolioPage from "@/views/metricDataPreview/newProtfolioPage";

import TruncateTip from "@/components/TruncateTip";
import SqlPanel from "@/components/SqlPanel";

import { shortcutDate, formatDate, formatDateTime } from "@/utils/common";

import BarChart from "@/components/eCharts/BarCharts";
import LineChart from "@/components/eCharts/LineCharts";
import PieChart from "@/components/eCharts/PieCharts";

import toast from "@/utils/toast";

export default {
  name: "metricDataPreview",
  components: {
    topNPage,
    filterPage,
    sortPage,
    newProtfolioPage,
    SqlPanel,
    TruncateTip,

    PieChart,
    LineChart,
    BarChart,
  },

  props: {
    // 基础类型
    groupID: {
      type: Number,
      default: null,
    },
  },

  data() {
    return {
      isSilentUpdate: false,
      currentTagID: null,

      pickerOptions: {
        shortcuts: shortcutDate["day"],
      }, //日期选择快捷键

      granularityEnum,
      chartTypeEnum,

      chartData: [],
      chartHeight: 0,

      loading: false,

      metricDateRange: [
        formatDate(),
        formatDate(),
      ],

      isTOPNPage: false,
      isFiltersPage: false,
      isSortPage: false,
      isProtfolioPage: false,
      isSqlPage: false,

      queryTreeParams: {
        keyword: "",
        metricIds: [],
        dimensionIds: [],
      },

      //维度数据列表
      dimensionsData: [],

      //指标数据列表
      metricsData: [],

      queryParams: {
        dimList: [],
        indexList: [],

        timeRange: {
          start: formatDate(),
          end: formatDate(),
        }, //日期
        dateGranularity: "day",
        filters: [],
        orderList: [],
        top: {
          groupDims: [{ id: null }],
          orders: [{ id: null, key: "", name: "", order: "desc" }],
          topNum: 10,
        },

        downloadFlag: 0,
        chartType: 1,

        page: 1,
        pageSize: 10,
        total: 0,
      },

      tempQuery: null,

      //列表数据
      metricPreviewData: {
        sql: "",
        records: [],
        columns: [],
      },

      //Tree的变量
      tags: [],

      defaultDimensionsProps: {
        children: "children",
        label: "name",
      },
      dimensionsTag: [],
      dimensionCheckedNodes: [],

      defaultMetricsProps: {
        children: "items",
        label: "name",
      },
      metricsTag: [],
      metricsCheckedNodes: [],

      groupItem: {
        id: null,
        groupCode: "",
        groupName: "",
        description: "",
      },

      dirty: false,
    };
  },

  computed: {
    showText: function () {
      return `已选项(${this.tags.length})`;
    },
  },

  async mounted() {
    if (this.groupID == null) {
      await this.getMetricTreeList();
    } else {
      this.getGroupDetail();
    }
    window.addEventListener("resize", this.computeChartsHeight);
  },

  beforeDestroy() {
    window.removeEventListener("resize", this.computeChartsHeight);
  },

  methods: {
    renderHeader(h, { column }) {
      return h(
        "el-tooltip",
        {
          props: {
            content: column.label,
            placement: this.$toolTipPlacement,
            effect: this.$toolTipEffect,
            openDelay: this.$toolTipOpenDelay,
          },
        },
        [
          h(
            "span",
            {
              style: { maxWidth: "100%" },
            },
            column.label
          ),
        ]
      );
    },

    getGroupDetail() {
      if (!this.groupID) return;

      getGroupDetailAPI(this.groupID)
        .then((response) => {
          if (response.code == 200) {
            const data = response.data;
            this.groupItem.id = data.id;
            this.groupItem.groupCode = data.groupCode;
            this.groupItem.groupName = data.groupName;
            this.groupItem.description = data.description;
            const groupConfig = JSON.parse(data.groupConfig);

            this.$set(this, "metricDateRange", [
              groupConfig.timeRange.start,
              groupConfig.timeRange.end,
            ]);
            this.$set(
              this.queryParams,
              "dateGranularity",
              groupConfig.timeGranularity
            );

            this.queryParams.filters = groupConfig.filters;
            this.queryParams.orderList = groupConfig.sorts;
            if (groupConfig.topN != null) {
              this.queryParams.top = groupConfig.topN;
            }

            this.dimensionsTag = [];
            for (let i = 0; i < groupConfig.dimensions.length; i++) {
              let temp = groupConfig.dimensions[i];
              temp.type = "dim";
              this.dimensionsTag.push(temp);
            }

            this.metricsTag = [];
            for (let i = 0; i < groupConfig.indicators.length; i++) {
              let temp = groupConfig.indicators[i];
              temp.type = "metrics";
              temp.YoY = 0;
              temp.PoP = 0;

              let node = groupConfig.indicatorComparison[temp.id];
              if (node !== undefined) {
                if (node == "both") {
                  temp.YoY = 1;
                  temp.PoP = 1;
                } else if (node == "yoy") {
                  temp.YoY = 1;
                } else if (node == "pop") {
                  temp.PoP = 1;
                }
              }
              this.metricsTag.push(temp);
            }

            this.tags = [];
            for (let i = 0; i < this.metricsTag.length; i++) {
              this.tags.push(this.metricsTag[i]);
              this.queryTreeParams.metricIds.push(this.metricsTag[i].id);
            }
            for (let i = 0; i < this.dimensionsTag.length; i++) {
              this.tags.push(this.dimensionsTag[i]);
              this.queryTreeParams.dimensionIds.push(this.dimensionsTag[i].id);
            }

            /*let dimChecked = []
            for(let i = 0;i < groupConfig.dimensions.length;i++){
              //this.$refs.dimensionTree.setChecked(groupConfig.dimensions[i].id,true,false)
              dimChecked.push(groupConfig.dimensions[i].id)
            }
            if(dimChecked.length>0){
              this.$refs.dimensionTree.setCheckedKeys(dimChecked)
              await this.sleep(600)
            }
            let inkChecked = []
            for(let i = 0;i < groupConfig.indicators.length;i++){
              //this.$refs.metricsTree.setChecked(groupConfig.indicators[i].id,true,false)
              inkChecked.push(groupConfig.indicators[i].id)
              //await this.sleep(600)
            }
            if(inkChecked.length>0){
              this.$refs.metricsTree.setCheckedKeys(inkChecked)
              await this.sleep(600)
            }*/

            /*Object.keys(groupConfig.indicatorComparison).forEach(key => {
              let index = this.tags.findIndex(item => String(item.id) === String(key))
              if(index > -1){
                if(groupConfig.indicatorComparison[key] == 'both'){
                  this.metricsTag[index].YoY = 1
                  this.metricsTag[index].PoP = 1
                }else if(groupConfig.indicatorComparison[key] == 'yoy'){
                  this.metricsTag[index].YoY = 1
                }else if(groupConfig.indicatorComparison[key] == 'pop'){
                  this.metricsTag[index].PoP = 1
                }
              }
            })*/

            this.getMetricTreeList();
            this.getNewMetricsPreview();
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    maybeDirty(){
      if (
        this.queryParams.dimList.length == 0 &&
        this.queryParams.indexList.length == 0
      ){
        return
      }
      this.dirty=true
    },

    setGranularity() {
      this.pickerOptions.shortcuts =
        shortcutDate[this.queryParams.dateGranularity];
      
      this.maybeDirty()
    },

    handleVisibleChange(visible) {
      if (!visible) {
        this.currentTagID = null;
      }
    },

    yoyChange(val, tag) {
      this.getNewMetricsPreview();
    },
    popChange(val, tag) {
      this.getNewMetricsPreview();
    },

    async openMore(name, tagID) {
      if (this.$refs[name] && this.$refs[name][0].show) {
        this.$refs[name][0].show();
      }
      this.currentTagID = tagID;
    },

    computeChartsHeight() {
      const mainElement = this.$refs.mainContent; // 假设你在模板中有一个类名为content的元素
      const searchElement = this.$refs.searchContent;
      if (mainElement && searchElement) {
        this.chartHeight =
          mainElement.clientHeight - searchElement.clientHeight - 40;

        this.$nextTick(() => {
          if (this.$refs.chart) {
            this.$refs.chart.resize();
          }
        });
      }
    },

    showChart() {
      if (this.queryParams.chartType == chartTypeEnum[1].value) {
        //表格
      } else {
        this.$nextTick(() => {
          if (this.$refs.chart) {
            this.$refs.chart.initChart();
            this.computeChartsHeight();
          }
        });
      }
    },

    changeDateRange(value) {
      this.maybeDirty()
    },

    openProtfolioPageClick() {
      //this.tempQuery = this.getQueryParams();
      this.isProtfolioPage = true;
    },

    sureProtfolioPage(data) {
      this.isProtfolioPage = false;
      if(this.groupItem.id) this.$emit("closelink", '编辑组合指标--' + this.groupItem.groupName);
      else this.$emit("closelink", '指标数据预览');
    },

    closeProtfolioPage() {
      this.isProtfolioPage = false;
    },

    openTopNPageClick() {
      this.isTOPNPage = true;
    },

    sureTopNPage(data) {
      this.maybeDirty()
      this.queryParams.top = data;
      this.isTOPNPage = false;
    },

    closeTopNPage() {
      this.isTOPNPage = false;
    },

    openFilterPageClick() {
      this.isFiltersPage = true;
    },

    sureFilterPage(data) {
      this.maybeDirty()
      this.queryParams.filters = data;
      this.isFiltersPage = false;
    },

    closeFilterPage() {
      this.isFiltersPage = false;
    },

    openSortPageClick() {
      this.isSortPage = true;
    },

    sureSortPage(data) {
      this.maybeDirty()
      this.queryParams.orderList = data;
      this.isSortPage = false;
    },

    closeSortPage() {
      this.isSortPage = false;
    },

    //左侧指标、维度树
    async getMetricTreeList() {
      let rv = false;
      let dimmensionTreeParams = this.queryTreeParams;
      dimmensionTreeParams.type = "dim";
      await getMetricsTreeAPI(dimmensionTreeParams)
        .then((response) => {
          if (response.code == 200) {
            this.dimensionsData = response.data;
            rv = true;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});

      if (!rv) return rv;

      let metricsTreeParams = this.queryTreeParams;
      metricsTreeParams.type = "metric";
      await getMetricsTreeAPI(metricsTreeParams)
        .then((response) => {
          if (response.code == 200) {
            this.metricsData = response.data;
            rv = true;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
      return rv;
    },

    handleEnter() {
      this.getMetricTreeList();
    },

    getNewMetricsPreview(){
      this.queryParams.page = 1
      this.getMetricsPreview()
    },

    getQueryParams() {
      this.queryParams.downloadFlag = 0;
      this.queryParams.indicatorComparison = {};

      this.queryParams.dimList = [];
      for (let i = 0; i < this.dimensionsTag.length; i++) {
        let data = {
          id: this.dimensionsTag[i].id,
          dimKey: this.dimensionsTag[i].key,
          dimName: this.dimensionsTag[i].name,
        };
        this.queryParams.dimList.push(data);
      }
      this.queryParams.indexList = [];
      for (let i = 0; i < this.metricsTag.length; i++) {
        let data = {
          id: this.metricsTag[i].id,
          indKey: this.metricsTag[i].key,
          indName: this.metricsTag[i].name,
        };

        let v = "none";
        if (this.metricsTag[i].YoY && this.metricsTag[i].PoP) {
          v = "both";
        } else if (this.metricsTag[i].YoY) {
          v = "yoy";
        } else if (this.metricsTag[i].PoP) {
          v = "pop";
        }

        this.$set(
          this.queryParams.indicatorComparison,
          this.metricsTag[i].id,
          v
        );

        this.queryParams.indexList.push(data);
      }

      if (
        this.queryParams.dimList.length == 0 &&
        this.queryParams.indexList.length == 0
      ) {
        toast.error("请选择指标或维度");
        return null;
      }

      if (this.metricDateRange == null) {
        this.queryParams.timeRange = { start: "", end: "" };
      } else {
        this.queryParams.timeRange = {
          start: this.metricDateRange[0],
          end: this.metricDateRange[1],
        };
      }

      //top没有不能传
      let tempQuery = JSON.parse(JSON.stringify(this.queryParams));

      tempQuery.top = {};
      if (this.queryParams.top.orders[0]?.id) {
        this.$set(tempQuery.top, "orders", [
          {
            id: this.queryParams.top.orders[0].id,
            key: this.queryParams.top.orders[0].key,
            name: this.queryParams.top.orders[0].name,
            order: this.queryParams.top.orders[0].order,
          },
        ]);
        this.$set(tempQuery.top, "topNum", this.queryParams.top.topNum);
      }
      if (this.queryParams.top.groupDims[0]?.id) {
        this.$set(tempQuery.top, "groupDims", [
          { id: this.queryParams.top.groupDims[0].id },
        ]);
      }

      for(let i=0;i<tempQuery.filters.length;i++){
        delete tempQuery.filters[i].options
      }
      return tempQuery;
    },

    //获取预览
    getMetricsPreview() {
      this.tempQuery = this.getQueryParams();
      if (!this.tempQuery) return;

      this.loading = true;
      getMetricsDataPreviewAPI(this.tempQuery)
        .then((response) => {
          if (response.code == 200) {
            this.metricPreviewData.sql = "";
            this.metricPreviewData.records = [];
            this.metricPreviewData.columns = [];

            this.queryParams.total = response.data.total;
            this.queryParams.page = response.data.page;
            this.queryParams.pageSize = response.data.pageSize;
            this.metricPreviewData = response.data;

            this.dirty = false
            this.showChart();
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false;
        });
    },

    //下载文件
    downFile() {
      let tempQuery = JSON.parse(JSON.stringify(this.tempQuery));
      if (!tempQuery) return;
      tempQuery.downloadFlag = 1;

      this.download(
        "/api/v1/chat-server/getdata",
        tempQuery,
        `数据导出_${formatDateTime()}.xlsx`
      );
    },

    //选择维度
    async selectDimension(data, checked, node) {
      if (this.isSilentUpdate) return;
      const checkedTreeNode = this.getDimensionCheckedTreeNodes();
      this.dimensionCheckedNodes = checkedTreeNode;

      let ids = [];
      for (let i = 0; i < checkedTreeNode.length; i++) {
        let node = { ...checkedTreeNode[i] };
        node.type = "dim";
        let index = this.dimensionsTag.findIndex((item) => item.id == node.id);
        if (index < 0) {
          this.dimensionsTag.push(node);
          this.queryTreeParams.dimensionIds.push(checkedTreeNode[i].id); //每次点击都需要刷新树，把选的加进去
          ids.push(checkedTreeNode[i].id);
        }
      }

      const rv = await this.getMetricTreeList(); //每次点击都需要刷新树，把选的加进去
      if (!rv) {
        //失败得话把已添加得减去
        for (let i = 0; i < ids.length; i++) {
          this.queryTreeParams.dimensionIds =
            this.queryTreeParams.dimensionIds.filter((item) => item != ids[i]);
          this.dimensionsTag = this.dimensionsTag.filter(
            (item) => item.id != ids[i]
          );
        }
        this.clearDimensionAll();
        return;
      }
      this.getNewMetricsPreview();

      this.tags = [];
      for (let i = 0; i < this.metricsTag.length; i++) {
        this.tags.push(this.metricsTag[i]);
      }
      for (let i = 0; i < this.dimensionsTag.length; i++) {
        this.tags.push(this.dimensionsTag[i]);
      }

      //this.getMetricTreeList(); //每次点击都需要刷新树，把选的加进去
    },

    clearDimensionAll() {
      this.isSilentUpdate = true;
      // 清空所有选中节点
      this.$refs.dimensionTree.setCheckedKeys([]);
      this.$nextTick(() => {
        this.isSilentUpdate = false;
      });
    },

    //获取选择的维度树
    getDimensionCheckedTreeNodes() {
      let checkedTreeNode = [];
      const allCheckedNodes = this.$refs.dimensionTree.getCheckedNodes(); // 获取所有选中的节点（包括半选节点）

      for (let i = 0; i < allCheckedNodes.length; i++) {
        if (allCheckedNodes[i].id != null) {
          checkedTreeNode.push(allCheckedNodes[i]);
        }
      }
      return checkedTreeNode;
    },

    //选择指标
    async selectMetrics(data, checked, node) {
      if (this.isSilentUpdate) return;
      const checkedTreeNode = this.getMetricsCheckedTreeNodes();
      this.metricsCheckedNodes = checkedTreeNode;

      let ids = [];
      for (let i = 0; i < checkedTreeNode.length; i++) {
        let node = { ...checkedTreeNode[i] };
        node.type = "metrics";
        node.YoY = 0;
        node.PoP = 0;
        let index = this.metricsTag.findIndex((item) => item.id == node.id);
        if (index < 0) {
          this.metricsTag.push(node);
          this.queryTreeParams.metricIds.push(checkedTreeNode[i].id); //每次点击都需要刷新树，把选的加进去
          ids.push(checkedTreeNode[i].id);
        }
      }

      const rv = await this.getMetricTreeList(); //每次点击都需要刷新树，把选的加进去
      if (!rv) {
        //失败得话把已添加得减去
        for (let i = 0; i < ids.length; i++) {
          this.queryTreeParams.metricIds =
            this.queryTreeParams.metricIds.filter((item) => item != ids[i]);
          this.metricsTag = this.metricsTag.filter((item) => item.id != ids[i]);
        }
        this.clearMetricsAll();
        return;
      }
      this.getNewMetricsPreview();

      this.tags = [];
      for (let i = 0; i < this.metricsTag.length; i++) {
        this.tags.push(this.metricsTag[i]);
      }
      for (let i = 0; i < this.dimensionsTag.length; i++) {
        this.tags.push(this.dimensionsTag[i]);
      }
    },

    clearMetricsAll() {
      this.isSilentUpdate = true;
      // 清空所有选中节点
      this.$refs.metricsTree.setCheckedKeys([]);
      this.$nextTick(() => {
        this.isSilentUpdate = false;
      });
    },

    //获取选择的指标树
    getMetricsCheckedTreeNodes() {
      let checkedTreeNode = [];
      const allCheckedNodes = this.$refs.metricsTree.getCheckedNodes(); // 获取所有选中的节点（包括半选节点）

      for (let i = 0; i < allCheckedNodes.length; i++) {
        if (allCheckedNodes[i].id != null) {
          checkedTreeNode.push(allCheckedNodes[i]);
        }
      }
      return checkedTreeNode;
    },

    reset() {
      this.metricDateRange = [
        formatDate(),
        formatDate(),
      ];

      this.queryParams.timeRange = {
        start: formatDate(),
        end: formatDate(),
      };

      this.queryParams.dateGranularity = "day";
      this.queryParams.filters = [];
      this.queryParams.orderList = [];
      this.queryParams.top.groupDims = [{ id: null }];
      this.queryParams.top.orders = [
        { id: null, key: "", name: "", order: "desc" },
      ];
      this.queryParams.top.topNum = 10;
      this.queryParams.chartType = 1;

      this.dimList = [];
      this.indexList = [];

      this.dimensionsTag = [];
      this.metricsTag = [];
      this.tags = [];

      this.clear();
    },

    //清空 需要同步树，以及表格清空
    clear() {
      this.dimensionCheckedNodes = [];
      this.metricsCheckedNodes = [];
      this.$refs.dimensionTree.setCheckedKeys([]);
      this.$refs.metricsTree.setCheckedKeys([]);

      this.tags = [];

      this.dimensionsTag = [];
      this.metricsTag = [];

      this.queryTreeParams.dimensionIds = [];
      this.queryTreeParams.metricIds = [];
      this.getMetricTreeList();

      this.metricPreviewData.sql = "";
      this.metricPreviewData.records = [];
      this.metricPreviewData.columns = [];
      this.queryParams.page = 1;
      this.queryParams.total = 0;
      this.queryParams.pageSize = 10;
    },

    //关闭标签
    closeTags(tag) {
      this.tags.splice(this.tags.indexOf(tag), 1);
      let index = this.metricsTag.indexOf(tag);
      if (index > -1) {
        const tag = this.metricsTag[index]
       

        let indexTemp = this.queryParams.filters.findIndex(item=>item.filterField.id == tag.id)

        if(indexTemp > -1){
          this.queryParams.filters.splice(indexTemp,1)
        }

        indexTemp = this.queryParams.top.groupDims.findIndex(item=>item.id == tag.id)
        if(indexTemp > -1){
          this.queryParams.top.groupDims.splice(indexTemp,1)
        }
        if(this.queryParams.top.groupDims.length == 0){
          this.queryParams.top.groupDims.push({ id: null })
        }

        indexTemp = this.queryParams.top.orders.findIndex(item=>item.id === tag.id)
        if(indexTemp > -1){
          this.queryParams.top.orders.splice(indexTemp,1)
        }
        if(this.queryParams.top.orders.length == 0){
          this.queryParams.top.orders.push({ id: null, key: "", name: "", order: "desc" })
        }

        indexTemp = this.queryParams.orderList.findIndex(item=>item.id == tag.id)
        if(indexTemp > -1){
          this.queryParams.orderList.splice(indexTemp,1)
        }

        this.metricsTag.splice(index, 1);
      }
      index = this.dimensionsTag.indexOf(tag);
      if (index > -1) {
        const tag = this.dimensionsTag[index]

        let indexTemp = this.queryParams.filters.findIndex(item=>item.filterField.id == tag.id)

        if(indexTemp > -1){
          this.queryParams.filters.splice(indexTemp,1)
        }

        indexTemp = this.queryParams.top.groupDims.findIndex(item=>item.id == tag.id)
        if(indexTemp > -1){
          this.queryParams.top.groupDims.splice(indexTemp,1)
        }
        if(this.queryParams.top.groupDims.length == 0){
          this.queryParams.top.groupDims.push({ id: null })
        }

        indexTemp = this.queryParams.top.orders.findIndex(item=>item.id === tag.id)
        if(indexTemp > -1){
          this.queryParams.top.orders.splice(indexTemp,1)
        }
        if(this.queryParams.top.orders.length == 0){
          this.queryParams.top.orders.push({ id: null, key: "", name: "", order: "desc" })
        }

        indexTemp = this.queryParams.orderList.findIndex(item=>item.id == tag.id)
        if(indexTemp > -1){
          this.queryParams.orderList.splice(indexTemp,1)
        }

        this.dimensionsTag.splice(index, 1);
      }

      index = this.dimensionCheckedNodes.indexOf(tag);
      if (index != -1) {
        this.dimensionCheckedNodes.splice(index, 1);
        let checkID = [];
        for (let i = 0; i < this.dimensionCheckedNodes.length; i++) {
          checkID.push(this.dimensionCheckedNodes[i].id);
        }

        this.$refs.dimensionTree.setCheckedKeys(checkID);
      }

      index = this.metricsCheckedNodes.indexOf(tag);
      if (index != -1) {
        this.metricsCheckedNodes.splice(index, 1);
        let checkID = [];
        for (let i = 0; i < this.metricsCheckedNodes.length; i++) {
          checkID.push(this.metricsCheckedNodes[i].id);
        }

        this.$refs.metricsTree.setCheckedKeys(checkID);
      }

      index = this.queryTreeParams.metricIds.indexOf(tag.id);
      if (index > -1) {
        this.queryTreeParams.metricIds.splice(index, 1);
      }
      index = this.queryTreeParams.dimensionIds.indexOf(tag.id);
      if (index > -1) {
        this.queryTreeParams.dimensionIds.splice(index, 1);
      }

      this.getMetricTreeList();
      this.getNewMetricsPreview();
    },
  },
};
</script>

<style lang="scss">
.custom-tag.el-tag--info {
  margin-bottom: 10px !important;
}

.custom-tag .el-icon-close {
  top: 0px !important;
  right: -2px !important;
}
</style>

 <style scoped lang="scss">


.more {
  &:hover {
    background: rgba(0, 0, 0, 0.06);
    color: #111827;
  }
  &.is-active {
    background: rgba(0, 0, 0, 0.08);
    color: #111827;
  }
}
</style>