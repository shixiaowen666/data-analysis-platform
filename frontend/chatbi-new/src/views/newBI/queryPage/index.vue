<template>
  <div
    style="
      width: 100%;
      height: 100%;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      position: relative;
      justify-content: space-between;
    "
    class="my-custom-style"
  >
    <welcomePage
      v-if="!turns.length"
      class="my-custom-style"
      :agent-name="currentAgent ? currentAgent.name : ''"
      :questions="topQuestion"
      @send="send"
      style="height: 100%"
    />

    <div
      v-if="turns.length"
      ref="mainContent"
      style="width: 100%; height: 100vh; overflow-y: auto; margin-bottom: 25px"
      class="no-scrollbar"
      @scroll="handleScroll"
    >
      <div
        v-for="(turn, index) in visiableTurns"
        :key="turn.uid"
        style="margin-top: 20px"
      >
        <div v-if="dividerOf(turn, index)" class="chat__sep">
          <span class="chat__septext">{{ dividerOf(turn, index) }}</span>
        </div>

        <div
          style="display: flex; margin-top: 20px; justify-content: flex-end"
          v-if="turn.user"
        >
          <div style="width: 100%">
            <div style="display: flex; justify-content: flex-end">
              <div class="user-Panel">
                {{ turn.user?.text }}
              </div>
              <div
                style="text-align: right; margin-left: 10px; margin-right: 10px"
              >
                <el-avatar
                  :size="32"
                  :style="{ backgroundColor: '#0073FF', color: 'white' }"
                >
                  {{ avatarText }}
                </el-avatar>
              </div>
            </div>

            <div
              style="
                display: flex;
                justify-content: flex-end;
                font-size: 10px;
                color: silver;
                margin-right: 50px;
                margin-top: 4px;
                align-items: center;
              "
            >
              <div style="margin-right: 10px">
                {{ turn.user?.time }}
              </div>

              <div>
                <el-tooltip
                  content="复制"
                  :placement="$toolTipPlacement"
                  :effect="$toolTipEffect"
                  :open-delay="$toolTipOpenDelay"
                >
                  <el-button
                    type="text"
                    class="op-icon-btn"
                    style="height: 20px; padding: 4px !important"
                    @click="copyToClipboard(turn.user?.text)"
                  >
                    <base-icon name="copy" :size="10" />
                  </el-button>
                </el-tooltip>

                <el-tooltip
                  content="重试"
                  :placement="$toolTipPlacement"
                  :effect="$toolTipEffect"
                  :open-delay="$toolTipOpenDelay"
                >
                  <el-button
                    type="text"
                    class="op-icon-btn"
                    style="
                      height: 20px;
                      padding: 4px !important;
                      margin-left: 0px;
                    "
                    @click="handleChatSend(turn.user?.text)"
                    
                  >
                    <base-icon
                      name="refresh"
                      :size="10"
                      
                    />
                  </el-button>
                </el-tooltip>
              </div>
            </div>
          </div>
        </div>

        <div
          style="
            text-align: left;
            margin-left: 10px;
            display: flex;
            align-items: center;
            margin-bottom: 10px;
          "
          v-if="turn.think || turn.step?.length > 0"
        >
          <div style="display: flex; align-items: center">
            <img class="agent-logo" src="@/assets/svgs/CSG.svg" alt="" />
          </div>
          <div>{{ currentAgent.name }}</div>
        </div>

        <div class="ai-Panel">
          <div style="margin: 10px 20px" v-if="turn.think">
            <span v-if="turn.loading" class="step__scan"></span>
            <el-collapse
              class="ai-Think-Collapse"
              :value="turn.think.openPanel"
              :ref="`thinkVisible${turn.chatId}`"
              @change="
                (val) =>
                  handleCollapseChange(
                    val,
                    `thinkVisible${turn.chatId}`,
                    turn.think,
                    `think${turn.chatId}`
                  )
              "
            >
              <el-collapse-item
                class="ai-Think-Collapse"
                :name="`think${turn.chatId}`"
              >
                <span slot="title" style="width: 100%">
                  <div style="display: flex; align-items: center">
                    <div class="think-badge">
                      <base-icon name="brain" :size="14" :stroke-width="2" />
                    </div>

                    <div>
                      <div
                        v-if="turn.loading"
                        style="display: flex; align-items: center"
                      >
                        &nbsp;&nbsp;
                        <div
                          class="think-loading"
                          style="
                            width: 160px;
                            height: 24px;
                            margin-left: 5px;
                            margin-right: 5px;
                          "
                          v-loading="turn.loading"
                          :element-loading-text="`思考中...  (${turn.seconds}s)`"
      
                        ></div>
                      </div>

                      <div
                        style="display: flex; align-items: center"
                        v-if="!turn.loading"
                      >
                        &nbsp;&nbsp;&nbsp;&nbsp;
                        {{ turn.think.title }} {{turn.seconds > 0? `(${turn.seconds}s)`: ''}} 
                      </div>
                    </div>
                  </div>
                </span>

                <div style="padding: 10px">
                  <div
                    style="white-space: break-spaces"
                    class="thinkPage think-content no-scrollbar"
                  >{{ turn.think.text }}
                    <!--<think-Page :content="turn.think.text || ''"> </think-Page>-->
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>

          <div
            v-for="(step, index1) in turn.steps"
            :key="index1"
            style="margin: 10px 20px"
          >
            <div v-if="step.stepType == 'query'">
              <el-collapse
                class="ai-Collapse"
                :value="step.openPanel"
                :ref="`queryVisible${turn.chatId}${step.itemId}`"
                @change="
                  (val) =>
                    handleCollapseChange(
                      val,
                      `queryVisible${turn.chatId}${step.itemId}`,
                      step,
                      `query${turn.chatId}${step.itemId}`,
                      `queryTable_${turn.chatId}_${step.itemId}`
                    )
                "
              >
                <el-collapse-item
                  class="ai-Collapse"
                  :name="`query${turn.chatId}${step.itemId}`"
                >
                  <span slot="title" style="width: 93%">
                    <div
                      style="display: flex; align-items: center; width: 100%"
                    >
                      <div class="ai-Title-No">
                        {{ step.itemId }}
                      </div>
                      &nbsp;&nbsp;
                      <div class="ai-Title-Type" style="min-width: 46px">
                        查询
                      </div>
                      &nbsp;&nbsp;
                      <div class="title">
                        <truncate-tip :text="step.title" />
                      </div>
                    </div>
                  </span>

                  <div
                    style="
                      padding: 10px;
                      border: 1px solid #e8ecf2;
                      border-radius: 10px;
                    "
                  >
                    <div
                      style="
                        background: #fafbfe;
                        border-bottom: 1px solid #e8ecf2;
                        height: 48px;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        padding: 0 20px;
                      "
                    >
                      <div>
                        <el-button
                          size="mini"
                          class="toolbar-btn"
                          @click="showSQL(step.viewSql)"
                          :disabled="!!step.error"
                          ><base-icon
                            name="file-code"
                            :size="13"
                            :stroke-width="2"
                          />&nbsp;SQL</el-button
                        >

                        <el-button
                          size="mini"
                          class="toolbar-btn"
                          @click="downFile(step)"
                          :disabled="step.data.records.length == 0"
                          ><base-icon
                            name="download"
                            :size="13"
                            :stroke-width="2"
                          />&nbsp;下载
                        </el-button>
                      </div>

                      <div class="chatType-DropDown" style="display: none">
                        <el-dropdown
                          @command="(val) => handleCommandChartType(val, step)"
                        >
                          <span style="margin-right: 5px">
                            展示方式:
                            {{ chartTypeEnum[step.chartType].name
                            }}<i class="el-icon-arrow-down el-icon--right"></i>
                          </span>
                          <el-dropdown-menu slot="dropdown">
                            <el-dropdown-item
                              v-for="item in chartTypeEnum"
                              :key="item.value"
                              :value="item.value"
                              :command="item.value"
                            >
                              {{ item.name }}
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </el-dropdown>
                      </div>
                    </div>

                    <div
                      style="
                        border-bottom: 1px solid #e7e9ec;
                        min-height: 48px;
                        padding: 5px 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                      "
                    >
                      <div class="query-row__main">
                        <div class="query-row__label" style="min-width: 60px">
                          维度
                        </div>

                        <div class="query-row__tags">
                          <div
                            v-for="dim in step.dims"
                            :key="dim.name"
                            class="queryDim-Panel"
                          >
                            <div>
                              <div
                                style="
                                  max-width: 80px;
                                  white-space: nowrap;
                                  overflow: hidden;
                                  text-overflow: ellipsis;
                                "
                              >
                                <truncate-tip
                                  class="queryDim-Panel__text"
                                  :text="dim.name"
                                />
                              </div>
                            </div>

                            <div
                              @click="(val) => deleteDim(val, dim, step)"
                              style="cursor: pointer"
                            >
                              <i class="el-icon-close"></i>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div>
                        <el-dropdown
                          @command="(val) => handleCommandDims(val, step)"
                          :ref="`dimVisible${index}${index1}`"
                          trigger="manual"
                          popper-append-to-body
                        >
                          <span>
                            <el-button
                              size="mini"
                              icon="el-icon-plus"
                              style="
                                border-radius: 50%;
                                padding: 6px;
                                height: 26px;
                              "
                              @click.stop="
                                openDims(`dimVisible${index}${index1}`, step)
                              "
                            ></el-button>
                          </span>

                          <el-dropdown-menu
                            slot="dropdown"
                            style="max-height: 200px; overflow: hidden auto"
                            class="no-scrollbar"
                          >
                            <div
                              class="dropdown-loading"
                              v-if="step.loadingCandidate"
                            >
                              <div
                                v-loading="step.loadingCandidate"
                                :element-loading-delay="300"
                                element-loading-text="加载中..."
                                style="width: 100px; height: 60px"
                              ></div>
                            </div>

                            <div
                              v-if="!step.loadingCandidate"
                              style="max-width: 200px"
                            >
                              <el-dropdown-item
                                :command="null"
                                v-if="step.candidateDims.length == 0"
                              >
                                暂无维度
                              </el-dropdown-item>

                              <el-dropdown-item
                                v-for="item in step.candidateDims"
                                :key="item.id"
                                :value="item.id"
                                :command="item"
                              >
                                <truncate-tip :text="item.name" />
                              </el-dropdown-item>
                            </div>
                          </el-dropdown-menu>
                        </el-dropdown>
                      </div>
                    </div>

                    <div
                      style="
                        border-bottom: 1px solid #e7e9ec;
                        min-height: 48px;
                        padding: 5px 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                      "
                    >
                      <div class="query-row__main">
                        <div class="query-row__label">指标</div>

                        <div class="query-row__tags">
                          <div
                            v-for="mertic in step.metrics"
                            :key="mertic.name"
                            class="queryDim-Panel"
                          >
                            <div>
                              <div
                                style="
                                  max-width: 80px;
                                  white-space: nowrap;
                                  overflow: hidden;
                                  text-overflow: ellipsis;
                                "
                              >
                                <truncate-tip
                                  class="queryDim-Panel__text"
                                  :text="mertic.name"
                                />
                              </div>
                            </div>
                            <div
                              @click="(val) => deleteMetric(val, mertic, step)"
                              style="cursor: pointer"
                            >
                              <i class="el-icon-close"></i>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div>
                        <el-dropdown
                          @command="(val) => handleCommandMetrics(val, step)"
                          :ref="`metricVisible${index}${index1}`"
                          trigger="manual"
                          popper-append-to-body
                        >
                          <span>
                            <el-button
                              size="mini"
                              icon="el-icon-plus"
                              style="
                                border-radius: 50%;
                                padding: 6px;
                                height: 26px;
                              "
                              @click.stop="
                                openMetric(
                                  `metricVisible${index}${index1}`,
                                  step
                                )
                              "
                            ></el-button>
                          </span>
                          <el-dropdown-menu
                            slot="dropdown"
                            style="max-height: 200px; overflow: hidden auto"
                            class="no-scrollbar"
                          >
                            <div
                              class="dropdown-loading"
                              v-if="step.loadingCandidate"
                            >
                              <div
                                v-loading="step.loadingCandidate"
                                :element-loading-delay="300"
                                element-loading-text="加载中..."
                                style="width: 100px; height: 60px"
                              ></div>
                            </div>

                            <div
                              v-if="!step.loadingCandidate"
                              style="max-width: 200px"
                            >
                              <el-dropdown-item
                                :command="null"
                                v-if="step.candidateMetrics.length == 0"
                              >
                                暂无指标
                              </el-dropdown-item>
                              <el-dropdown-item
                                v-for="item in step.candidateMetrics"
                                :key="item.id"
                                :value="item.id"
                                :command="item"
                              >
                                <truncate-tip :text="item.name" />
                              </el-dropdown-item>
                            </div>
                          </el-dropdown-menu>
                        </el-dropdown>
                      </div>
                    </div>

                    <div class="query-row query-row--filter">
                      <div class="query-row__main">
                        <div class="query-row__label" style="min-width: 60px">
                          筛选器
                        </div>

                        <div style="width: 90%">
                          <div class="filter-area">
                            <div class="filter-conds">
                              <div class="filter-cond">
                                <el-select
                                  placeholder="日期"
                                  v-model="step.granularity"
                                  class="filter-cond__field filter-Style"
                                  style="width: 100px"
                                  @change="setGranularity(step)"
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

                              <el-date-picker
                                v-model="step.dateRange"
                                type="daterange"
                                range-separator="至"
                                start-placeholder="开始日期"
                                end-placeholder="结束日期"
                                :value-format="'yyyy-MM-dd'"
                                class="filter-cond__date filter-Date"
                                style="width: 348px"
                                @change="setDateRange(step)"
                                :picker-options="step.pickerOptions"
                              >
                                <template slot="suffix">
                                  <i class="el-input__icon el-icon-date"></i>
                                  <!-- 自定义图标位置 -->
                                </template>
                              </el-date-picker>

                              <!--<div
                                    style="
                                      width: 30px;
                                      height: 30px;
                                      margin-left: 10px;
                                    "
                                  ></div>-->
                            </div>

                            <div
                              v-for="(filter, index2) in step.filters"
                              :key="index2"
                              class="filter-cond"
                              style="width: 462px"
                            >
                              <truncate-tip
                                class="filter-cond__name"
                                :text="filter.name"
                                style="width: 112px"
                              />

                              <el-select
                                placeholder="操作"
                                v-model="filter.operator"
                                class="filter-cond__op filter-Style"
                                @change="onOperatorConfirm(filter,step)"
                                style="width: 130px"
                              >
                                <el-option
                                  v-for="item in operatorEnum"
                                  :key="item.value"
                                  :label="`${item.name} ${item.symbol}`"
                                  :value="item.value"
                                >
                                </el-option>
                              </el-select>

                              <!--按照操作符变化输入框和下拉框-->
                              <div
                                style="position: relative"
                                v-if="isMulti(filter)"
                              >
                                <el-select
                                  placeholder="请选择值"
                                  v-model="filter.value"
                                  class="filter-cond__value filter-cond__value--select"
                                  multiple
                                  collapse-tags
                                  filterable
                                  @change="onValuesConfirm(step)"
                                >
                                  <template slot="prefix">
                                    <span
                                      class="clear-btn"
                                      style="background: transparent"
                                    >
                                      <span
                                        class="triangle"
                                        @click.stop="deleteFilter(index2, step)"
                                      >
                                        <span class="x-mark">×</span>
                                      </span>
                                    </span>
                                  </template>

                                  <el-option
                                    v-for="item in filter.options"
                                    :key="item"
                                    :label="item"
                                    :value="item"
                                  >
                                  </el-option>
                                </el-select>
                              </div>

                              <div style="position: relative" v-else>
                                <el-input
                                  v-model="filter.value"
                                  class="filter-cond__value filter-input"
                                  placeholder="请输入值"
                                  style="width: 220px"
                                  @input="onValuesConfirm(step)"
                                >
                                  <template slot="suffix">
                                    <span
                                      class="clear-btn"
                                      @click="deleteFilter(index2, step)"
                                    >
                                      <span class="triangle">
                                        <span class="x-mark">×</span>
                                      </span>
                                    </span>
                                  </template>
                                </el-input>
                              </div>
                            </div>
                          </div>

                          <div class="filter-area__actions">
                            <el-button
                              v-if="step.dirty"
                              type="primary"
                              icon="el-icon-search"
                              class="dirty-button filter-area__search"
                              @click="applyNewQuery(step, 1)"
                            >
                              应用修改
                            </el-button>

                            <el-button
                              v-else
                              type="primary"
                              icon="el-icon-search"
                              class="filter-area__search"
                              @click="applyNewQuery(step, 1)"
                              :disabled="step.loadingData"
                            >
                              {{ step.loadingData ? "查询中" : "重新查询" }}
                            </el-button>
                          </div>
                        </div>
                      </div>

                      <div>
                        <el-dropdown
                          :ref="`filterVisible${index}${index1}`"
                          trigger="manual"
                          popper-append-to-body
                        >
                          <span>
                            <el-button
                              size="mini"
                              icon="el-icon-plus"
                              style="
                                border-radius: 50%;
                                padding: 6px;
                                height: 26px;
                              "
                              @click.stop="
                                openFilter(
                                  `filterVisible${index}${index1}`,
                                  `${index}${index1}`,
                                  step
                                )
                              "
                            ></el-button>
                          </span>

                          <el-dropdown-menu
                            slot="dropdown"
                            class="filter-pop-menu"
                          >
                            <div
                              class="filter-pop"
                              style="width: 300px"
                              v-loading="step.loadingFilterMeta"
                              element-loading-text="加载中..."
                              :element-loading-delay="300"
                            >
                              <div class="filter-pop__header">
                                <span class="filter-pop__icon">
                                  <base-icon
                                    name="filter"
                                    :size="13"
                                    :stroke-width="2"
                                  />
                                </span>
                                添加筛选器
                              </div>

                              <div class="filter-pop__body">
                                <el-tabs
                                  :ref="`activeName${index}${index1}`"
                                  stretch
                                  style="width: 100%"
                                >
                                  <el-tab-pane
                                    label="维度"
                                    :name="`first${index}${index1}`"
                                  >
                                    <div class="filter-pop__list no-scrollbar">
                                      <el-checkbox-group
                                        v-model="dimsFilterTemp"
                                        style="
                                          display: flex;
                                          width: 100%;
                                          flex-wrap: wrap;
                                        "
                                      >
                                        <el-checkbox
                                          class="filter-Panel"
                                          v-for="dim in step.dims"
                                          :key="dim.id"
                                          :label="dim"
                                        >
                                          <truncate-tip :text="dim.name" />
                                        </el-checkbox>
                                      </el-checkbox-group>
                                    </div>
                                  </el-tab-pane>

                                  <el-tab-pane
                                    label="指标"
                                    :name="`second${index}${index1}`"
                                  >
                                    <div class="filter-pop__list no-scrollbar">
                                      <el-checkbox-group
                                        v-model="metricsFilterTemp"
                                        style="
                                          display: flex;
                                          width: 100%;
                                          flex-wrap: wrap;
                                        "
                                      >
                                        <el-checkbox
                                          class="filter-Panel"
                                          v-for="mertic in step.metrics"
                                          :key="mertic.id"
                                          :label="mertic"
                                        >
                                          <truncate-tip :text="mertic.name" />
                                        </el-checkbox>
                                      </el-checkbox-group>
                                    </div>
                                  </el-tab-pane>
                                </el-tabs>
                              </div>

                              <div class="filter-pop__footer">
                                <el-button
                                  type="primary"
                                  size="mini"
                                  @click.stop="
                                    sureFilter(
                                      `filterVisible${index}${index1}`,
                                      step
                                    )
                                  "
                                  >确定</el-button
                                >
                              </div>
                            </div>
                          </el-dropdown-menu>
                        </el-dropdown>
                      </div>
                    </div>

                    <div
                      v-if="step.error"
                      style="
                        width: 100%;
                        height: 100%;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                      "
                    >
                      <div style="width: 20%; height: 20%">
                        <svg
                          data-v-f1f3fdc0=""
                          viewBox="0 0 120 96"
                          fill="none"
                        >
                          <defs data-v-f1f3fdc0="">
                            <linearGradient
                              data-v-f1f3fdc0=""
                              id="sbg7"
                              x1="0"
                              y1="0"
                              x2="120"
                              y2="96"
                              gradientUnits="userSpaceOnUse"
                            >
                              <stop
                                data-v-f1f3fdc0=""
                                stop-color="#fbe3e4"
                              ></stop>
                              <stop
                                data-v-f1f3fdc0=""
                                offset="1"
                                stop-color="#e5484d"
                              ></stop>
                            </linearGradient>
                          </defs>
                          <ellipse
                            data-v-f1f3fdc0=""
                            cx="60"
                            cy="82"
                            rx="38"
                            ry="6"
                            fill="#fbe3e4"
                            opacity="0.5"
                          ></ellipse>
                          <rect
                            data-v-f1f3fdc0=""
                            x="26"
                            y="20"
                            width="68"
                            height="52"
                            rx="8"
                            fill="#fff"
                            stroke="#e5484d"
                            stroke-width="1.4"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="32"
                            width="30"
                            height="4"
                            rx="2"
                            fill="url(#sbg7)"
                            opacity="0.85"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="42"
                            width="48"
                            height="3.4"
                            rx="1.7"
                            fill="#fbe3e4"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="51"
                            width="38"
                            height="3.4"
                            rx="1.7"
                            fill="#fbe3e4"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="60"
                            width="26"
                            height="3.4"
                            rx="1.7"
                            fill="#fbe3e4"
                          ></rect>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="92"
                            cy="24"
                            r="12"
                            fill="url(#sbg7)"
                            opacity="0.14"
                          ></circle>
                          <g
                            data-v-f1f3fdc0=""
                            stroke="#e5484d"
                            stroke-width="1.8"
                            stroke-linecap="round"
                          >
                            <path
                              data-v-f1f3fdc0=""
                              d="M88 20l8 8M96 20l-8 8"
                            ></path>
                          </g>
                          <g data-v-f1f3fdc0="" fill="#e5484d" opacity="0.42">
                            <circle
                              data-v-f1f3fdc0=""
                              cx="18"
                              cy="30"
                              r="1.6"
                            ></circle>
                            <circle
                              data-v-f1f3fdc0=""
                              cx="14"
                              cy="46"
                              r="1.2"
                            ></circle>
                            <circle
                              data-v-f1f3fdc0=""
                              cx="104"
                              cy="52"
                              r="1.6"
                            ></circle>
                            <circle
                              data-v-f1f3fdc0=""
                              cx="108"
                              cy="66"
                              r="1.2"
                            ></circle>
                          </g>
                        </svg>
                      </div>
                      <div style="font-weight: 600">
                        {{ step.error }}
                      </div>
                      <div
                        style="
                          font-size: 12px;
                          color: #93a4bd;
                          line-height: 1.6;
                        "
                      >
                        可调整查询条件后重试
                      </div>
                    </div>

                    <div
                      v-else-if="step.data.records.length == 0"
                      style="
                        width: 100%;
                        height: 100%;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                      "
                    >
                      <div style="width: 20%; height: 20%">
                        <svg
                          data-v-f1f3fdc0=""
                          viewBox="0 0 120 96"
                          fill="none"
                        >
                          <defs data-v-f1f3fdc0="">
                            <linearGradient
                              data-v-f1f3fdc0=""
                              id="sbg2"
                              x1="0"
                              y1="0"
                              x2="120"
                              y2="96"
                              gradientUnits="userSpaceOnUse"
                            >
                              <stop
                                data-v-f1f3fdc0=""
                                stop-color="#e4eefd"
                              ></stop>
                              <stop
                                data-v-f1f3fdc0=""
                                offset="1"
                                stop-color="#4f8cf5"
                              ></stop>
                            </linearGradient>
                          </defs>
                          <ellipse
                            data-v-f1f3fdc0=""
                            cx="60"
                            cy="82"
                            rx="38"
                            ry="6"
                            fill="#e4eefd"
                            opacity="0.5"
                          ></ellipse>
                          <rect
                            data-v-f1f3fdc0=""
                            x="26"
                            y="20"
                            width="68"
                            height="52"
                            rx="8"
                            fill="#fff"
                            stroke="#4f8cf5"
                            stroke-width="1.4"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="32"
                            width="30"
                            height="4"
                            rx="2"
                            fill="url(#sbg2)"
                            opacity="0.85"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="42"
                            width="48"
                            height="3.4"
                            rx="1.7"
                            fill="#e4eefd"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="51"
                            width="38"
                            height="3.4"
                            rx="1.7"
                            fill="#e4eefd"
                          ></rect>
                          <rect
                            data-v-f1f3fdc0=""
                            x="36"
                            y="60"
                            width="26"
                            height="3.4"
                            rx="1.7"
                            fill="#e4eefd"
                          ></rect>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="92"
                            cy="24"
                            r="12"
                            fill="url(#sbg2)"
                            opacity="0.14"
                          ></circle>
                          <g
                            data-v-f1f3fdc0=""
                            stroke="#4f8cf5"
                            stroke-width="1.8"
                            stroke-linecap="round"
                          >
                            <circle
                              data-v-f1f3fdc0=""
                              cx="90.6"
                              cy="22.6"
                              r="4.6"
                              fill="none"
                            ></circle>
                            <path
                              data-v-f1f3fdc0=""
                              d="M94.4 26.4L98 30"
                            ></path>
                          </g>
                          <g data-v-f1f3fdc0="" fill="#4f8cf5" opacity="0.42">
                            <circle
                              data-v-f1f3fdc0=""
                              cx="18"
                              cy="30"
                              r="1.6"
                            ></circle>
                            <circle
                              data-v-f1f3fdc0=""
                              cx="14"
                              cy="46"
                              r="1.2"
                            ></circle>
                            <circle
                              data-v-f1f3fdc0=""
                              cx="104"
                              cy="52"
                              r="1.6"
                            ></circle>
                            <circle
                              data-v-f1f3fdc0=""
                              cx="108"
                              cy="66"
                              r="1.2"
                            ></circle>
                          </g>
                        </svg>
                      </div>

                      <div style="font-weight: 600">当前条件下无数据</div>
                      <div
                        style="
                          font-size: 12px;
                          color: #93a4bd;
                          line-height: 1.6;
                        "
                      >
                        可尝试放宽筛选条件或调整时间范围
                      </div>
                    </div>
                    <div class="drag-table" v-else>
                      <el-table
                        :data="step.data.records"
                        style="width: 100%"
                        :ref="`queryTable_${turn.chatId}_${step.itemId}`"
                        border
                        stripe
                        class="query-tableBox"
                        v-loading="step.loadingData"
                        :element-loading-delay="300"
                        element-loading-text="加载中..."
                        element-loading-background="rgb(248 248 248 / 50%)"
                      >
                        <el-table-column-with-tooltip
                          width="auto"
                          v-for="column in step.data.columns"
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
                        v-show="step.data.total > 0"
                        :total="step.data.total"
                        :page.sync="step.data.page"
                        :limit.sync="step.data.pageSize"
                        @pagination="(val) => applyQuery(step, val)"
                        style="padding: 10px"
                      />
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>

            <div v-if="step.stepType == 'compute'">
              <el-collapse
                class="ai-Collapse"
                :value="step.openPanel"
                :ref="`computeVisible${turn.chatId}${step.itemId}`"
                @change="
                  (val) =>
                    handleCollapseChange(
                      val,
                      `computeVisible${turn.chatId}${step.itemId}`,
                      step,
                      `compute${turn.chatId}${step.itemId}`,
                      `computeTable_${turn.chatId}_${step.itemId}`
                    )
                "
              >
                <el-collapse-item
                  class="ai-Collapse"
                  :name="`compute${turn.chatId}${step.itemId}`"
                >
                  <span style="width: 93%" slot="title">
                    <div
                      style="display: flex; align-items: center; width: 100%"
                    >
                      <div class="ai-Title-No">
                        {{ step.itemId }}
                      </div>
                      &nbsp;&nbsp;
                      <div
                        class="ai-Title-Type ai-Title-Type--compute"
                        style="min-width: 46px"
                      >
                        计算
                      </div>
                      &nbsp;&nbsp;
                      <div class="title">
                        <truncate-tip :text="step.title" />
                      </div>
                    </div>
                  </span>

                  <div
                    v-if="step.error"
                    style="
                      width: 100%;
                      height: 100%;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                    "
                  >
                    <div style="width: 20%; height: 20%">
                      <svg data-v-f1f3fdc0="" viewBox="0 0 120 96" fill="none">
                        <defs data-v-f1f3fdc0="">
                          <linearGradient
                            data-v-f1f3fdc0=""
                            id="sbg7"
                            x1="0"
                            y1="0"
                            x2="120"
                            y2="96"
                            gradientUnits="userSpaceOnUse"
                          >
                            <stop
                              data-v-f1f3fdc0=""
                              stop-color="#fbe3e4"
                            ></stop>
                            <stop
                              data-v-f1f3fdc0=""
                              offset="1"
                              stop-color="#e5484d"
                            ></stop>
                          </linearGradient>
                        </defs>
                        <ellipse
                          data-v-f1f3fdc0=""
                          cx="60"
                          cy="82"
                          rx="38"
                          ry="6"
                          fill="#fbe3e4"
                          opacity="0.5"
                        ></ellipse>
                        <rect
                          data-v-f1f3fdc0=""
                          x="26"
                          y="20"
                          width="68"
                          height="52"
                          rx="8"
                          fill="#fff"
                          stroke="#e5484d"
                          stroke-width="1.4"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="32"
                          width="30"
                          height="4"
                          rx="2"
                          fill="url(#sbg7)"
                          opacity="0.85"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="42"
                          width="48"
                          height="3.4"
                          rx="1.7"
                          fill="#fbe3e4"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="51"
                          width="38"
                          height="3.4"
                          rx="1.7"
                          fill="#fbe3e4"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="60"
                          width="26"
                          height="3.4"
                          rx="1.7"
                          fill="#fbe3e4"
                        ></rect>
                        <circle
                          data-v-f1f3fdc0=""
                          cx="92"
                          cy="24"
                          r="12"
                          fill="url(#sbg7)"
                          opacity="0.14"
                        ></circle>
                        <g
                          data-v-f1f3fdc0=""
                          stroke="#e5484d"
                          stroke-width="1.8"
                          stroke-linecap="round"
                        >
                          <path
                            data-v-f1f3fdc0=""
                            d="M88 20l8 8M96 20l-8 8"
                          ></path>
                        </g>
                        <g data-v-f1f3fdc0="" fill="#e5484d" opacity="0.42">
                          <circle
                            data-v-f1f3fdc0=""
                            cx="18"
                            cy="30"
                            r="1.6"
                          ></circle>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="14"
                            cy="46"
                            r="1.2"
                          ></circle>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="104"
                            cy="52"
                            r="1.6"
                          ></circle>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="108"
                            cy="66"
                            r="1.2"
                          ></circle>
                        </g>
                      </svg>
                    </div>
                    <div style="font-weight: 600">
                      {{ step.error }}
                    </div>
                  </div>

                  <div
                    v-else-if="step.data.records.length == 0"
                    style="
                      width: 100%;
                      height: 100%;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                    "
                  >
                    <div style="width: 20%; height: 20%">
                      <svg data-v-f1f3fdc0="" viewBox="0 0 120 96" fill="none">
                        <defs data-v-f1f3fdc0="">
                          <linearGradient
                            data-v-f1f3fdc0=""
                            id="sbg2"
                            x1="0"
                            y1="0"
                            x2="120"
                            y2="96"
                            gradientUnits="userSpaceOnUse"
                          >
                            <stop
                              data-v-f1f3fdc0=""
                              stop-color="#e4eefd"
                            ></stop>
                            <stop
                              data-v-f1f3fdc0=""
                              offset="1"
                              stop-color="#4f8cf5"
                            ></stop>
                          </linearGradient>
                        </defs>
                        <ellipse
                          data-v-f1f3fdc0=""
                          cx="60"
                          cy="82"
                          rx="38"
                          ry="6"
                          fill="#e4eefd"
                          opacity="0.5"
                        ></ellipse>
                        <rect
                          data-v-f1f3fdc0=""
                          x="26"
                          y="20"
                          width="68"
                          height="52"
                          rx="8"
                          fill="#fff"
                          stroke="#4f8cf5"
                          stroke-width="1.4"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="32"
                          width="30"
                          height="4"
                          rx="2"
                          fill="url(#sbg2)"
                          opacity="0.85"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="42"
                          width="48"
                          height="3.4"
                          rx="1.7"
                          fill="#e4eefd"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="51"
                          width="38"
                          height="3.4"
                          rx="1.7"
                          fill="#e4eefd"
                        ></rect>
                        <rect
                          data-v-f1f3fdc0=""
                          x="36"
                          y="60"
                          width="26"
                          height="3.4"
                          rx="1.7"
                          fill="#e4eefd"
                        ></rect>
                        <circle
                          data-v-f1f3fdc0=""
                          cx="92"
                          cy="24"
                          r="12"
                          fill="url(#sbg2)"
                          opacity="0.14"
                        ></circle>
                        <g
                          data-v-f1f3fdc0=""
                          stroke="#4f8cf5"
                          stroke-width="1.8"
                          stroke-linecap="round"
                        >
                          <circle
                            data-v-f1f3fdc0=""
                            cx="90.6"
                            cy="22.6"
                            r="4.6"
                            fill="none"
                          ></circle>
                          <path data-v-f1f3fdc0="" d="M94.4 26.4L98 30"></path>
                        </g>
                        <g data-v-f1f3fdc0="" fill="#4f8cf5" opacity="0.42">
                          <circle
                            data-v-f1f3fdc0=""
                            cx="18"
                            cy="30"
                            r="1.6"
                          ></circle>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="14"
                            cy="46"
                            r="1.2"
                          ></circle>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="104"
                            cy="52"
                            r="1.6"
                          ></circle>
                          <circle
                            data-v-f1f3fdc0=""
                            cx="108"
                            cy="66"
                            r="1.2"
                          ></circle>
                        </g>
                      </svg>
                    </div>

                    <div style="font-weight: 600">当前条件下无数据</div>
                  </div>

                  <div v-else style="padding: 10px" class="drag-table">
                    <div
                      style="
                        width: 100%;
                        padding: 0 20px;
                        background: #fafbfe;
                        border-bottom: 1px solid #e8ecf2;
                        height: 48px;
                        display: flex;
                        align-items: center;
                      "
                    >
                      <el-button
                        size="mini"
                        class="toolbar-btn"
                        @click="
                          downComputeFile(sessionId, turn.chatId, step.itemId)
                        "
                        ><base-icon
                          name="download"
                          :size="13"
                          :stroke-width="2"
                        />&nbsp;下载
                      </el-button>
                    </div>

                    <el-table
                      :data="step.data.records"
                      style="width: 100%"
                      border
                      stripe
                      class="compute-tableBox"
                      :ref="`computeTable_${turn.chatId}_${step.itemId}`"
                    >
                      <el-table-column-with-tooltip
                        width="auto"
                        v-for="column in step.data.columns"
                        :key="column.name"
                        :prop="column.name"
                        :label="
                          column.unit
                            ? column.cn_name ||
                              column.name + '(' + column.unit + ')'
                            : column.cn_name || column.name
                        "
                        resizable
                        sortable
                        :render-header="renderHeader"
                      />
                    </el-table>

                    <pagination
                      v-show="step.data.total > 0"
                      :total="step.data.total"
                      :page.sync="step.data.page"
                      :limit.sync="step.data.pageSize"
                      @pagination="getComputedListByPage(step)"
                      style="padding: 10px"
                    />
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>

            <div v-if="step.stepType == 'analyze'">
              <el-collapse
                class="ai-Collapse"
                :value="step.openPanel"
                :ref="`analyzeVisible${turn.chatId}${step.itemId}`"
                @change="
                  (val) =>
                    handleCollapseChange(
                      val,
                      `analyzeVisible${turn.chatId}${step.itemId}`,
                      step,
                      `analyze${turn.chatId}${step.itemId}`
                    )
                "
              >
                <el-collapse-item
                  class="ai-Collapse"
                  :name="`analyze${turn.chatId}${step.itemId}`"
                >
                  <span style="width: 93%" slot="title">
                    <div
                      style="display: flex; align-items: center; width: 100%"
                    >
                      <div class="ai-Title-No">
                        {{ step.itemId }}
                      </div>
                      &nbsp;&nbsp;
                      <div
                        class="ai-Title-Type ai-Title-Type--analyze"
                        style="min-width: 46px"
                      >
                        分析
                      </div>
                      &nbsp;&nbsp;
                      <div class="title">
                        <truncate-tip :text="step.title" />
                      </div>
                    </div>
                  </span>

                  <div style="padding: 10px">
                    <div
                      style="
                        width: 100%;
                        padding: 0 20px;
                        background: #fafbfe;
                        border-bottom: 1px solid #e8ecf2;
                        height: 48px;
                        display: flex;
                        align-items: center;
                      "
                    >
                      <el-button
                        size="mini"
                        class="toolbar-btn"
                        @click="
                          downAnalyzeFile(sessionId, turn.chatId, step.itemId)
                        "
                        ><base-icon
                          name="download"
                          :size="13"
                          :stroke-width="2"
                        />&nbsp;下载
                      </el-button>
                    </div>

                    <div
                      class="ai-result-card ai-result-card--analyze no-scrollbar"
                      :id="`analyzePanel${turn.chatId}${step.itemId}`"
                    >
                      <think-Page :content="step.answer"> </think-Page>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>

            <div v-if="step.stepType == 'summarize'">
              <el-collapse
                class="ai-Collapse"
                :value="step.openPanel"
                :ref="`summarizeVisible${turn.chatId}${step.itemId}`"
                @change="
                  (val) =>
                    handleCollapseChange(
                      val,
                      `summarizeVisible${turn.chatId}${step.itemId}`,
                      step,
                      `summarize${turn.chatId}${step.itemId}`
                    )
                "
              >
                <!--<el-collapse-item
                      class="ai-Collapse"
                      :name="`summarize${groupChatItem.chatId}`"
                    >-->
                <el-collapse-item
                  class="ai-Collapse"
                  :name="`summarize${turn.chatId}${step.itemId}`"
                >
                  <span style="width: 93%" slot="title">
                    <div
                      style="display: flex; align-items: center; width: 100%"
                    >
                      <div class="ai-Title-No">
                        {{ step.itemId }}
                      </div>
                      &nbsp;&nbsp;
                      <div
                        class="ai-Title-Type ai-Title-Type--summary"
                        style="min-width: 46px"
                      >
                        总结
                      </div>
                      &nbsp;&nbsp;
                      <div class="title">
                        <truncate-tip :text="step.title" />
                      </div>
                    </div>
                  </span>

                  <div>
                    <div style="padding: 10px">
                      <div
                        class="ai-result-card ai-result-card--summary no-scrollbar"
                        :id="`summarizePanel${turn.chatId}${step.itemId}`"
                      >
                        <!--{{ step.answer }}-->
                        <think-Page :content="step.answer"> </think-Page>
                      </div>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
          <!-- 问答质量管理：收到 close 后在答案卡片底部出现 👍 👎 -->
          <div
            v-if="turn.status === 'done' && turn.chatId && turn.steps && turn.steps.length"
            style="text-align: left; margin: 2px 0 0 44px"
          >
            <feedback-bar
              :chatSessionId="sessionId"
              :chatId="turn.chatId"
              :aiBodyCode="currentAgent && currentAgent.code"
              :question="turn.user && turn.user.text"
              :answer="lastAnswerOf(turn)"
            />
          </div>
        </div>
      </div>
    </div>

    <div
      :style="
        visiableBottom
          ? 'position: absolute; right: 10px; bottom: 205px'
          : 'position: absolute; right: 10px; bottom: 170px'
      "
      v-if="visiableTop"
    >
      <el-button
        size="small"
        icon="el-icon-download"
        class="scrollTop"
        @click="scrollToTop"
      >
      </el-button>
    </div>

    <div
      style="position: absolute; right: 10px; bottom: 170px"
      v-if="visiableBottom"
    >
      <el-button
        size="small"
        icon="el-icon-download"
        class="scrollBottom"
        @click="scrollToBottom(true)"
      >
      </el-button>
    </div>

    <questionPage
      v-if="showQuestion"
      class="my-custom-style"
      :aiBodyCode="currentAgent.code"
      :left="left"
      @send="send"
      :showQuestion.sync="showQuestion"
    />

    <composerPage
      class="my-custom-style"
      :newPage="!turns.length"
      :busy="streamParam.busy"
      :canStop="streamParam.currentChatId != ''"
      :loadingHistory="loadingHistory"
      @send="send"
      @stop="stopChat"
      @showQuestion="showQuestionCommand"
      :showQuestionButton="topQuestion.length>0"
    />

    <div v-if="!turns.length && topQuestion.length" style="height: 15%"></div>
    <div v-if="!turns.length && !topQuestion.length" style="height: 40%"></div>

    <!--<div style="position: absolute; bottom: 0px; right: 0px; color: #c0c0c080">
      {{ version }}
    </div>-->

    <el-dialog
      :show-close="false"
      :close-on-click-modal="false"
      title="SQL"
      :modal="false"
      width="640px"
      :visible.sync="isSqlPage"
    >
      <div style="padding: 0 20px">
        <sql-panel :sql="viewSql" title="本次查询语句" />
      </div>

      <div style="display: flex; padding: 14px 20px 16px">
        <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
          <el-button @click="isSqlPage = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>


