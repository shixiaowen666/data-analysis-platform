
<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <div class="tablebg">
        <div class="searchBox">
          <div style="display: flex">
            <div style="width: 240px">
              <el-input
                placeholder="搜索组合..."
                suffix-icon="el-icon-search"
                v-model="queryParams.keyword"
                @keyup.enter.native="handleEnter"
              >
              </el-input>
            </div>

            <div style="margin-left: 10px; width: 120px">
              <el-select
                placeholder="请选择状态"
                @change="handleEnter"
                v-model="queryParams.status"
              >
                <el-option label="全部状态" value=""></el-option>
                <el-option
                  v-for="item in statusEnum"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </div>

            <div style="margin-left: 10px; text-align: right">
              <el-button @click="getPortfolioDataList"
                ><base-icon name="refresh" :size="14"
              /></el-button>
            </div>
          </div>

          <div style="width: 100%"></div>
          <!--<div>
            <el-button type="primary" class="toolbar-btn" @click="nenPortfolioDataClick"
              ><base-icon name="plus" :size="14" />&nbsp;新建指标组合</el-button
            >
          </div>-->
        </div>


          <div class="table-container">
            <div class="normal-table">
              <el-table
                :data="portfolioDataList"
                border
                v-loading="loading"
                element-loading-text="加载中..."
                element-loading-background="rgb(248 248 248 / 50%)"
              >
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
                  label="组合名称"
                  prop="groupName"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="组合编码"
                  prop="groupCode"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="组合描述"
                  prop="description"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <!--<el-table-column-with-tooltip
                  label="字段数"
                  prop="fieldCount"
                  width="auto"
                  min-width="50%"
                  resizable
                  sortable

                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="主题域"
                  prop="subjectDomain"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
      
                  :render-header="renderHeader"
                />-->
                <el-table-column-with-tooltip
                  label="状态"
                  prop="status"
                  width="auto"
                  min-width="55%"
                  resizable
                  sortable
                  :formatter="(row) => statusEnum[row.status].name"
                  :render-header="renderHeader"
                />

                <el-table-column-with-tooltip
                  label="更新时间"
                  prop="updatedAt"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />

                <el-table-column
                  label="操作"
                  align="center"
                  min-width="110px"
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
                        @click="editPortfolioDataClick(scope.row)"
                      >
                        <base-icon name="edit" :size="15" />
                      </el-button>
                    </el-tooltip>

                    <el-tooltip
                      content="下线"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="offlineCandidates(scope.row)"
                        v-if="scope.row.status == 2"
                      >
                        <base-icon name="sort-asc" :size="15" />
                      </el-button>
                    </el-tooltip>

                    <el-tooltip
                      content="上线"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="onlineCandidates(scope.row)"
                        v-if="scope.row.status == 3"
                      >
                        <base-icon name="sort-desc" :size="15" />
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
                        @click="deletePortfolioData(scope.row)"
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
                :limit.sync="queryParams.pageSize"
                @pagination="getPortfolioDataList"
              />
            </div>
          </div>
  
      </div>
    </div>

    <!--<el-drawer
      title="指标组合"
      :visible.sync="isNewPortfolio"
      direction="rtl"
      size="50%"
      :wrapperClosable="false"
      :show-close="false"
      append-to-body
      class="my-custom-style"
    >
      <span>
        <newPortfolio-Page
          v-if="isNewPortfolio"
          :portfolioDataItem="portfolioDataItem"
          @close="closePortfolioData"
          @sure="surePortfolioData"
        />
      </span>
    </el-drawer>-->
  </div>
</template>

<script>
import {
  statusEnum,
  getGroupDataListAPI,
  onlineGroupAPI,
  offlineGroupAPI,
  deleteGroupAPI,
} from "@/api/portfolioManager/portfolioAPI.js";
import newPortfolioPage from "@/views/portfolioManager/newPortfolioPage";
import toast from "@/utils/toast";
import { RouterEnum } from "@/utils/common.js";

export default {
  name: "portfolioManager",
  components: {
    newPortfolioPage,
  },

  data() {
    return {
      statusEnum,

      loading: false,

      isNewPortfolio: false,

      portfolioDataItem: null,
      portfolioDataList: [],

      queryParams: {
        keyword: "",
        status: "",
        page: 1,
        pageSize: 10,
        total: 0,
      },
    };
  },

  mounted() {
    this.getPortfolioDataList();
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

    nenPortfolioDataClick() {
      this.portfolioDataItem = null;
      this.isNewPortfolio = true;
    },

    editPortfolioDataClick(row) {
      this.$emit("openlink", RouterEnum.METRICDATAPREVIEW, {
        groupID: row.id,
        name: row.groupName,
      });
    },

    closePortfolioData() {
      this.isNewPortfolio = false;
    },

    surePortfolioData() {
      this.isNewPortfolio = false;
      this.getPortfolioDataList();
    },

    handleEnter() {
      this.queryParams.page = 1
      this.getPortfolioDataList();
    },

    getPortfolioDataList() {
      this.loading = true;
      getGroupDataListAPI(this.queryParams)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.portfolioDataList = [];

              this.queryParams.total = response.data.total;
              this.queryParams.page = response.data.page;
              this.queryParams.pageSize = response.data.pageSize;
              this.portfolioDataList = response.data.list;
            }
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false;
        });
    },

    offlineCandidates(row) {
      offlineGroupAPI(row.id)
        .then((response) => {
          if (response.code == 200) {
            toast.success("下线成功");
            this.getPortfolioDataList();
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    onlineCandidates(row) {
      onlineGroupAPI(row.id)
        .then((response) => {
          if (response.code == 200) {
            toast.success("上线成功");
            this.getPortfolioDataList();
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    //删除
    deletePortfolioData(row) {
      this.$confirm("确定要删除 " + row.groupName + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          deleteGroupAPI(row.id)
            .then((response) => {
              if (response.code == 200) {
                toast.success("删除成功");
                this.$emit("closelink", '编辑组合指标--' + row.groupName);
                this.getPortfolioDataList();
              } else {
                toast.error(response.message);
              }
            })
            .catch(() => {})
            .finally(() => {});
        })
        .catch(() => {});
    },
  },
};
</script>

 <style scoped lang="scss">
</style>