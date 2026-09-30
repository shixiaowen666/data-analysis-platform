
<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <div class="tablebg">
        <div class="searchBox">
          <div
            style="display: flex; justify-content: space-between; width: 100%;gap: 10px;flex-wrap: wrap;"
          >
            <div style="display: flex; align-items: center; gap: 10px;">
              <div
                style="
                  background: #cff3c1;
                  padding: 5px 10px;
                  border-radius: 999px;
                  min-width:130px;
                  text-align: center;
                "
              >
                当前版本: {{ currentVersion }}
              </div>

              <div style="min-width: 200px;width:25%;">
                <el-select
                  placeholder="请选择分组"
                  v-model="queryParams.group_id"
                  @change="changeGroup"
                  filterable
                >
                  <el-option
                    v-for="item in pormptGroupList"
                    :key="item.id"
                    :label="item.label"
                    :value="item.id"
                  >
                  </el-option>
                </el-select>
              </div>

              <div style="min-width: 200px;width:25%;">
                <el-input
                  v-model="queryParams.keyword"
                  placeholder="搜索关键词..."
                  @keyup.enter.native="handleEnter"
                  suffix-icon="el-icon-search"
                />
              </div>

              <div>
                <el-button @click="getPromptList"
                  ><base-icon name="refresh" :size="14"
                /></el-button>
              </div>
            </div>

            <div style="text-align: right">
              <div>
                <el-button
                  type="primary"
                  class="toolbar-btn"
                  @click="newPrompt()"
                  :disabled="queryParams.group_id == ''"
                  ><base-icon
                    name="plus"
                    :size="14"
                  />&nbsp;新建提示词</el-button
                >
              </div>
            </div>
          </div>
        </div>


          <div class="table-container">
            <div class="normal-table">
              <el-table
                :data="pormptList"
                border
                v-loading="loading"
                element-loading-text="加载中..."
                element-loading-background="rgb(248 248 248 / 50%)"
              >
                <el-table-column-with-tooltip
                  prop="id"
                  label="版本ID"
                  align="center"
                  width="100"
                  min-width="100"
                  fixed="left"
                  :resizable="false"
                />

                <el-table-column-with-tooltip
                  prop="version"
                  label="版本号"
                  width="auto"
                  min-width="80%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />

                <el-table-column
                  prop="is_active"
                  label="当前版本"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                >
                  <template slot-scope="scope">
                    <el-switch
                      v-model="scope.row.is_active"
                      @change="(val) => switchPrompt(val, scope.row)"
                      :active-value="true"
                      :inactive-value="false"
                    >
                    </el-switch>
                  </template>
                </el-table-column>
                <el-table-column-with-tooltip
                  prop="description"
                  label="描述"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  prop="created_at"
                  label="创建时间"
                  width="auto"
                  min-width="130%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  prop="updated_at"
                  label="更新时间"
                  width="auto"
                  min-width="130%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column
                  label="操作"
                  align="center"
                  min-width="100px"
                  :resizable="false"
                >
                  <template slot-scope="scope">
                    <el-tooltip
                      content="编辑"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="editPrompt(scope.row)"
                      >
                        <base-icon name="edit" :size="15" />
                      </el-button>
                    </el-tooltip>

                    <el-tooltip
                      content="删除"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn is-danger"
                        style="margin: 0px"
                        @click="deletePrompt(scope.row)"
                      >
                        <base-icon name="trash" :size="15" />
                      </el-button>
                    </el-tooltip>
                  </template>
                </el-table-column>
              </el-table>

              <pagination
                v-show="queryParams.total > 0"
                :total="queryParams.total"
                :page.sync="queryParams.page"
                :limit.sync="queryParams.page_size"
                @pagination="getPromptList"
              />
            </div>
          </div>

      </div>
    </div>

    <el-dialog
      append-to-body
      class="my-custom-style"
      :show-close="false"
      :close-on-click-modal="false"
      top="10vh"
      width="80%"
      title="提示词"
      :visible.sync="isShowPrompt"
    >
      <newPrompt-Page
        v-if="isShowPrompt"
        :promptItem="promptItem"
        :groupID="queryParams.group_id"
        @close="closeNewPrompt"
        @sure="sureNewPrompt"
      />
    </el-dialog>
  </div>