<script>
import {
  chartTypeEnum,
  granularityEnum,
  getMetricsTreeAPI,
  getMetricsDataPreviewAPI,
  operatorEnum,
  filterTypeEnum,
} from "@/api/metricDataPreview/metricPreviewAPI.js";

import {
  sendChatAPI,
  stopChatAPI,
  getQuestionAPI,
  isMultiValueOperator,
} from "@/api/newBI/newBIAPI.js";
import { getDimensionValuesAPI } from "@/api/dimensionManager/dimensionAPI.js";

import staticData from "@/static/data.json";

import {
  PAGE_SIZE,
  buildDataPayload,
  createTurn,
  nextUid,
  parseTurn,
  turnDivider,
  openStream,
} from "@/utils/parseChat";
import toast from "@/utils/toast";
import { getUserInfo } from "@/utils/auth";

import { shortcutDate, formatDateTime } from "@/utils/common";

import { copyToClipboard } from "@/utils/clipboard.js";

import SqlPanel from "@/components/SqlPanel";
import TruncateTip from "@/components/TruncateTip";
import welcomePage from "@/views/newBI/welcomePage";
import questionPage from "@/views/newBI/questionPage";
import composerPage from "@/views/newBI/composerPage";
import thinkPage from "@/views/newBI/thinkPage";
import feedbackBar from "@/views/newBI/feedbackBar";

