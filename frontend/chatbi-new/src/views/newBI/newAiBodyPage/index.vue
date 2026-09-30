<template>
  <div class="my-custom-style agent-config">
    <el-form label-position="top" size="small" class="agent-config__form">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="智能体名称" required>
            <el-input
              v-model="agentDetail.name"
              placeholder="如:销售分析智能体"
            ></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="描述">
            <el-input
              v-model="agentDetail.description"
              placeholder="简要描述该智能体的用途"
            ></el-input>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-tabs v-model="activeName" class="agent-config__tabs">
      <el-tab-pane name="first">
        <span slot="label" class="agent-config__tab-label">
          <base-icon name="table" :size="14" /> 数据表
        </span>

        <div class="agent-config__panes" style="max-height: 20%;margin-bottom:10px;">
          <!-- 左:候选表 -->
          <div class="agent-config__pane">
            <div class="agent-config__pane-title">选择数据表</div>
            <div class="agent-config__toolbar">
              <el-select
                placeholder="选择数据源"
                v-model="queryParams.sourceId"
                @change="getAiBodyCandidateTables"
                size="small"
                style="width: 46%"
              >
                <el-option
                  v-for="item in dataSourceList"
                  :key="item.id"
                  :label="`${item.name}(${item.dbType})`"
                  :value="item.id"
                >
                </el-option>
              </el-select>
              <el-input
                v-model="queryParams.keyword"
                placeholder="搜索表名..."
                size="small"
                style="flex: 1"
                @keyup.enter.native="getAiBodyCandidateTables"
              >
                <base-icon
                  slot="suffix"
                  name="search"
                  :size="14"
                  style="margin: 8px 6px 0 0; color: #94a3b8"
                />
              </el-input>
            </div>

            <div class="agent-config__table-wrap no-scrollbar">
              <div class="drag-table">
                <el-table
                  ref="multipleTable"
                  :data="candidateTables"
                  style="width: 100%"
                  border
                  @selection-change="handleSelectionChange"
                  stripe
                >
                  <el-table-column label="序号" type="selection" width="55"/>

                  <el-table-column-with-tooltip
                    label="表名"
                    prop="tableName"
                    width="auto"
                    min-width="13%"
                    resizable
                    sortable
        
                    :render-header="renderHeader"
                  />

                  <el-table-column-with-tooltip
                    label="注释"
                    prop="tableComment"
                    width="auto"
                    min-width="13%"
                    resizable
                    sortable
              
                    :render-header="renderHeader"
                  />
                </el-table>
              </div>
            </div>
          </div>

          <!-- 右:已选关系 -->
          <div class="agent-config__pane">
            <div class="agent-config__pane-title">已选数据表(按数据源分组)</div>
            <div
              class="agent-config__table-wrap agent-config__table-wrap--tall no-scrollbar"
            >
              <div class="drag-table">
                <el-table
                  :data="tableRelations"
                  style="width: 100%"
                  border
                  stripe
                  class="expand-table"
                >
                  <el-table-column type="expand" width="20px">
                    <template slot-scope="props">
                      <el-table
                        :data="props.row.relations"
                        style="width: 100%"
                        :row-style="{ backgroundColor: '#F5F7FA' }"
                        border
                      >
                        <el-table-column-with-tooltip
                          label="表名"
                          prop="tableName"
                          width="auto"
                          min-width="40%"
                          resizable
                          sortable
                   
                          :render-header="renderHeader"
                        />

                        <el-table-column-with-tooltip
                          label="注释"
                          prop="tableComment"
                          width="auto"
                          min-width="40%"
                          resizable
                          sortable
          
                          :render-header="renderHeader"
                        />
                        <el-table-column
                          label="操作"
                          align="center"
                          :resizable="false"
                          min-width="20%"
                        >
                          <template slot-scope="scope">
                            <el-tooltip content="删除" :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay">
                            <el-button
                              type="text"
                              class="op-icon-btn is-danger"
                              @click="deleteSelection(scope.row)"
                              ><base-icon name="trash" :size="14"
                            /></el-button>
                            </el-tooltip>
                          </template>
                        </el-table-column>
                      </el-table>
                    </template>
                  </el-table-column>

                  <el-table-column-with-tooltip
                    label="表名"
                    prop="name"
                    width="auto"
                    min-width="40%"
                    resizable
                    sortable

                    :render-header="renderHeader"
                  />

                  <el-table-column-with-tooltip
                    label="数量"
                    prop=""
                    width="auto"
                    min-width="30%"
                    resizable

                    sortable
                    :sort-method="
                      (a, b) => a.relations.length - b.relations.length
                    "
                    :render-header="renderHeader"

                    :formatter="(row) => row.relations.length"
                  >
                    <!--<template slot-scope="scope">
                      {{ scope.row.relations.length }}
                    </template>-->
                  </el-table-column-with-tooltip>
                </el-table>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane name="second">
        <span slot="label" class="agent-config__tab-label">
          <base-icon name="book" :size="14" /> 行业知识
        </span>

        <div class="agent-config__knowledge no-scrollbar">
          <div
            class="agent-config__knowledge-row"
            v-for="(knowledge, index) in agentDetail.knowledgeList"
            :key="index"
          >
            <span class="agent-config__knowledge-no">{{ index + 1 }}</span>
            <el-input
              v-model="knowledge.knowledgeElement"
              placeholder="知识内容(标准术语)"
              size="small"
            ></el-input>
            <el-button
              type="text"
              class="op-icon-btn is-danger"
              @click="deleteKnowledge(index)"
              ><base-icon name="trash" :size="14"
            /></el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane name="thrid" v-if="agentItem">
        <span slot="label" class="agent-config__tab-label">
          <base-icon name="cube" :size="14" /> 推荐问
        </span>
        <div style="width: 240px; padding: 0px 4px 10px">
          <el-input
            placeholder="搜索问题..."
            v-model="queryQuestionParams.keyword"
            @keyup.enter.native="handleEnter"
            suffix-icon="el-icon-search"
          />
        </div>

        <div class="agent-config__knowledge no-scrollbar">
          <div class="drag-table">
            <el-table
              :data="questionList"
              border
              stripe
              v-loading="loading"
              element-loading-text="加载中..."
              element-loading-background="rgb(248 248 248 / 50%)"
            >
              <el-table-column-with-tooltip
                label="排序"
                prop="sortOrder"
                width="auto"
                min-width="70%"
                resizable
                sortable

                    :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                label="问题"
                prop="question"
                width="auto"
                min-width="150%"
                resizable
                sortable

                    :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                label="描述"
                prop="description"
                width="auto"
                min-width="150%"
                resizable
                sortable

                    :render-header="renderHeader"
              />

              <el-table-column-with-tooltip
                label="标签"
                prop="tags"
                width="auto"
                min-width="150%"
                resizable
                sortable
       
                
              >
                <template slot-scope="scope">
                  <span v-for="(item, index) in scope.row.tags" :key="index">
                    {{ index ? "," : "" }} {{ item.name }}
                  </span>
                </template>
              </el-table-column-with-tooltip>
              <el-table-column
                label="状态"
                width="auto"
                min-width="80%"
                resizable
                sortable

                    :render-header="renderHeader"
              >
                <template slot-scope="scope">
                  <el-switch
                    v-model="scope.row.status"
                    @change="(val) => setQuestionStatus(val, scope.row)"
                    :active-value="1"
                    :inactive-value="0"
                  >
                  </el-switch>
                </template>
              </el-table-column>

              <el-table-column
                label="操作"
                align="center"
                min-width="80px"
                :resizable="false"
              >
                <template slot-scope="scope">
                  <el-tooltip
                    content="编辑"
                    :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay"
                  >
                    <el-button
                      type="text"
                      class="op-icon-btn"
                      style="margin: 0px"
                      @click="editQuestion(scope.row)"
                    >
                      <base-icon name="edit" :size="15" />
                    </el-button>
                  </el-tooltip>

                  <el-tooltip
                    content="删除"
                    :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay"
                  >
                    <el-button
                      type="text"
                      class="op-icon-btn is-danger"
                      style="margin: 0px"
                      @click="delQuestion(scope.row)"
                    >
                      <base-icon name="trash" :size="15" />
                    </el-button>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>

            <pagination
              v-show="queryQuestionParams.total > 0"
              :total="queryQuestionParams.total"
              :page.sync="queryQuestionParams.page"
              :limit.sync="queryQuestionParams.pageSize"
              @pagination="getQuestionList"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

      <div class="horizontal-line"></div>

      <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end;padding: 0px 0px 8px 0px;gap:10px;">

      <div v-show="activeName == 'second'">
        <el-button size="small" @click="newKnowledge()">
          <base-icon name="plus" :size="14" />&nbsp;添加</el-button
        >
      </div>
      <div v-show="activeName == 'thrid'">
        <el-button size="small" @click="showQuestionTags()">
          <base-icon name="plus" :size="14" />&nbsp;编辑标签</el-button
        >
      </div>
      <div v-show="activeName == 'thrid'">
        <el-button size="small" @click="newQuestion()">
          <base-icon name="plus" :size="14" />&nbsp;添加问题</el-button
        >
      </div>
      <div style="flex: 1"></div>

      <el-button @click="close()"> 取消 </el-button>
      <el-button style="margin-left: 0px" type="primary" @click="newAIBody()"> 确定 </el-button>

      </div>
 </div>

      
    <!--<div class="agent-config__footer">
      <div v-show="activeName == 'second'">
        <el-button size="small" @click="newKnowledge()">
          <base-icon name="plus" :size="14" />&nbsp;添加</el-button
        >
      </div>
      <div v-show="activeName == 'thrid'">
        <el-button size="small" @click="showQuestionTags()">
          <base-icon name="plus" :size="14" />&nbsp;编辑标签</el-button
        >
      </div>
      <div v-show="activeName == 'thrid'">
        <el-button size="small" @click="newQuestion()">
          <base-icon name="plus" :size="14" />&nbsp;添加问题</el-button
        >
      </div>
      <div style="flex: 1"></div>
      <el-button @click="close()"> 取消 </el-button>
      <el-button type="primary" @click="newAIBody()"> 确定 </el-button>
    </div>-->

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="20vh"
      :title="questionItem.id ? '编辑推荐问' : '新建推荐问'"
      width="640px"
      :modal="false"
      :visible.sync="isQuestionShow"
    >
      <div style="padding: 10px 24px">
        <div style="display: flex; width: 100%; margin-bottom: 10px">
          <div style="width: 100px">
            问题内容<span style="color: red">*</span>
          </div>
          <div style="flex: 1 0 0">
            <el-input
              v-model="questionItem.question"
              placeholder="问题内容(最多300个字)"
              size="small"
            ></el-input>
          </div>
        </div>

        <div style="display: flex; width: 100%; margin-bottom: 10px">
          <div style="width: 100px">
            问题描述<span style="color: red">*</span>
          </div>
          <div style="flex: 1 0 0">
            <el-input
              v-model="questionItem.description"
              placeholder="问题描述(最多300个字)"
              size="small"
            ></el-input>
          </div>
        </div>

        <div style="display: flex; width: 100%; margin-bottom: 10px">
          <div style="width: 100px">排序</div>
          <div style="flex: 1 0 0">
            <el-input-number
              class="my-input-number"
              v-model="questionItem.sortOrder"
              controls-position="right"
              :min="1"
              :max="
                questionItem.id ? questionList.length : questionList.length + 1
              "
            ></el-input-number>
            <span style="font-size: 12px; color: silver">(数值越小越靠前)</span>
          </div>
        </div>

        <div style="display: flex; width: 100%">
          <div style="width: 100px" class="custom-select">
            <el-select
              v-model="questionItem.tags"
              multiple
              placeholder=""
              value-key="id"
              :popper-append-to-body="false"
            >
              <el-option
                v-for="tag in questionTagList"
                :key="tag.id"
                :label="tag.name"
                :value="tag"
              >
              </el-option>
            </el-select>
          </div>

          <div style="display: flex; flex-wrap: wrap; gap: 10px; flex: 1">
            <el-tag
              v-for="tag in questionItem.tags"
              :key="tag.id"
              closable
              type="info"
              @close="handleCloseQuestionTags(tag)"
              style="display: flex; align-items: center;background:rgb(230, 241, 255);font-size:14px;"
            >
              <truncate-tip :text="tag.name" style="max-width: 120px" />
            </el-tag>
          </div>
        </div>
      </div>

      <div class="horizontal-line"></div>

      <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end;">
          <el-button @click="isQuestionShow = false"> 取消 </el-button>
          <el-button type="primary" @click="sureQuestion()"> 确定 </el-button>
      </div>
    </div>

    </el-dialog>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="20vh"
      :title="questionTagsItem.id ? '编辑标签' : '新建标签'"
      width="640px"
      :modal="false"
      :visible.sync="isQuestionTagsName"
    >
      <div style="padding: 12px 24px; display: flex; align-items: center">
        <div style="width: 80px">标签名称<span style="color: red">*</span></div>
        <div style="flex: 1">
          <el-input
            v-model="questionTagsItem.name"
            placeholder="标签名称(不能超过20个字符)"
            size="small"
          ></el-input>
        </div>
      </div>