</template>

<script>
import {
  getPromptGroupAPI,
  getPromptListAPI,
  deletePromptAPI,
  switchPromptAPI,
  getCurrentPromptVersionAPI,
} from "@/api/promptManager/promptAPI.js";

import newPromptPage from "@/views/promptManager/newPromptPage";
import toast from "@/utils/toast";

export default {
  name: "aiLogManager",
  components: {
    newPromptPage,
  },

  data() {
    return {
      loading: false,

      isShowPrompt: false,

      pormptGroupList: [],
      pormptList: [],

      queryParams: {
        group_id: "",
        keyword: "",
        is_active: null,
        page: 1,
        page_size: 10,
        total: 0,
      },

      promptItem: null,
      currentVersion: "",
    };
  },

  async mounted() {
    await this.getPromptGroup();
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

    changeGroup() {
      this.queryParams.page = 1;
      this.handleEnter();
    },

    //搜索条件变化
    handleEnter() {
      this.queryParams.page = 1
      this.getPromptList();
    },

    close() {
      this.isShowPrompt = false;
    },

    newPrompt() {
      this.promptItem = null;
      this.isShowPrompt = true;
    },

    editPrompt(row) {
      this.promptItem = row;
      this.isShowPrompt = true;
    },

    closeNewPrompt() {
      this.isShowPrompt = false;
    },

    sureNewPrompt() {
      this.getPromptList();
      this.isShowPrompt = false;
    },

    /*getCurrentPromptVersion() {
      //const queryParams = {group_id:'', keyword: ''}
      if(this.queryParams.group_id == ''){
        return
      }
      //this.currentVersion = ''
      let params = {group_id:this.queryParams.group_id}
      getCurrentPromptVersionAPI(params)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.currentVersion = response.data.version;
            }
          } else {
            //访问失败
            toast.error(response.message)
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },*/

    switchPrompt(val, row) {
      if (!val) {
        this.getPromptList();
        return;
      }

      let param = { group_id: row.group_id, version: row.version };
      switchPromptAPI(param)
        .then((response) => {
          if (response.code == 200) {
            toast.success("切换成功");
            //this.getCurrentPromptVersion()
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
          this.getPromptList();
        });
    },

    deletePrompt(row) {
      this.$confirm("确定要删除 " + row.version + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          let param = { group_id: row.group_id, version: row.version };
          deletePromptAPI(param)
            .then((response) => {
              if (response.code == 200) {
                toast.success("删除成功");
                this.getPromptList();
              } else {
                toast.error(response.message);
              }
            })
            .catch(() => {})
            .finally(() => {});
        })
        .catch(() => {});
    },

    getPromptList() {
      if (this.queryParams.group_id == "") {
        return;
      }
      this.loading = true;
      getPromptListAPI(this.queryParams)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.pormptList = [];

              this.queryParams.total = response.data.total;
              this.queryParams.page = response.data.page;
              this.queryParams.page_size = response.data.page_size;

              this.pormptList = response.data.list;
              this.currentVersion = response.data.current_version;
            }
          } else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false;
        });
    },

    //获取数据源列表
    async getPromptGroup() {
      const queryParams = { keyword: "" };
      return getPromptGroupAPI(queryParams)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.pormptGroupList = [];
              this.pormptGroupList = response.data.list;

              if (this.pormptGroupList.length > 0) {
                this.queryParams.group_id = this.pormptGroupList[0].id;
                this.handleEnter();
              }
            }
          } else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },
  },
};
</script>

 <style scoped lang="scss">
.agent-sidebar__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  //opacity: 0;
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
</style>