export default {
  name: "queryPage",
  props: [],
  components: {
    SqlPanel,
    TruncateTip,
    staticData,
    thinkPage,
    welcomePage,
    composerPage,
    questionPage,
    feedbackBar,
  },
  data() {
    return {
      chartTypeEnum,
      granularityEnum,
      operatorEnum,
      filterTypeEnum,

      /*pickerOptions: {
          shortcuts: []
        },*/

      version: process.env.VUE_APP_VERSION,

      currentAgent: null, //当前agent

      loadingHistory: false,
      //busy: false,

      turns: [],
      sessionId: "",
      //currentChatId: "",

      topQuestion: [],

      left: 0,
      showQuestion: false,

      visiableTop: false,
      visiableBottom: false,

      viewSql: "", //sql页内容
      isSqlPage: false, //显示sql页

      dimsFilterTemp: [], //filter选择维度
      metricsFilterTemp: [], //filter选择指标
      //chatWs: null,

      followBottom: true,

      streamParam: {
        chatWs: null,
        busy: false,
        currentChatId: "",
        currentStepType: "",
      },

      busyTimer: null,

      //内容太多后做的优化，显示pageSize*2+1项
      currentIndex: -1,
      pageSize: 5,
      visiableTurns: [],
      top: false,
      bottom: false,
      scrollLoading: false,

      //queryParams: null,
      seconds:0,
      timer: null,
    };
  },
  computed: {
    avatarText() {
      return getUserInfo().name.charAt(0).toUpperCase() || "用户";
    },
  },

  watch: {
    // 监听 turns 变化
    "turns.length"(newLen, oldLen) {
      this.updateVisibleTurns();
    },
  },

  mounted() {
  },
  beforeDestroy() {
    if (this.busyTimer) clearTimeout(this.busyTimer);
    this.closeChatWebSocket();
    this.stopTimer();
  },
  methods: {
    /*calcColumnWidth() {
      const el = this.$refs.mainContent;
      if (!el) return;
      this.columnWidth = el.clientWidth * 0.8 - 40 - 2 - 20 - 4;
      console.log(this.columnWidth)
    },*/

    startTimer(turn) {
      //this.seconds = 0;
      if(!turn) return

      this.stopTimer();
      this.timer = setInterval(() => (turn.seconds += 1), 1000);
    },
    stopTimer() {
      if (this.timer) clearInterval(this.timer);
      this.timer = null;
    },

    onBusyChange(busy, turn){
      if(busy) this.startTimer(turn)
      else this.stopTimer()

      if (this.busyTimer) clearTimeout(this.busyTimer);
      this.busyTimer = setTimeout(() => {
        this.$emit("agentBusy", busy);
      }, 300);

      //this.$emit("agentBusy", busy);
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
    },

    downComputeFile(chatSessionId, chatId, itemId) {
      this.downCustomFile(
        chatSessionId,
        chatId,
        itemId,
        `计算导出_${formatDateTime()}.xlsx`
      );
    },

    lastAnswerOf(turn) {
      const s = (turn.steps || []).filter((x) => x.stepType === "summarize" || x.stepType === "analyze");
      const last = s.length ? s[s.length - 1] : null;
      return last && last.answer ? String(last.answer) : "";
    },

    downAnalyzeFile(chatSessionId, chatId, itemId) {
      this.downCustomFile(
        chatSessionId,
        chatId,
        itemId,
        `分析导出_${formatDateTime()}.docx`
      );
    },

    downCustomFile(chatSessionId, chatId, itemId, filename) {
      this.download(
        "/api/v1/chat-server/download/history/item",
        {
          chatSessionId: chatSessionId,
          chatId: chatId,
          itemId: itemId,
        },
        filename
      );
    },

    //下载查询文件
    downFile(step, page) {
      this.download(
        "/api/v1/chat-server/getdata",
        buildDataPayload(step, {
          page: page || step.data.page,
          pageSize: PAGE_SIZE,
          downloadFlag: 1,
        }),
        `查询导出_${formatDateTime()}.xlsx`
      );
    },

    //设置智能体
    setSelectedAgent(agent) {
      this.resetSession();
      this.currentAgent = agent;
      this.getTopQuestion();
    },

    getTopQuestion() {
      let queryQuestionParams = {
        aiBodyCode: this.currentAgent?.code,
        keyword: "",
        status: 1,
        tagId: "",
        page: 1,
        pageSize: 4,
        total: 0,
      };
      this.topQuestion = [];
      getQuestionAPI(queryQuestionParams)
        .then((response) => {
          if (response.code == 200) {
            this.topQuestion = response.data.list;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },
    /////////////////////////

    //显示历史 -- 拆成每次解析一个就等待一会，用于界面渲染，要不然等待时间很长
    async setHistory(agent, history) {
      this.resetSession();
      //await this.sleep(100)  // 等待 2 秒
      await new Promise((resolve) => {
      requestAnimationFrame(async () => {
        try{
        this.currentAgent = agent;
        this.sessionId = history.chatSessionId;

        this.streamParam.busy = true;
        this.loadingHistory = true;
        this.onBusyChange(this.streamParam.busy,null)

        const list = history.chatInfo || [];
        // 分帧插入，避免长会话阻塞主线程
        for (let i = 0; i < list.length; i += 1) {
          this.streamParam.currentChatId = list[i].chatId;
          const turn = parseTurn(list[i]);
          this.turns.push(turn);
          if (i % 2 === 1) {
            this.$nextTick(() => this.scrollToBottom(false));
            await this.nextFrame();
          }
        }
        this.$nextTick(() => this.scrollToBottom(false));
        
        //toast.success(`已载入 ${list.length} 轮对话`);
        this.streamParam.currentChatId = "";
        this.loadingHistory = false;
        this.streamParam.busy = false;
        this.onBusyChange(this.streamParam.busy,null)
      } finally {
        resolve();   // 保证外层一定解除等待
      }
    });
  });
    },

    nextFrame() {
      return new Promise((r) => requestAnimationFrame(() => setTimeout(r, 0)));
    },
    /////////////////////////////////

    //显示推荐问
    compute() {
      let el = this.$refs.mainContent;
      if (el != null) {
        //this.width = el.scrollWidth*0.8
        //this.height = el.scrollHeight- this.top
        //this.top = 60
        this.left = (el.scrollWidth - el.scrollWidth * 0.8) / 2;
      }
    },

    showQuestionCommand() {
      //显示面板，先计算，获取标签，获取全部推荐的数据
      this.compute();
      this.showQuestion = true;
    },
    ////////////////////////////

    //判断是否显示滚动到头和滚动到底
    handleScroll() {
      clearTimeout(this.scrollTimeout);
      this.scrollTimeout = setTimeout(() => {
        this.onScrollEnd();
      }, 100);

      this.optimizeMainContent();
    },

    optimizeMainContent() {
      if (this.scrollLoading) return;

      if (this.turns.length > this.pageSize * 2 + 1) {
        const el = this.$refs.mainContent;
        if (!el) return;

        const scrollTop = el.scrollTop;
        const clientHeight = el.clientHeight;
        const scrollHeight = el.scrollHeight;

        // 向上滚动加载更多
        if (scrollTop < 150 && !this.top) {
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
              const children = el.children;
              for (let i = 0; i < targetIndex && i < children.length; i++) {
                newScrollTop += children[i].offsetHeight || 0;
              }
              newScrollTop += targetIndex * 20 - 40;
              el.scrollTop = Math.max(0, newScrollTop);
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
        if (scrollTop + clientHeight >= scrollHeight - 200 && !this.bottom) {
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

    onScrollEnd() {
      //if (this.scrollLoading) return;

      this.$nextTick(() => {
        const el = this.$refs.mainContent;
        if (!el) return;
        
        const nearBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 90;
        this.followBottom = nearBottom;
        //this.followBottom = scrollTop + clientHeight >= scrollHeight - 90;

        /*if (el.scrollHeight < 2000) {
          this.visiableTop = false;
          this.visiableBottom = false;
          return;
        }*/

        this.visiableTop = !this.top;
        this.visiableBottom = !this.bottom;

        
        if (this.top) {
          this.showToTop = el.scrollTop > 600;
        }

        if (this.bottom) {
          this.showToBottom = !nearBottom && el.scrollHeight > el.clientHeight + 600;
        }

        /*if (this.top) {
          if (el.scrollTop < 1500) {
            this.visiableTop = false;
          } else {
            this.visiableTop = true;
          }
        }

        if (this.bottom) {
          if (el.scrollTop + el.clientHeight >= el.scrollHeight - 10) {
            this.visiableBottom = false;
          } else {
            this.visiableBottom = true;
          }
        }*/
      });
    },

    /*onScrollEnd() {
      this.$nextTick(() => {
        const el = this.$refs.mainContent;
        if (!el) return;

        this.followBottom =
          el.scrollTop + el.clientHeight >= el.scrollHeight - 90;

        if (el.scrollHeight < 2000) {
          this.visiableTop = false;
          this.visiableBottom = false;
          return;
        }

        if (el.scrollTop < 1500) {
          this.visiableTop = false;
        } else {
          this.visiableTop = true;
        }

        if (el.scrollTop + el.clientHeight >= el.scrollHeight - 10) {
          this.visiableBottom = false;
        } else {
          this.visiableBottom = true;
        }
      });
    },*/
    //////////////////////////

    //工具函数
    dividerOf(turn, index) {
      return turnDivider(turn, index > 0 ? this.visiableTurns[index - 1] : null);
    },

    copyToClipboard(text) {
      copyToClipboard(text)
        .then(() => {
          toast.success("复制成功！");
        })
        .catch((err) => {
          console.error("复制失败:", err);
          toast.error("复制失败，请手动复制");
        });
    },

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

    nowText() {
      const d = new Date();
      const p = (n) => String(n).padStart(2, "0");
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(
        d.getHours()
      )}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
    },

    resetSession() {
      this.showQuestion = false;
      this.turns = [];
      this.currentAgent = null;
      this.sessionId = "";
      this.streamParam.currentChatId = "";
      this.streamParam.busy = false;
      this.onBusyChange(this.streamParam.busy,null)
      this.stopTimer();
      this.left = 0;
      //this.topQuestion = [];
      this.loadingHistory = false;
      this.visiableTop = false;
      this.visiableBottom = false;
      this.isSqlPage = false;
      this.followBottom = true;
      this.closeChatWebSocket();
    },
    //////////////////////

    //滚动
    /*scrollToTop() {
      this.$nextTick(() => {
        const el = this.$refs.mainContent;
        if (!el) return;
        el.scrollTo({
          top: 0,
          behavior: "smooth",
        });
      });
    },*/

    /*scrollToBottom(smooth) {
      this.$nextTick(() => {
        const el = this.$refs.mainContent;
        if (!el) return;
        el.scrollTo({
          top: el.scrollHeight,
          behavior: smooth ? "smooth" : "auto",
        });
        this.followBottom = true;
      });
    },*/

    scrollToTop() {
      if (this.turns.length === 0) return;
      if (this.top) {
        this.$nextTick(() => {
          const el = this.$refs.mainContent;
          if (!el) return;
          el.scrollTo({
            top: 0,
            behavior: "smooth",
          });
        });
        return;
      }

      const el = this.$refs.mainContent;
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
          const currentEl = this.$refs.mainContent;
          if (currentEl) {
            currentEl.scrollTop = 0;
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
            const currentEl = this.$refs.mainContent;
            if (currentEl && currentEl.scrollTop > 10) {
              currentEl.scrollTop = 0;
            }
            setTimeout(retryScroll, 100);
          });
        };

        setTimeout(retryScroll, 50);
      });
    },

    scrollToBottom(smooth) {
      if (this.turns.length === 0) return;
      if (this.bottom) {
        this.$nextTick(() => {
          const el = this.$refs.mainContent;
          if (!el) return;
          el.scrollTo({
            top: el.scrollHeight,
            behavior: smooth ? "smooth" : "auto",
          });
          this.followBottom = true;
        });
        return;
      }

      const el = this.$refs.mainContent;
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
          const currentEl = this.$refs.mainContent;
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
            const currentEl = this.$refs.mainContent;
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

    handleCollapseChange(val, name, step, openName, tableName) {
      if (val.length > 0) {
        this.scrollToCollapseTop(name);
        step.openPanel.push(openName);

        this.$nextTick(() => {
          requestAnimationFrame(() => {
            const table = this.$refs[tableName];
            if (table) {
              const inst = Array.isArray(table) ? table[0] : table;
              inst && inst.doLayout && inst.doLayout();
            }
           });
        });

      } else {
        let index = step.openPanel.indexOf(openName);
        if (index > -1) {
          step.openPanel.splice(index, 1);
        }
      }
    },

    //分析和总结，展开到最上边
    scrollToCollapseTop(name) {
      // 当面板展开时触发（activeId 为当前展开的 name）
      if (name) {
        this.$nextTick(() => {
          setTimeout(() => {
            const panel = this.$refs[name];
            if (panel) {
              const el = panel[0]?.$el || panel.$el;
              el.scrollIntoView({ behavior: "smooth", block: "start" }); // 滚动到中间位置更舒适
            }
          }, 300);
        });
      }
    },

    maybeFollow() {
      if (this.followBottom) this.$nextTick(() => this.scrollToBottom(false));
    },
    //////////////////

    //提交问题
    send(text) {
      if (this.streamParam.busy) {
        this.stopChat();
      } else {
        this.handleChatSend(text);
      }
    },

    handleChatSend(text) {
      if (this.streamParam.busy) {
        toast.warn("正在分析中，请稍后再试");
        return;
      }

      if (!this.currentAgent || !this.currentAgent.code) {
        toast.warn("请先选择智能体");
        return;
      }
      this.closeChatWebSocket();
      const aicode = this.currentAgent.code;
      this.followBottom = true;
      text = text.replace("\n", "")

      text = text.replace("\n", "")

      const turn = createTurn(null);
      turn.user = { text: text, time: this.nowText() };
      this.turns.push(turn);
      this.$nextTick(() => this.scrollToBottom(true));

      this.streamParam.busy = true;
      this.onBusyChange(this.streamParam.busy, turn)
      sendChatAPI({
        aicode,
        question: text,
        chatSessionId: this.sessionId || undefined,
      })
        .then((data) => {
          if (data.code == 200) {
            this.startChatSession(data.data, turn);
          } else {
            toast.error(response.message);
            this.streamParam.busy = false;
            this.onBusyChange(this.streamParam.busy, turn)
          }
        })
        .catch((e) => {
          console.log(e);
          toast.error(e.message);
          this.streamParam.busy = false;
          this.onBusyChange(this.streamParam.busy, turn)
        })
        .finally(() => {});
    },

    startChatSession({ chatSessionId, chatId, host }, turn) {
      this.sessionId = chatSessionId;
      turn.chatId = chatId;
      this.streamParam.currentChatId = chatId;
      turn.loading = true;

      openStream(
        chatSessionId,
        chatId,
        host,
        turn,
        this.streamParam,
        this.maybeFollow,
        this.onBusyChange,
      );
    },

    /*openStream(sid, chatId, host, turn) { 
      this.chatWs = connectChatWebSocket({
        chatSessionId: sid,
        chatId,
        host,
        onMessage: (msg) => onStreamMessage(msg, sid, turn),
        onClose: () => {
          this.busy = false;
          this.currentChatId = ''
          turn.loading = false
        },
        onError: (e) => {
          this.busy = false;
          this.currentChatId = ''
          turn.loading = false
          toast.error(e && e.type === 'WebSocket timed out' ? '思考过程超时，请刷新重试' : '思考过程连接异常');
        },
      });
    },*/

    /*onStreamMessage(msg, sid, turn) {
      if (!msg || typeof msg !== 'object') return;
      if (msg.metadata && msg.metadata.close) return;

      const rid = msg.request_id;
      if (!rid || rid.split('@')[0] !== sid) return;

      const stepType = msg.step_type;
      const eventType = msg.event_type;
      const message = msg.message;
      if (!stepType || stepType === 'close') return;

      if (stepType === 'think') {
        if (!turn.think) turn.think = { title: '', text: '', done: false, open: true, openPanel:[] };
        if (eventType === 'title') turn.think.title = message;
        else if (eventType === 'line') turn.think.text += message + '\n\n';
        else if (eventType === 'token') turn.think.text += message;
        else if (eventType === 'done') {
          turn.think.done = true;
          turn.think.open = false;
          turn.think.openPanel = []
        }
        this.maybeFollow();
        return;
      }

      const itemId = Number(String(msg.step_id || '').split('_')[1] || 0);
      let step = turn.steps.find((s) => s.itemId === itemId && s.stepType === stepType);
      if (!step) {
        step = createStep(stepType, itemId);
        if (stepType === 'summarize' || stepType === 'analyze'){
          step.openPanel = [`${stepType}${turn.chatId}${step.itemId}`]
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
          step.open = false; 
        }
      }
      this.maybeFollow();
    },*/

    /*async fetchStepDetail(step, sid, chatId, itemId) {
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

    closeChatWebSocket() {
      //this.wsShouldFetchOnClose = false;
      if (this.streamParam.chatWs) {
        this.streamParam.chatWs.close();
        this.streamParam.chatWs = null;
      }
    },

    stopChat() {
      if (this.sessionId != null && this.streamParam.currentChatId != null) {
        let params = {
          requestId: `${this.sessionId}@${this.streamParam.currentChatId}`,
        };
        stopChatAPI(params)
          .then((response) => {
            if (response.code == 200) {
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {
            //loading.close();
          });
      }
    },
    //////////////////////////

    //查询中的操作
    setGranularity(step) {
      /*if (step.granularity == "day") {
        const day = getCurrentDay();
        this.$set(step.dateRange, 0, day.startStr);
        this.$set(step.dateRange, 1, day.endStr);
      } else if (step.granularity == "week") {
        const week = getCurrentWeek();
        this.$set(step.dateRange, 0, week.startStr);
        this.$set(step.dateRange, 1, week.endStr);
      } else if (step.granularity == "month") {
        const month = getCurrentMonth();
        this.$set(step.dateRange, 0, month.startStr);
        this.$set(step.dateRange, 1, month.endStr);
      } else if (step.granularity == "quarter") {
        const season = getCurrentSeason();
        this.$set(step.dateRange, 0, season.startStr);
        this.$set(step.dateRange, 1, season.endStr);
      } else if (step.granularity == "year") {
        const year = getCurrentYear();
        this.$set(step.dateRange, 0, year.startStr);
        this.$set(step.dateRange, 1, year.endStr);
      }*/

      step.pickerOptions.shortcuts = shortcutDate[step.granularity];
      step.dirty = true;
    },

    setDateRange(step) {
      step.dirty = true;
    },

    async openDims(name, step) {
      if (this.$refs[name] && this.$refs[name][0].show) {
        this.$refs[name][0].show();
      }
      await this.loadCandidates(step, "dim");
    },

    handleCommandDims(val, step) {
      //添加维度
      if (val == null) return;

      let dim = { id: val.id, key: val.key, name: val.name, check: false };

      let index = step.dims.findIndex((item) => item.key == val.key);
      if (index < 0) {
        step.dims.push(dim);
        step.dirty = true;

        //添加删除，重置为第1页
        //step.data.page = 1;
        //this.applyQuery(step);
        //this.getMetricTreeList(chatItemInfo);
      }
    },

    deleteDim(event, dim, step) {
      //删除维度
      //this.selectDim(false, dim, chatItemInfo);

      step.filters = step.filters.filter((item) => item.key != dim.key);

      let index = step.dims.findIndex((item) => item.key == dim.key);
      if (index > -1) {
        step.dims.splice(index, 1);
        step.dirty = true;
        //添加删除，重置为第1页
        //step.data.page = 1;
        //this.applyQuery(step);
      }
    },

    async openMetric(name, step) {
      if (this.$refs[name] && this.$refs[name][0].show) {
        this.$refs[name][0].show();
      }
      await this.loadCandidates(step, "metric");
    },

    handleCommandMetrics(val, step) {
      //添加指标
      if (val == null) return;
      let metric = { id: val.id, key: val.key, name: val.name, check: false };

      let index = step.metrics.findIndex((item) => item.key == val.key);
      if (index < 0) {
        step.metrics.push(metric);
        step.dirty = true;
        //添加删除，重置为第1页
        //step.data.page = 1;
        //this.applyQuery(step);
        //this.getMetricTreeList(chatItemInfo);
      }
    },

    deleteMetric(event, metric, step) {
      step.filters = step.filters.filter((item) => item.key != metric.key);

      let index = step.metrics.findIndex((item) => item.key == metric.key);
      if (index > -1) {
        step.metrics.splice(index, 1);
        step.dirty = true;
        //添加删除，重置为第1页
        //step.data.page = 1;
        //this.applyQuery(step);
      }
    },

    async loadCandidates(step, type) {
      step.loadingCandidate = true;
      try {
        const res = await getMetricsTreeAPI({
          type: type === "metric" ? "metric" : "dim",
          metricIds: step.metrics.map((m) => m.id),
          dimensionIds: step.dims.map((d) => d.id),
        });
        if (res && res.code === 200) {
          if (type === "metric") step.candidateMetrics = res.data || [];
          else step.candidateDims = res.data || [];
        }
      } catch (e) {
        /* handled */
      } finally {
        step.loadingCandidate = false;
      }
    },

    openFilter(name, tabName, step) {
      //this.$refs[`activeName${tabName}`][0].setCurrentName(`first${tabName}`);

      if (
        this.$refs[`activeName${tabName}`] &&
        this.$refs[`activeName${tabName}`][0].setCurrentName
      ) {
        this.$refs[`activeName${tabName}`][0].setCurrentName(`first${tabName}`);
      }

      if (this.$refs[name] && this.$refs[name][0].show) {
        this.$refs[name][0].show();
      }

      this.dimsFilterTemp = [];
      this.metricsFilterTemp = [];
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

    async addFilters(step, v, kind) {
      //const [kind, key] = String(raw).split(':');
      const key = v.key;
      //if (step.filters.some((f) => f.key === key)) return;
      const isDim = kind === "dim";
      const meta = (isDim ? step.dims : step.metrics).find(
        (x) => x.key === key
      );
      if (!meta) return;
      let filter = {
        uid: nextUid(),
        type: isDim ? filterTypeEnum.dim.value : filterTypeEnum.metric.value,
        id: meta.id,
        key: meta.key,
        name: meta.name,
        operator: isDim ? 5 : 0,
        value: isDim ? [] : "",
        options: [],
        loadingOptions: false,
      };
      if (isDim) await this.loadFilterValues(step, filter);
      step.filters.push(filter);

      step.dirty = true;
    },

    async sureFilter(name, step) {
      step.loadingFilterMeta = true;
      for (let i = 0; i < this.dimsFilterTemp.length; i++) {
        await this.addFilters(step, this.dimsFilterTemp[i], "dim");
        step.dirty = true;
      }
      for (let i = 0; i < this.metricsFilterTemp.length; i++) {
        await this.addFilters(step, this.metricsFilterTemp[i], "metrics");
        step.dirty = true;
      }
      step.loadingFilterMeta = false;

      if (this.$refs[name] && this.$refs[name][0].show) {
        this.$refs[name][0].hide();
      }
    },

    deleteFilter(index, step) {
      step.filters.splice(index, 1);
      step.dirty = true;
    },

    isMulti(f) {
      return f.type === 0 && isMultiValueOperator(f.operator);
    },

    onOperatorConfirm(f, step) {
      //f.operator = op;
      // 值类型随运算符切换（数组 <-> 标量）
      if (this.isMulti(f)) {
        f.value = Array.isArray(f.value) ? f.value : f.value ? [f.value] : [];
      } else {
        f.value = Array.isArray(f.value)
          ? f.value[0] || ""
          : f.value == null
          ? ""
          : f.value;
      }
      step.dirty = true;
    },

    onValuesConfirm(step){
      step.dirty = true;
    },

    /*async dimensionValues(id) {
      return getDimensionValuesAPI(id)
        .then((response) => {
          if (response.code == 200) {
            return response.data;
          } else {
            return [];
          }
        })
        .catch(() => {
          return [];
        })
        .finally(() => {
          //loading.close();
        });
    },*/

    //展现形式改变
    handleCommandChartType(val, step) {
      step.chartType = val;
    },

    showSQL(viewSql) {
      this.viewSql = viewSql;
      this.isSqlPage = true;
    },
    ///////////////////////////

    async applyNewQuery(step, page) {
      step.queryParams = buildDataPayload(step, {
        page: page || step.data.page,
        pageSize: PAGE_SIZE,
      });

      const r = await this.applyQuery(step, {page:page, limit:PAGE_SIZE});
      if (r == false) return;

      step.dirty = false;
    },

    //查询数据
    async applyQuery(step, page) {
      if (!step.dims.length && !step.metrics.length) {
        toast.warn("请至少选择一个维度或指标");
        return false;
      }

      if (step.queryParams == null) {
        step.queryParams = buildDataPayload(step, {
          page: page.page,
          pageSize: page.limit,
        });
      } else {
        //step.data.page = 1
        step.queryParams.page = page.page || step.data.page;
        step.queryParams.pageSize = page.limit || step.data.pageSize;
      }

      step.loadingData = true;
      step.error = "";
      try {
        const res = await getMetricsDataPreviewAPI(step.queryParams);
        if (res && res.code === 200) {
          const d = res.data;
          step.data.columns = d.columns || [];
          step.data.records = d.records || [];
          step.data.page = d.page || 1;
          step.data.pageSize = d.pageSize || PAGE_SIZE;
          step.data.total = d.total || 0;
          step.data.totalPage = Math.max(
            1,
            Math.ceil((d.total || 0) / (d.pageSize || PAGE_SIZE))
          );
          step.viewSql = d.sql || step.viewSql;
        } else {
          step.error = (res && res.message) || "查询失败";
        }
      } catch (e) {
        step.error = "查询失败";
      } finally {
        step.loadingData = false;
      }
    },

    getComputedListByPage(step) {
      const page = step.data.page;
      const pageSize = step.data.pageSize;

      let start = (page - 1) * pageSize;
      let end = start + pageSize;

      step.data.records = [...step.data.allRecords].slice(start, end);
    },
    ///////////////////////////
  },
};
</script>


<style scoped lang="scss">
.title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
}

.ai-Title-No {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: var(--accent-gradient, linear-gradient(135deg, #3b82f6, #06b6d4));
  color: rgb(255, 255, 255);
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(43, 92, 255, 0.25);
}

.ai-Title-Type {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  height: 20px;
  display: flex;
  align-content: center;
  padding: 2px 10px;
  flex-wrap: wrap;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  justify-content: center;

  &--compute {
    background: rgba(6, 182, 212, 0.12);
    color: #0e7490;
  }

  &--analyze {
    background: rgba(139, 92, 246, 0.12);
    color: #6d28d9;
  }

  &--summary {
    background: rgba(16, 185, 129, 0.12);
    color: #047857;
  }
}

/* ---------- 思考徽标 / 步骤徽标 / 工具按钮 ---------- */
.think-badge {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(135deg, #4f8cff, #2b5cff);
  box-shadow: 0 2px 6px rgba(43, 92, 255, 0.28);
  flex-shrink: 0;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  border-radius: 8px;

  .base-icon {
    vertical-align: middle;
  }
}

.question-show-btn {
  //width: 40px;
  cursor: pointer;
  height: 36px !important;
  line-height: 36px;
  max-width: 100px;
  //display: inline-flex;
  //align-items: center;
  padding: 0 10px !important;
  border-radius: 12px;

  border: none;
  box-shadow: 0 4px 12px rgba(43, 92, 255, 0.3);
  transition: all 0.15s;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.4);
  }

  &:hover {
    background: linear-gradient(135deg, #4f8dfa 0%, #3b6cff 55%, #6156ee 100%);
    background-position: 100% 50%;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.38);
    border: none;
    color: white !important;
  }

  &.is-active {
    background: linear-gradient(135deg, #4f8dfa 0%, #3b6cff 55%, #6156ee 100%);
    background-position: 100% 50%;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.38);
    border: none;
    color: white !important;
  }

  &.is-disabled,
  &.is-disabled:hover {
    color: #c0c4cc;
    transform: none;
    box-shadow: none;
  }
}

.no-scrollbar {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.no-scrollbar::-webkit-scrollbar {
  overflow: hidden auto;
  display: none;
}

/* 分析/总结结果文字卡片: 多段渐变底 + 内高光, 体现质感 */
.ai-result-card {
  //padding: 14px 18px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #3f4a63;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 1px 4px rgba(30, 41, 59, 0.04);
}

.ai-result-card--analyze {
  background: linear-gradient(
    130deg,
    #f0f7ff 0%,
    #eef4ff 30%,
    #f0f0ff 65%,
    #f6f1ff 100%
  );
  border: 1px solid rgba(99, 102, 241, 0.16);
  max-height: 400px;
  overflow-y: auto;
}

.ai-result-card--summary {
  background: linear-gradient(
    130deg,
    #eff8ff 0%,
    #ecfbf9 40%,
    #f0fdf6 75%,
    #f3fcff 100%
  );
  border: 1px solid rgba(16, 185, 129, 0.2);
  min-height: 80px;
  max-height: 400px;
  overflow-y: auto;
}

.think-content {
  //margin: 8px 10px 10px;
  //padding: 0px 16px 12px 16px;
  padding: 15px;
  border-radius: 10px;
  background: linear-gradient(130deg, #f8fbff 0%, #f5f9ff 55%, #f3fbfe 100%);
  border: 1px solid rgba(43, 92, 255, 0.08);

  max-height: 400px; /* 限制高度，出现滚动条，便于测试懒加载 */
  min-height: 80px;
  overflow-y: auto;
}

.user-Panel {
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
  max-width: 80%;
  padding: 10px 16px;
  min-height: 42px;
  font-size: 14px;
  line-height: 1.6;

  background: linear-gradient(
    135deg,
    #e8efff 0%,
    #e3f0ff 40%,
    #ddf3ff 75%,
    #e6f0ff 100%
  );
  border: 1px solid rgba(43, 92, 255, 0.16);
  border-radius: 16px 4px 16px 16px;
  color: var(--text-primary, #1e293b);
  box-shadow: 0 2px 8px rgba(43, 92, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.ai-Panel {
  text-align: left;
  width: 80%;
  background-color: rgba(255, 255, 255, 0.92);
  margin-left: 50px;
  border: 1px solid var(--border-light, #e5eaf1);
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(30, 41, 59, 0.05);
  backdrop-filter: blur(6px);
}

.el-collapse {
  border-top: 0;
  border-bottom: 0;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}

.el-collapse-item__content {
  padding-bottom: 0px;
}

.ai-Disable-Collapse {
  ::v-deep .el-icon-arrow-right:before {
    //content: "";
    display: none;
  }
  ::v-deep .el-collapse-item__header {
    position: relative;
    overflow: hidden;
    padding: 10px 24px;
    height: 44px;
    color: #5b5e7a;
    font-weight: 500;
    border: 1px solid rgba(43, 92, 255, 0.14);
    border-radius: 12px;
    /* 水波纹式光晕: 同色系蓝调渐变自左向右缓慢流动 */
    background: linear-gradient(
      100deg,
      #f0f6ff 0%,
      #e9f2ff 30%,
      #e7f6fd 50%,
      #e9f2ff 70%,
      #f0f6ff 100%
    );
    background-size: 220% 100%;
    animation: think-wave 5.5s ease-in-out infinite;
  }
  /* 高光扫过(水波光晕) */
  ::v-deep .el-collapse-item__header::after {
    content: "";
    position: absolute;
    top: 0;
    left: -70%;
    width: 55%;
    height: 100%;
    background: linear-gradient(
      105deg,
      transparent 0%,
      rgba(255, 255, 255, 0.5) 50%,
      transparent 100%
    );
    animation: think-shine 4.8s ease-in-out infinite;
    pointer-events: none;
  }
  ::v-deep .el-collapse-item__content {
    padding-bottom: 0px;
  }
  ::v-deep .el-collapse-item:last-child {
    margin-bottom: -1px;
    //border: 1px solid rgb(232, 234, 237);
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__wrap {
    border-radius: 10px;
  }
}

@keyframes think-wave {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

@keyframes think-shine {
  0% {
    left: -70%;
  }
  45% {
    left: 120%;
  }
  100% {
    left: 120%;
  }
}

.ai-Think-Collapse {
  ::v-deep .el-collapse-item__header {
    background: linear-gradient(120deg, #f4f8ff 0%, #eef5ff 50%, #f0faff 100%);
    padding: 10px 24px;
    height: 44px;
    color: #4c5670;
    border: 1px solid rgba(43, 92, 255, 0.12);
    border-radius: 12px;
    transition: box-shadow 0.2s ease, border-color 0.2s ease,
      transform 0.2s ease;
  }
  ::v-deep .el-collapse-item__header:hover {
    border-color: rgba(43, 92, 255, 0.28);
    box-shadow: 0 4px 14px rgba(43, 92, 255, 0.12);
    transform: translateY(-1px);
  }
  ::v-deep .el-collapse-item:last-child {
    margin-bottom: -1px;
    //border: 1px solid rgb(232, 234, 237);
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__wrap {
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__content {
    padding-bottom: 0px;
  }
}

.ai-Collapse {
  ::v-deep .el-collapse-item__header {
    background: linear-gradient(120deg, #f7faff 0%, #f4f8ff 55%, #f2fbfd 100%);
    padding: 10px 24px;
    height: 44px;
    color: #47506b;
    border: 1px solid rgba(59, 130, 246, 0.13);
    border-radius: 12px;
    transition: box-shadow 0.22s ease, border-color 0.22s ease,
      transform 0.22s ease, background 0.22s ease;
  }
  ::v-deep .el-collapse-item__header:hover {
    background: linear-gradient(120deg, #f0f6ff 0%, #edf4ff 55%, #e9f9fc 100%);
    border-color: rgba(59, 130, 246, 0.35);
    box-shadow: 0 6px 18px rgba(43, 92, 255, 0.13);
    transform: translateY(-2px);
  }
  ::v-deep .el-collapse-item:last-child {
    margin-bottom: -1px;
    //border: 1px solid rgb(232, 234, 237);
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__wrap {
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__content {
    padding-bottom: 0px;
  }
}

.ai-Analysis-Collapse {
  ::v-deep .el-collapse-item__header {
    background: linear-gradient(120deg, #f2f6ff 0%, #f1f0fe 60%, #f5f0ff 100%);
    padding: 10px 24px;
    height: 42px;
    color: #4c5470;
    border: 1px solid rgba(99, 102, 241, 0.13);
    border-radius: 12px;
    transition: box-shadow 0.2s ease, border-color 0.2s ease,
      transform 0.2s ease;
  }
  ::v-deep .el-collapse-item__header:hover {
    border-color: rgba(99, 102, 241, 0.32);
    box-shadow: 0 4px 14px rgba(99, 102, 241, 0.13);
    transform: translateY(-1px);
  }
  ::v-deep .el-collapse-item:last-child {
    margin-bottom: -1px;
    //border: 1px solid rgb(232, 234, 237);
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__wrap {
    border-radius: 10px;
  }
  ::v-deep .el-collapse-item__content {
    padding-bottom: 0px;
  }
}

.query-tableBox {
  border-bottom-left-radius: 10px;
  border-bottom-right-radius: 10px;
}

.compute-tableBox {
  border-radius: 10px;
}

.chatType-DropDown {
  width: 140px;
  height: 32px;
  background: #e1eafc;
  border-radius: 4px;
  border: 1px solid #e7e9ec;
  cursor: pointer;
  padding: 0 3px 0 6px;
  line-height: 32px;
  font-size: 14px;
  color: #0b0b0b;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.queryDim-Panel {
  display: flex;
  align-items: center;
  gap: 5px;
  background: linear-gradient(135deg, #e3f1fe, #dbf3fd);
  border: 1px solid rgba(59, 130, 246, 0.16);
  color: #0369a1;
  padding: 0 14px;
  border-radius: 999px;
  height: 30px;
  margin-right: 5px;
  transition: box-shadow 0.18s ease, transform 0.18s ease,
    border-color 0.18s ease;
  &:hover {
    border-color: rgba(59, 130, 246, 0.38);
    box-shadow: 0 3px 10px rgba(59, 130, 246, 0.16);
    transform: translateY(-1px);
  }
}

/* 标签文本：超长单行省略；仅当确实被截断时才显示 Tooltip（TruncateTip 组件控制） */
.queryDim-Panel__text {
  max-width: 7em; /* 约 7 个汉字，超出即省略 */
  cursor: default;
}

/* ---------- 行级布局（维度/指标/筛选器公用，尺寸自适应） ---------- */
.query-row--filter {
  padding: 10px 20px;
  background: #fafbfe;
  display: flex;
  justify-content: space-between;
  border-bottom: 1px solid #eaeaea;
}

.query-row__main {
  display: flex;
  align-items: flex-start;
  flex: 1 1 auto;
  min-width: 0; /* 允许内部收缩，防止撑破 */
}

.query-row__label {
  flex: 0 0 60px;
  width: 60px;
  line-height: 30px;
  color: #303a4e;
  font-weight: 500;
  white-space: nowrap;
}

.query-row__tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
  padding: 2px 0;
}

/* ---------- 筛选条件区：响应式胶囊卡片 ---------- */
.filter-area {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: row;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-conds {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
}

/* 单个条件：圆角胶囊，内部元素无缝拼接 */
.filter-cond {
  display: inline-flex;
  align-items: stretch;
  border: 1px solid #dde3ee;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  &:hover {
    border-color: rgba(43, 92, 255, 0.4);
    box-shadow: 0 2px 8px rgba(43, 92, 255, 0.08);
  }
  &:focus-within {
    border-color: #2b5cff;
    box-shadow: 0 0 0 2px rgba(43, 92, 255, 0.12);
  }

  /* 胶囊内部控件去边框，由外层胶囊统一描边 */
  ::v-deep .el-input__inner,
  ::v-deep .el-range-editor.el-input__inner {
    border: none !important;
    border-radius: 0 !important;
    height: 30px;
    line-height: 30px;
    background: #fff !important;
  }

  ::v-deep .filter-Style .el-input__inner {
    background: #f7f9fd !important;
    border-right: 1px solid #e6eaf3 !important;
    text-align: center;
  }

  ::v-deep .el-date-editor .el-range-input {
    background: transparent;
  }

  ::v-deep .el-select__tags {
    max-width: calc(100% - 24px) !important;
    flex-wrap: nowrap;
    overflow: hidden;
    height: 28px;
  }
  ::v-deep .el-select__tags > span {
    display: inline-flex;
    align-items: center;
    flex-wrap: nowrap;
  }
}

/* 条件名（字段名）：固定宽度，超长省略，保证每个筛选器长度一致 */
.filter-cond__name {
  display: block;
  box-sizing: border-box;
  width: 108px;
  padding: 0 10px;
  height: 30px;
  line-height: 30px;
  background: #f2f5fb;
  color: #303a4e;
  font-weight: 500;
  border-right: 1px solid #e6eaf3;
  flex: 0 0 auto;
}

.filter-cond__field {
  width: 88px;
  flex: 0 0 auto;
}

.filter-cond__op {
  width: 84px;
  flex: 0 0 auto;
}

/* 88 + 240 = 328，与动态条件胶囊总长一致 */
.filter-cond__date {
  width: 240px;
  flex: 0 0 auto;
  ::v-deep .el-range-separator {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 30px;
    height: 30px;
    padding: 0;
    width: 10%;
    font-size: 12px;
    color: #98a2b8;
  }
  /* 日期区间：分隔符"至"垂直水平居中、两侧输入均分宽度 */
  ::v-deep .el-range-input {
    width: 42%;
    font-size: 13px;
  }
  ::v-deep .el-range__icon {
    line-height: 30px;
    margin-left: 2px;
  }
  ::v-deep .el-range__close-icon {
    line-height: 30px;
    width: 16px;
  }
}

/* 值区域：固定宽度，所有条件胶囊总长一致（108+84+110+26） */
.filter-cond__value {
  //width: 110px;
  flex: 0 0 auto;
}

/* 多选值：输入框高度锁定 30px，不随标签数量变高 */
.filter-cond__value--select {
  ::v-deep .el-input__inner {
    height: 30px !important;
  }
  ::v-deep .el-tag {
    display: inline-flex;
    align-items: center;
    height: 20px;
    line-height: 18px;
    margin: 0 0 0 4px;
    padding: 0 4px;
    flex: 0 0 auto;
    max-width: 100px;
    background: #eef3ff;
    border-color: #dbe4ff;
    color: #2b5cff;
  }
  ::v-deep .el-tag .el-select__tags-text {
    display: inline-block;
    max-width: 85px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: middle;
  }
  ::v-deep .el-tag .el-tag__close {
    flex: 0 0 auto;
    margin-left: 2px;
    transform: scale(0.8);
    background: transparent;
    color: #7c8db5;
  }
  ::v-deep .el-tag .el-tag__close:hover {
    background: #2b5cff;
    color: #fff;
  }

  .clear-btn {
    display: inline-flex;
    position: absolute;

    right: -213px;
    top: -2px;
    padding: 0 10px;
    line-height: 30px;
    background: transparent;

    width: 10px;
    height: 20px;
    overflow: hidden;
    cursor: pointer;
    z-index: 1;

    .triangle {
      position: absolute;
      top: 0px;
      right: 20px;
      width: 40px;
      height: 40px;
      transform: rotate(220deg);
      transform-origin: top right;
      background: #d7d4d4;
      border-radius: 0 0 0 50%;
      border-radius: 50px;
    }
    .x-mark {
      position: absolute;
      top: 1px;
      right: 12px;
      font-size: 10px;
      font-weight: bold;
      color: #8b8585;
      z-index: 2;
      pointer-events: none;
      line-height: 1;
      transform: rotate(142deg);
    }
  }
}

.filter-area__actions {
  padding-top: 10px;
  display: flex;
}

.filter-area__search {
  height: 30px;
  padding: 0 20px;
}

/* 窄屏：条件胶囊内部允许换行 */
@media (max-width: 768px) {
  .filter-cond {
    flex-wrap: nowrap;
  }
}

/* 使用深度选择器穿透 scoped 样式限制 */
.filter-Style {
  ::v-deep .el-input__inner {
    height: 30px; /* 修改输入框高度 */
    background: #f7f8fa !important;
    text-align: center;
    border-radius: 0px;
  }
  ::v-deep .el-input__suffix {
    top: 0; /* 或根据实际情况调整 */
    display: flex;
    align-items: center;
    /* 或者使用 height: 100%; */
  }
  ::v-deep .el-input__icon {
    line-height: 30px; /* 与输入框高度保持一致 */
  }
}

.filter-Date {
  height: 30px;
  ::v-deep .el-range__icon {
    line-height: 30px;
  }
  ::v-deep .el-range__close-icon {
    line-height: 30px;
  }
  ::v-deep .el-range-separator {
    line-height: 24px;
  }
}

.filter-input {
  ::v-deep .el-input__inner {
    line-height: 30px;
    height: 30px;
    border-radius: 0px;
    padding: 0 15px;
  }

  .el-input ::v-deep .el-input__suffix {
    position: relative;
    height: 30px;
    line-height: 30px;
  }

  /* 清除按钮容器（负责裁剪） */
  .clear-btn {
    display: inline-flex;
    position: absolute;

    right: -4px;
    top: 0px;
    padding: 0 10px;
    line-height: 30px;
    background: transparent;

    width: 10px;
    height: 20px;
    overflow: hidden;
    cursor: pointer;
    z-index: 1;

    .triangle {
      position: absolute;
      top: -1px;
      right: 20px;
      width: 40px;
      height: 40px;
      transform: rotate(220deg);
      transform-origin: top right;
      background: #d7d4d4;
      border-radius: 0 0 0 50%;
      border-radius: 50px;
    }
    .x-mark {
      position: absolute;
      top: 1px;
      right: 12px;
      font-size: 10px;
      font-weight: bold;
      color: #8b8585;
      z-index: 2;
      pointer-events: none;
      line-height: 1;
      transform: rotate(142deg);
    }
  }
}

.analysis {
}

/* ===== 添加筛选器卡片 ===== */
.filter-pop-menu {
  padding: 0 !important;
  border-radius: 14px !important;
  border: 1px solid rgba(43, 92, 255, 0.1) !important;
  box-shadow: 0 12px 32px rgba(30, 60, 120, 0.14) !important;
  overflow: hidden;
}

.filter-pop {
  width: 264px;
}

.filter-pop__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #2c3550;
  background: linear-gradient(120deg, #f4f8ff 0%, #f0f7ff 60%, #f2fbfe 100%);
  border-bottom: 1px solid rgba(43, 92, 255, 0.08);
}

.filter-pop__icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: #fff;
  background: linear-gradient(135deg, #4f8cff, #2b5cff);
  box-shadow: 0 2px 5px rgba(43, 92, 255, 0.25);
  flex-shrink: 0;
}

.filter-pop__body {
  padding: 4px 8px 0;
  ::v-deep .el-tabs__header {
    margin-bottom: 6px;
  }
  ::v-deep .el-tabs__item {
    font-size: 12.5px;
    height: 36px;
    line-height: 36px;
    color: #66758a;
    transition: color 0.2s ease;
  }
  ::v-deep .el-tabs__item.is-active {
    color: var(--brand, #2b5cff);
    font-weight: 600;
  }
  ::v-deep .el-tabs__active-bar {
    background: linear-gradient(90deg, #3b82f6, #2b5cff);
    height: 3px;
    border-radius: 3px;
  }
  ::v-deep .el-tabs__nav-wrap::after {
    height: 1px;
    background-color: rgba(43, 92, 255, 0.08);
  }
}

.filter-pop__list {
  width: 100%;
  max-height: 200px;
  overflow-y: auto;
  padding: 2px 8px 6px;
  .filter-Panel {
    display: flex;
    align-items: center;
    //width: 100%;
    margin: 0 0 2px;
    padding: 7px 10px;
    border-radius: 8px;
    transition: background 0.15s ease;
    ::v-deep .el-checkbox__label {
      font-size: 12.5px;
      color: #3c465e;
      width: 95px;
    }
  }
  .filter-Panel:hover {
    background: rgba(43, 92, 255, 0.05);
  }
}

.filter-pop__footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px 12px;
  border-top: 1px solid rgba(43, 92, 255, 0.08);
  background: #fbfcff;
  .el-button--mini {
    padding: 6px 18px;
    border-radius: 8px;
  }
}

.thinkPage {
  ::v-deep .block-wrapper .markdown-body.has-h3 {
    &::before {
      content: "";
      position: absolute;
      left: 0px; /* 与圆圈中心对齐 */
      top: 28px;
      bottom: 0;
      width: 2px;
      background: #dcdfe6;
    }
    :last-child::before {
      display: none;
    }
    p {
      margin-left: 10px;
      margin-top: 0;
      margin-bottom: 8px;
    }
    ul,
    ol {
      margin-left: 20px;
      margin-bottom: 8px;
    }

    h3 {
      position: relative;
      margin: 0 0 8px 0;
      padding-left: 0;
      font-size: 16px;
      font-weight: 600 !important;
      color: #333;
      &::before {
        content: attr(data-step);
        position: absolute;
        left: -26px; /* 相对于 .block-wrapper 的 padding-left */
        top: 2px;
        width: 24px;
        height: 24px;
        line-height: 24px;
        text-align: center;
        background: #409eff;
        color: #fff;
        border-radius: 50%;
        font-size: 12px;
        font-weight: bold;
        z-index: 1;
        margin-left: 0px;
      }
      &::after {
        display: none !important;
      }
    }
  }

  ::v-deep .markdown-body h3 {
    position: relative;
    margin: 0 0 8px 0;
    padding-left: 0;
    font-size: 16px;
    font-weight: 600 !important;
    color: #333;
    margin-left: 15px;

    &::before {
      content: attr(data-step);
      position: absolute;
      left: -26px; /* 相对于 .block-wrapper 的 padding-left */
      top: 2px;
      width: 24px;
      height: 24px;
      line-height: 24px;
      text-align: center;
      background: #409eff;
      color: #fff;
      border-radius: 50%;
      font-size: 12px;
      font-weight: bold;
      z-index: 1;
      margin-left: 0px;
    }
    &::after {
      display: none !important;
    }
  }

  ::v-deep .markdown-body.has-h3 {
    position: relative;
    padding-left: 15px;
    margin-top: 15px;
    margin-bottom: 24px;
  }

  ::v-deep .markdown-body:not(.has-h3) {
    margin-top: 15px;
    margin-bottom: 8px;
    padding-left: 0px;
  }
}

.one-bgdiv {
  width: 100%;
  text-align: left;
  padding: 0px 5px 0 0;
}

::v-deep .el-tabs__item {
  line-height: 24px;
  height: 24px;
  font-size: 12px;
}

::v-deep .el-tabs__header {
  margin: 0 0 5px;
}

.agent-logo {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  margin-right: 8px;
  object-fit: contain;
}

/*::v-deep .el-table th.el-table__cell {
  background-color: rgb(248, 249, 251);
  color: rgb(71, 85, 105);
}*/

.el-dropdown {
  display: inline-block;
  position: relative;
  color: #606266;
  font-size: 14px;
}

.el-popper {
  padding: 0px;
  margin-top: 0px;
}

.think-loading {
  ::v-deep .el-loading-spinner .circular {
    height: 24px;
    width: 24px;
  }
  ::v-deep .el-loading-spinner {
    display: flex;
    gap: 5px;
    margin-top: -10px;
  }
  ::v-deep .el-loading-spinner .el-loading-text {
    font-size: 13px;
    height: 24px;
    line-height: 15px;
  }
  ::v-deep .el-loading-mask {
    background: transparent;
  }
}

.question-loading {
  ::v-deep .el-loading-spinner .circular {
    height: 48px;
    width: 48px;
  }

  ::v-deep .el-loading-spinner .el-loading-text {
    font-size: 14px;
  }
  ::v-deep .el-loading-mask {
    background: transparent;
  }
}

.dropdown-loading {
  ::v-deep .el-loading-spinner .circular {
    width: 24px;
    height: 24px;
  }
  ::v-deep .el-loading-spinner .el-loading-text {
    font-size: 10px;
  }

  ::v-deep .el-loading-mask {
    background: transparent;
  }
}

.scrollTop {
  border-radius: 50%;
  padding: 6px;
  height: 32px;
  width: 32px;
  display: inline-block;
  transform: rotate(180deg);
  background: rgb(255 255 255 / 50%);

  &:hover {
    display: inline-block;
    transform: rotate(180deg);
    background: rgb(255 255 255 / 100%);
  }
  &:focus {
    display: inline-block;
    transform: rotate(180deg);
    background: rgb(255 255 255 / 100%);
  }

  ::v-deep i {
    font-size: 14px;
  }
}

.scrollBottom {
  border-radius: 50%;
  padding: 6px;
  height: 32px;
  width: 32px;
  display: inline-block;

  background: rgb(255 255 255 / 50%);

  &:hover {
    display: inline-block;

    background: rgb(255 255 255 / 100%);
  }
  &:focus {
    display: inline-block;

    background: rgb(255 255 255 / 100%);
  }

  ::v-deep i {
    font-size: 14px;
  }
}

.agent-sidebar__action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted, #94a3b8);
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: rgba(43, 92, 255, 0.12);
    color: var(--brand, #2b5cff);
  }

  &--danger:hover {
    background: rgba(239, 68, 68, 0.1);
    color: #ef4444;
  }
}

.history-sidebar__item {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  &:hover {
    background: rgba(43, 92, 255, 0.06);

    .history-sidebar__item-name {
      color: var(--brand, #2b5cff);
    }

    .history-sidebar__item-icon {
      color: var(--brand, #2b5cff);
    }
  }

  &.is-active {
    background: rgba(43, 92, 255, 0.06);

    .history-sidebar__item-name {
      color: var(--brand, #2b5cff);
      font-weight: 600;
    }

    .history-sidebar__item-icon {
      color: var(--brand, #2b5cff);
    }
  }
  &.is-thinking {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.history-sidebar__item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--text-secondary, #475569);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
  transition: color 0.15s;
  &.is-thinking {
    opacity: 0.7;
    cursor: not-allowed;
    pointer-events: none;
  }
}

.rec-item {
  min-height: 64px;
  max-height: 122px;
  border-radius: 15px;
  border: 1px solid rgba(82, 154, 255, 0.16);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.86),
    rgba(244, 249, 255, 0.74)
  );
  padding: 12px 13px;
  cursor: pointer;
  transition: 0.22s;
  position: relative;
  overflow: hidden;
}

.rec-item::after {
  content: "";
  position: absolute;
  right: -22px;
  top: -22px;
  width: 54px;
  height: 54px;
  border-radius: 999px;
  background: rgba(49, 157, 255, 0.12);
}

.rec-item:hover {
  transform: translateY(-2px);
  border-color: rgba(32, 136, 255, 0.35);
  box-shadow: 0 14px 28px rgba(36, 120, 220, 0.14);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.96),
    rgba(232, 246, 255, 0.88)
  );
}

.recommend-panel {
  //position: absolute;
  //left: 0;
  //right: 0;
  //bottom: 104px;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.95),
    rgba(240, 248, 255, 0.92)
  );
  border: 1px solid rgba(65, 150, 255, 0.24);
  border-radius: 22px;
  box-shadow: 0 24px 70px rgba(31, 100, 210, 0.18),
    inset 0 0 0 1px rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(22px);
  padding: 18px;
  display: none;
  transform: translateY(12px) scale(0.98);
  opacity: 0;
  transition: 0.22s ease;
  overflow: hidden;

  &.is-show {
    display: block;
    opacity: 1;
    transform: translateY(0) scale(1);
    animation: panelIn 0.24s ease;
  }
}

@keyframes panelIn {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.recommend-panel::before {
  content: "";
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  right: -80px;
  top: -120px;
  background: radial-gradient(
    circle,
    rgba(50, 171, 255, 0.22),
    transparent 68%
  );
  pointer-events: none;
}

.recommend-panel::after {
  content: "";
  position: absolute;
  width: 160px;
  height: 160px;
  border-radius: 999px;
  left: -50px;
  bottom: -70px;
  background: radial-gradient(circle, rgba(87, 92, 255, 0.12), transparent 70%);
  pointer-events: none;
}

.homeRecommend {
  background: transparent;
  min-height: 45px;
  border: 1px solid rgb(186 186 186 / 48%);
  color: var(--text-primary, #1e293b);
  box-shadow: rgba(43, 92, 255, 0.1) 0px 2px 8px,
    rgba(255, 255, 255, 0.85) 0px 1px 0px inset;
  border-radius: 20px;

  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 14px;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(178, 178, 178, 0.4);
  }
}

.chat__sep {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0 -2px;

  &::before,
  &::after {
    content: "";
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, rgba(199, 220, 251, 0), #c7dcfb);
  }
  &::after {
    background: linear-gradient(90deg, #c7dcfb, rgba(199, 220, 251, 0));
  }
}

.chat__septext {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  color: #93a4bd;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid #eaf1fb;
}

@keyframes fxScan {
  0% {
    left: 0%;
    //transform: translate3d(-100%, 0, 0);
  }
  100% {
    left: calc(80% - 20%);
    //transform: translate3d(300%, 0, 0);
  }
}

.step__scan {
  position: absolute;
  top: 0;
  left: 0;
  width: 34%;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(
    90deg,
    rgba(46, 107, 230, 0) 0%,
    rgba(46, 107, 230, 0.8) 46%,
    rgba(34, 184, 207, 0) 100%
  );
  animation: fxScan 0.9s cubic-bezier(0.45, 0.05, 0.55, 0.95) infinite;
  z-index: 2;
  pointer-events: none;
}

.dirty-button {
  background: linear-gradient(135deg, #f2a53a 0%, #e79217 55%, #e88c0d 100%);
  background-size: 160% 160%;
  background-position: 0% 50%;
  border: none;
  box-shadow: 0 2px 8px rgba(231, 146, 23, 0.26);

  &:hover,
  &:focus {
    background: linear-gradient(135deg, #f7a635 0%, #de8e1d 55%, #ec8d07 100%);
    background-position: 100% 50%;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(231, 146, 23, 0.26);
    border: none;
    color: white !important;
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 6px rgba(231, 146, 23, 0.26);
  }

  &.is-disabled,
  &.is-disabled:hover {
    background: linear-gradient(135deg, #f2a53a, #ec8e0b);
    transform: none;
    box-shadow: none;
  }
}
</style>