<div class="horizontal-line"></div>

      <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end;">
          <el-button @click="isQuestionTagsName = false"> 取消 </el-button>
          <el-button type="primary" @click="sureQuestionTags()">
            确定
          </el-button>
      </div>
    </div>
    </el-dialog>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="20vh"
      title="标签"
      width="640px"
      :modal="false"
      :visible.sync="isQuestionTags"
    >
      <div class="one-bgdiv" style="width: 240px;margin-top:10px;">
        <el-input
          placeholder="搜索标签..."
          v-model="queryTagParams.keyword"
          @keyup.enter.native="getTagList"
          suffix-icon="el-icon-search"
        />
      </div>

      <div class="agent-config__knowledge no-scrollbar">
        <div class="one-bgdiv">
          <div class="drag-table">
            <el-table :data="questionTagList" border stripe>
              <el-table-column
                label="序号"
                align="center"
                type="index"
                width="65px"
                min-width="65"
                fixed="left"
                :resizable="false"
              />
              <el-table-column-with-tooltip
                label="标签名称"
                prop="name"
                width="auto"
                min-width="300%"
                resizable
                sortable
  
              />

              <el-table-column
                label="操作"
                align="center"
                min-width="80px"
                :resizable="false"
              >
                <template slot-scope="scope">
                  <el-tooltip
                    content="编辑"
                    :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay"
                  >
                    <el-button
                      type="text"
                      class="op-icon-btn"
                      style="margin: 0px"
                      @click="editQuestionTags(scope.row)"
                    >
                      <base-icon name="edit" :size="15" />
                    </el-button>
                  </el-tooltip>

                  <el-tooltip
                    content="删除"
                    :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay"
                  >
                    <el-button
                      type="text"
                      class="op-icon-btn is-danger"
                      style="margin: 0px"
                      @click="delQuestionTags(scope.row)"
                    >
                      <base-icon name="trash" :size="15" />
                    </el-button>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>

    <div class="horizontal-line"></div>

    <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: space-between;">
          <el-button size="small" @click="newQuestionTags()">
            <base-icon name="plus" :size="14" />&nbsp;添加标签</el-button
          >
          <el-button @click="closeQuestionTags"> 关闭 </el-button>
      </div>
    </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  getAiBodyDetailAPI,
  getAiBodyCandidateTablesAPI,
  saveAiBodyAPI,
  delRelationAPI,
  getTagListAPI,
  saveTagAPI,
  delTagAPI,
  getQuestionAPI,
  saveQuestionAPI,
  delQuestionAPI,
  setQuestionStatusAPI,
} from "@/api/newBI/newBIAPI";
import { getDataSourcesListAPI } from "@/api/fieldMappingManager/fieldMappingAPI.js";
import TruncateTip from "@/components/TruncateTip";
import toast from "@/utils/toast";

