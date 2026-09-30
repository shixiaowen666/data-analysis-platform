
<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <div class="tablebg">
        <div class="searchBox">
          <div style="display: flex">
            <div style="width: 240px">
              <el-input
                placeholder="搜索模型..."
                v-model="queryParams.keyword"
                @keyup.enter.native="handleEnter"
                suffix-icon="el-icon-search"
              >
              </el-input>
            </div>

            <div style="margin-left: 10px; width: 120px">
              <el-select
                placeholder="请选择类型"
                v-model="queryParams.status"
                @change="handleEnter"
              >
                <el-option label="全部类型" value=""></el-option>
                <el-option
                  v-for="item in statusEnum"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </div>

            <div style="margin-left: 10px">
              <el-button @click="getDataModelList">
                <base-icon name="refresh" :size="14" />
              </el-button>
            </div>
          </div>

          <div style="width: 100%"></div>

          <div style="text-align: right">
            <div>
              <el-button
                type="primary"
                class="toolbar-btn"
                @click="newDataModelClick()"
                ><base-icon name="plus" :size="14" />&nbsp;新建模型</el-button
              >
            </div>
          </div>
        </div>


          <div class="table-container">
            <div class="normal-table">
              <el-table
                :data="dataModelList"
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
                  label="模型名称"
                  prop="name"
                  width="auto"
                  min-width="150%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="主表"
                  prop="factTableName"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="关联表数"
                  prop="dimTableCount"
                  width="auto"
                  min-width="90%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="数据源"
                  prop="sourceName"
                  width="auto"
                  min-width="140%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="更新时间"
                  prop="updatedAt"
                  width="auto"
                  min-width="130%"
                  resizable
                  sortable
                  :render-header="renderHeader"
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
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="editDataModelClick(scope.row)"
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
                        @click="deleteDataModel(scope.row)"
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
                @pagination="getDataModelList"
              />
            </div>
          </div>

      </div>
    </div>

    <el-dialog
      top="10vh"
      width="60%"
      :show-close="false"
      :close-on-click-modal="false"
      title="数据模型"
      :visible.sync="isNewDataModel"
      append-to-body
      class="my-custom-style"
    >
      <newDataModel-Page
        v-if="isNewDataModel"
        :dataModelItem="dataModelItem"
        @close="closeNewDataModel"
        @sure="sureNewDataModel"
      />
    </el-dialog>
  </div>
</template>


<script>
import { statusEnum } from "@/api/dataSourceManager/dataSourceAPI.js";
import {
  getDataModelListAPI,
  deleteDataModelAPI,
} from "@/api/dataModelManager/dataModelAPI.js";
import newDataModelPage from "@/views/dataModelManager/newDataModelPage";
import toast from "@/utils/toast";

export default {
  name: "dataModelManager",
  components: { newDataModelPage },
  data() {
    return {
      statusEnum,
      loading: false,

      isNewDataModel: false,
      dataModelList: [],
      dataModelItem: null,

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
    this.getDataModelList();
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

    newDataModelClick() {
      this.dataModelItem = null;
      this.isNewDataModel = true;
    },

    editDataModelClick(row) {
      this.dataModelItem = row;
      this.isNewDataModel = true;
    },

    closeNewDataModel() {
      this.isNewDataModel = false;
      //this.getDataModelList();
    },

    sureNewDataModel() {
      this.isNewDataModel = false;
      this.getDataModelList();
    },

    handleEnter() {
      this.queryParams.page = 1
      this.getDataModelList();
    },

    getDataModelList() {
      this.loading = true;
      getDataModelListAPI(this.queryParams)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.dataModelList = [];

              this.queryParams.total = response.data.total;
              this.queryParams.page = response.data.page;
              this.queryParams.pageSize = response.data.pageSize;

              for (let i = 0; i < response.data.list.length; i++) {
                this.dataModelList.push(response.data.list[i]);
              }
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

    //删除
    deleteDataModel(row) {
      this.$confirm("确定要删除 " + row.name + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          deleteDataModelAPI(row.id)
            .then((response) => {
              if (response.code == 200) {
                toast.success("删除成功");

                this.getDataModelList();
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