export default {
  name: "newAiBodyPage",
  props: ["agentItem"],
  components: { TruncateTip },
  data() {
    return {
      activeName: "first",
      loading: false,

      queryTagParams: {
        keyword: "",
      },
      questionTagList: [],
      isQuestionTags: false,
      questionTagsItem: { id: null, name: "" },
      isQuestionTagsName: false,

      queryQuestionParams: {
        aiBodyCode: "",
        keyword: "",
        tagId: "",
        page: 1,
        pageSize: 10,
        total: 0,
      },

      questionList: [],
      isQuestionShow: false,
      questionItem: {
        id: null,
        aiBodyCode: "",
        question: "",
        description: "",
        sortOrder: "",
        status: 1,
        tags: [],
      },

      dataSourceList: [],
      candidateTables: [],

      queryParams: {
        sourceId: "",
        keyword: "",
      },

      agentDetail: {
        name: "",
        description: "",
        knowledgeList: [],
        tableRelations: [],
      },

      tableRelations: [],
    };
  },

  async mounted() {
    if (this.agentItem) {
      this.queryQuestionParams.aiBodyCode = this.agentItem.code;
      this.getTagList();
      this.getQuestionList();
    }

    await this.getDataSourceList();
    this.getAiBodyDetail();

    this.newKnowledge();
  },

  methods: {
    handleCloseQuestionTags(tag) {
      this.questionItem.tags = this.questionItem.tags.filter(
        (item) => item.id != tag.id
      );
    },

    sureQuestion() {
      if (this.agentItem) {
        if (!this.questionItem.question?.toString().trim()) {
          toast.error("问题内容不能为空");
          return;
        }
        if (this.questionItem.question.length > 200) {
          toast.error("问题内容最大300个字");
          return;
        }
        if (!this.questionItem.description?.toString().trim()) {
          toast.error("问题描述不能为空");
          return;
        }
        if (this.questionItem.description.length > 200) {
          toast.error("问题描述最大300个字");
          return;
        }
        if (this.questionItem.tags.length == 0) {
          toast.error("标签不能为空");
          return;
        }

        this.questionItem.aiBodyCode = this.agentItem.code;
        this.$set(this.questionItem, "tagIds", []);
        for (let i = 0; i < this.questionItem.tags.length; i++) {
          this.questionItem.tagIds.push(this.questionItem.tags[i].id);
        }

        saveQuestionAPI(this.questionItem)
          .then((response) => {
            if (response.code == 200) {
              this.isQuestionShow = false;
              this.getQuestionList();
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

    newQuestion() {
      this.questionItem = {
        id: null,
        aiBodyCode: "",
        question: "",
        description: "",
        sortOrder: "",
        status: 1,
        tags: [],
      };
      this.isQuestionShow = true;
    },

    editQuestion(question) {
      this.questionItem = JSON.parse(JSON.stringify(question));
      this.isQuestionShow = true;
    },

    setQuestionStatus(val, question) {
      let params = { id: question.id, status: val };
      setQuestionStatusAPI(params)
        .then((response) => {
          if (response.code == 200) {
            //this.getQuestionList();
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();

          this.getQuestionList();
        });
    },

    delQuestion(question) {
      this.$confirm("确定要删除 " + question.question + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          delQuestionAPI(question.id)
            .then((response) => {
              if (response.code == 200) {
                this.getQuestionList();
                toast.success("删除成功");
              } else {
                toast.error(response.message);
              }
            })
            .catch(() => {})
            .finally(() => {
              //loading.close();
            });
        })
        .catch(() => {});
    },

    handleEnter() {
      this.queryQuestionParams.page = 1
      this.getQuestionList();
    },

    getQuestionList() {
      this.loading = true;
      getQuestionAPI(this.queryQuestionParams)
        .then((response) => {
          if (response.code == 200) {
            this.questionList = [];
            this.questionList = response.data.list;
            this.queryQuestionParams.page = response.data.page;
            this.queryQuestionParams.pageSize = response.data.pageSize;
            this.queryQuestionParams.total = response.data.total;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
          this.loading = false;
        });
    },

    showQuestionTags() {
      this.isQuestionTags = true;
    },

    closeQuestionTags() {
      this.isQuestionTags = false;
    },

    newQuestionTags() {
      this.questionTagsItem = { id: null, name: "" };
      this.isQuestionTagsName = true;
    },

    editQuestionTags(tag) {
      this.questionTagsItem = JSON.parse(JSON.stringify(tag));
      this.isQuestionTagsName = true;
    },

    sureQuestionTags() {
      if (!this.questionTagsItem.name?.toString().trim()) {
        toast.error("标签名称不能为空");
        return;
      }
      if (this.questionTagsItem.name.length > 20) {
        toast.error("标签不能超过20个字符");
        return;
      }

      saveTagAPI(this.questionTagsItem)
        .then((response) => {
          if (response.code == 200) {
            this.isQuestionTagsName = false;
            this.getTagList();
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    delQuestionTags(tag) {
      this.$confirm("确定要删除 " + tag.name + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          delTagAPI(tag.id)
            .then((response) => {
              if (response.code == 200) {
                this.getTagList();
                toast.success("删除成功");
              } else {
                toast.error(response.message);
              }
            })
            .catch(() => {})
            .finally(() => {
              //loading.close();
            });
        })
        .catch(() => {});
    },

    getTagList() {
      getTagListAPI(this.queryTagParams)
        .then((response) => {
          if (response.code == 200) {
            this.questionTagList = [];
            this.questionTagList = response.data;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
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
    /*loadingScreen() {
      const loading = this.$loading({
        lock: true,
        text: "Loading",
        spinner: "el-icon-loading",
        background: "rgba(0, 0, 0, 0.7)",
      });

      return loading;
    },*/

    handleSelectionChange(row) {
      let index = this.tableRelations.findIndex(
        (item) => item.sourceId == this.queryParams.sourceId
      );

      if (index > -1) {
        let oldRelations = JSON.parse(JSON.stringify(this.tableRelations[index]));
        for (let i = 0; i < this.candidateTables.length; i++) {
          oldRelations.relations = oldRelations.relations.filter(
            (item) => item.relationId != this.candidateTables[i].relationId
          );
        }

        this.tableRelations[index].relations = [
          ...row,
          ...oldRelations.relations,
        ];
      } else {
        let index = this.dataSourceList.findIndex(
          (item) => item.id == this.queryParams.sourceId
        );

        let tables = {
          sourceId: this.queryParams.sourceId,
          name: this.dataSourceList[index].name,
          relations: row,
        };
        this.tableRelations.push(tables);
      }

      for (let i = 0; i < this.tableRelations.length; i++) {
        if (this.tableRelations[i].relations.length == 0) {
          this.tableRelations.splice(i, 1);
          i--;
        }
      }
    },

    deleteSelection(row) {
      for (let i = 0; i < this.tableRelations.length; i++) {
        const relations = this.tableRelations[i].relations;
        const idx = relations.findIndex(
          (item) =>
            item.relationId == row.relationId && item.sourceId == row.sourceId
        );
        if (idx > -1) {
          relations.splice(idx, 1);
          if (relations.length == 0) {
            this.tableRelations.splice(i, 1);
          }
          break;
        }
      }
      //同步左侧勾选状态
      if (
        row.sourceId == this.queryParams.sourceId &&
        this.$refs.multipleTable
      ) {
        const cIdx = this.candidateTables.findIndex(
          (item) => item.relationId == row.relationId
        );
        if (cIdx > -1) {
          this.$refs.multipleTable.toggleRowSelection(
            this.candidateTables[cIdx],
            false
          );
        }
      }
    },

    async delRelation(relationId) {
      if (this.agentItem != null) {
        let params = { code: this.agentItem.code, id: relationId };
        return delRelationAPI(params)
          .then((response) => {
            if (response.code == 200) {
            } else {
            }
          })
          .catch(() => {})
          .finally(() => {
            //loading.close();
          });
      }
    },

    newKnowledge() {
      if (!this.agentDetail.hasOwnProperty("knowledgeList")) {
        this.$set(this.agentDetail, "knowledgeList", []);
      }

      for (let i = 0; i < this.agentDetail.knowledgeList.length; i++) {
        if (this.agentDetail.knowledgeList[i].knowledgeElement == "") {
          return;
        }
      }

      this.agentDetail.knowledgeList.push({
        knowledgeElement: "",
        //knowledgeAlias: "",
      });
    },

    deleteKnowledge(index) {
      this.agentDetail.knowledgeList.splice(index, 1);
    },

    async getDataSourceList() {
      //查询数据源的参数
      //const loading = this.loadingScreen();
      return getDataSourcesListAPI()
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.dataSourceList = [];

              this.dataSourceList = response.data;
              //for (let i = 0; i < response.data.length; i++) {
              //this.dataSourceList.push(response.data[i]);
              //}
            }
          } else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    getAiBodyDetail() {
      //获取详情，把表格按sourceID进行归组
      if (this.agentItem != null) {
        //const loading = this.loadingScreen();
        getAiBodyDetailAPI(this.agentItem.code)
          .then((response) => {
            if (response.code == 200) {
              this.agentDetail = response.data;

              for (let i = 0; i < this.agentDetail.tableRelations.length; i++) {
                if (
                  this.agentDetail.tableRelations[i].hasOwnProperty("sourceId")
                ) {
                  let index = this.tableRelations.findIndex(
                    (item) =>
                      item.sourceId ==
                      this.agentDetail.tableRelations[i].sourceId
                  );

                  if (index > -1) {
                    this.tableRelations[index].relations.push(
                      this.agentDetail.tableRelations[i]
                    );
                  } else {
                    let index1 = this.dataSourceList.findIndex(
                      (item) =>
                        item.id == this.agentDetail.tableRelations[i].sourceId
                    );
                    let tables = {
                      sourceId: this.agentDetail.tableRelations[i].sourceId,
                      name: this.dataSourceList[index1].name,
                      relations: [this.agentDetail.tableRelations[i]],
                    };

                    this.tableRelations.push(tables);
                  }
                }
              }

              //添加一行
              if (!(this.agentDetail.knowledgeList?.length > 0))
                this.newKnowledge();
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

    getAiBodyCandidateTables() {
      //将已选得数据选择上
      if (!this.queryParams.sourceId) {
        toast.error("请选择数据源");
        return;
      }
      //const loading = this.loadingScreen();
      getAiBodyCandidateTablesAPI(this.queryParams)
        .then((response) => {
          if (response.code == 200) {
            /*for (let i = 0; i < response.data.length; i++) {
            let table = {};
            table.relationId = response.data[i].relationId;
            table.relationName = response.data[i].relationName;
            table.type = response.data[i].type;
            table.sourceId = this.queryParams.sourceId;
            this.candidateTables.push(table);
          }*/
            this.candidateTables = response.data;

            for (let i = 0; i < this.tableRelations.length; i++) {
              for (
                let j = 0;
                j < this.tableRelations[i].relations.length;
                j++
              ) {
                let index = this.candidateTables.findIndex(
                  (item) =>
                    item.relationId ==
                      this.tableRelations[i].relations[j].relationId &&
                    item.sourceId ==
                      this.tableRelations[i].relations[j].sourceId
                );
                if (index > -1) {
                  this.$nextTick(() => {
                    this.$refs.multipleTable.toggleRowSelection(
                      this.candidateTables[index],
                      true
                    );
                  });
                }
              }
            }

            /*for (let i = 0; i < this.agentDetail.tableRelations.length; i++) {
            let index = this.candidateTables.findIndex(
              (item) =>
                item.relationId == this.agentDetail.tableRelations[i].relationId
            );
            if (index > -1) {
              this.$nextTick(() => {
                this.$refs.multipleTable.toggleRowSelection(
                  this.candidateTables[index],
                  true
                );
              });
            }
          }*/
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    async newAIBody() {
      if (!this.agentDetail.name?.toString().trim()) {
        toast.error("智能体名称不能为空");
        return;
      }

      /*this.agentDetail.tableRelations = [];
      for (let i = 0; i < this.tableRelations.length; i++) {
        for (let j = 0; j < this.tableRelations[i].relations.length; j++) {
          this.agentDetail.tableRelations.push(
            this.tableRelations[i].relations[j]
          );
        }
      }*/
      //console.log(this.agentDetail.tableRelations)
      //console.log(this.tableRelations)

      //增加一步删除，在点了删除的，调用后台删除------后台要求
      for (let i = 0; i < this.agentDetail.tableRelations.length; i++) {
        let existTable = this.tableRelations.map((item) => ({
          ...item,
          relations: item.relations.filter(
            (relation) =>
              relation.sourceId ===
                this.agentDetail.tableRelations[i].sourceId &&
              relation.relationId ===
                this.agentDetail.tableRelations[i].relationId
          ),
        }));

        if (existTable && existTable.length > 0) {
          if (existTable[0].relations.length == 0) {
            await this.delRelation(this.agentDetail.tableRelations[i].id);
          } else {
          }
        }
      }

      this.agentDetail.tableRelations = [];
      for (let i = 0; i < this.tableRelations.length; i++) {
        for (let j = 0; j < this.tableRelations[i].relations.length; j++) {
          this.agentDetail.tableRelations.push(
            this.tableRelations[i].relations[j]
          );
        }
      }

      this.agentDetail.knowledgeList = this.agentDetail.knowledgeList.filter(
        (item) => item.knowledgeElement != ""
      );

      //const loading = this.loadingScreen();
      saveAiBodyAPI(this.agentDetail)
        .then((response) => {
          if (response.code == 200) {
            this.$emit("sure");
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    close() {
      this.$emit("close");
    },
  },
};
</script>

<style scoped lang="scss">
.agent-config {
  width: 100%;
  padding: 10px 24px 0px;
  box-sizing: border-box;
}

.agent-config__form {
  ::v-deep .el-form-item__label {
    padding-bottom: 6px;
    font-weight: 600;
    color: #334155;
  }
  ::v-deep .el-form-item {
    margin-bottom: 20px;
  }
}

.agent-config__tabs {
  ::v-deep .el-tabs__header {
    margin-bottom: 15px;
  }
}

.agent-config__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.agent-config__panes {
  display: flex;
  gap: 20px;
}

.agent-config__pane {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--border-color, #e5eaf1);
  border-radius: 12px;
  padding: 18px;
  background: linear-gradient(160deg, #fcfdff, #f9fbfe);
}

.agent-config__pane-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;

  &::before {
    content: "";
    width: 3px;
    height: 12px;
    border-radius: 2px;
    background: var(
      --accent-gradient,
      linear-gradient(180deg, #3b82f6, #06b6d4)
    );
  }
}

.agent-config__toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.agent-config__table-wrap {
  max-height: 31vh;
  overflow-y: auto;
  overflow-x: hidden;

  &--tall {
    max-height: 37vh;
  }
}

.agent-config__knowledge {
  max-height: 42vh;
  overflow-y: auto;
  //padding: 2px 4px;
}

.agent-config__knowledge-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.agent-config__knowledge-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--accent-gradient, linear-gradient(135deg, #3b82f6, #06b6d4));
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.agent-config__footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 4px;
  margin-top: 6px;
  border-top: 1px solid var(--border-color, #e5eaf1);
}

.agent-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 2px 10px;
  flex-shrink: 0;
}

.agent-sidebar__header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.agent-sidebar__header-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  color: #fff;
  background: var(--accent-gradient, linear-gradient(135deg, #3b82f6, #06b6d4));
  box-shadow: 0 2px 6px rgba(43, 92, 255, 0.25);
}

.agent-sidebar__add-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px dashed rgba(43, 92, 255, 0.45);
  border-radius: 7px;
  background: rgba(43, 92, 255, 0.06);
  color: var(--brand, #2b5cff);
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: var(--brand, #2b5cff);
    border-style: solid;
    color: #fff;
    box-shadow: 0 2px 8px rgba(43, 92, 255, 0.3);
  }
}

.agent-sidebar__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-sidebar__item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  &:hover:not(.is-active) {
    background: rgba(43, 92, 255, 0.06);
  }

  &.is-active {
    background: rgba(43, 92, 255, 0.1);

    .agent-sidebar__name {
      color: var(--brand, #2b5cff);
      font-weight: 600;
    }

    .agent-sidebar__avatar {
      color: #fff;
      background: var(
        --accent-gradient,
        linear-gradient(135deg, #3b82f6, #06b6d4)
      );
      border-color: transparent;
    }
  }
}

.agent-sidebar__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 7px;
  border: 1px solid var(--border-light, #e5eaf1);
  background: #fff;
  color: var(--brand, #2b5cff);
  transition: all 0.15s;
}

.agent-sidebar__name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--text-secondary, #475569);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
}

.agent-sidebar__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.agent-sidebar__item:hover .agent-sidebar__actions,
.agent-sidebar__item.is-active .agent-sidebar__actions {
  opacity: 1;
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

.agent-sidebar__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 8px;
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
}

.my-input-number {
  line-height: 30px;
}

::v-deep
  .my-input-number.el-input-number.is-controls-right
  .el-input-number__increase {
  border-radius: 0 10px 0 0;
  line-height: 15px;
  height: 15px;
}

::v-deep
  .my-input-number.el-input-number.is-controls-right
  .el-input-number__decrease {
  border-left: 1px solid #dcdfe6;
  border-radius: 0 0 10px;
  line-height: 15px;
}
</style>



<style scoped>
.custom-select ::v-deep .el-select-dropdown {
  max-width: 400px;
}

::v-deep .custom-select .el-select__tags {
  display: none;
}

::v-deep .custom-select .el-input::before {
  content: "+ 关联标签";
}

::v-deep .custom-select .el-input__inner {
  display: none;
}

::v-deep .custom-select .el-input__suffix {
  display: none;
}

::v-deep .custom-select .el-input {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  width: 90px;
  height: 30px;
  border: 1px dashed rgba(43, 92, 255, 0.45);
  border-radius: 7px;
  background: rgba(43, 92, 255, 0.06);
  color: var(--brand, #2b5cff);
  cursor: pointer;
  transition: all 0.15s;
}

::v-deep .el-tag .el-icon-close::before
 {
    margin-top: 2px;
}
</style>