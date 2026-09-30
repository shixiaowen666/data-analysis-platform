
<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <div class="tablebg">
        <div
          class="searchBox"
          style="
            display: flex;
            flex-wrap: wrap;
            justify-content: space-between;
            width: 100%;
            gap: 10px;
          "
        >
          <div style="display: flex; gap: 10px">
            <div style="width: 200px">
              <el-select
                @change="handleEnter"
                placeholder="请选择数据源"
                v-model="queryParams.sourceId"
              >
                <el-option label="全部数据源" value=""> </el-option>
                <el-option
                  v-for="item in dataSourceList"
                  :key="item.id"
                  :label="`${item.name}(${item.dbType})`"
                  :value="item.id"
                >
                </el-option>
              </el-select>
            </div>
            <div style="width: 200px">
              <el-input
                placeholder="搜索表名..."
                v-model="queryParams.keyword"
                suffix-icon="el-icon-search"
                @keyup.enter.native="handleEnter"
              >
              </el-input>
            </div>

            <div style="width: 150px">
              <el-select
                @change="handleEnter"
                placeholder="请选择业务类型"
                v-model="queryParams.typeKey"
              >
                <el-option label="全部业务类型" value=""></el-option>
                <el-option
                  v-for="item in typeBusinessEnum"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </div>

            <!--<div style="width: 100px">
              <el-select
                @change="getFieldMappingList"
                placeholder="请选择状态"
                v-model="queryParams.status"
              >
                <el-option label="全部状态" value=""></el-option>

                <el-option
                  v-for="item in statusEnum"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                ></el-option>
              </el-select>
            </div>-->

            <div style="text-align: right">
              <el-button @click="getFieldMappingList"
                ><base-icon name="refresh" :size="14"
              /></el-button>
            </div>
          </div>

          <div style="display: flex; gap: 10px">
            <div>
              <el-button
                type="primary"
                class="toolbar-btn"
                @click="openPhysicalPageClick"
                ><base-icon name="plus" :size="14" />&nbsp;注册物理表</el-button
              >
            </div>
            <div>
              <el-button class="toolbar-btn" @click="openViewPageClick"
                ><base-icon name="plus" :size="14" />&nbsp;注册视图</el-button
              >
            </div>
          </div>
        </div>


          <div class="table-container">
            <div class="normal-table">
              <el-table
                :data="fieldMappingList"
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
                  label="表名"
                  prop="tbName"
                  width="auto"
                  min-width="130%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="中文名"
                  prop="cnName"
                  width="auto"
                  min-width="130%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="数据源"
                  prop="sourceName"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="存储类型"
                  prop="tbTypeName"
                  width="auto"
                  min-width="80%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="业务类型"
                  prop="typeKey"
                  width="auto"
                  min-width="80%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <!--<el-table-column-with-tooltip
                  label="状态"
                  width="auto"
                  prop="statusName"
                  min-width="50%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />-->

                <el-table-column-with-tooltip
                  label="字段映射进度"
                  prop="mappedCount"
                  width="auto"
                  min-width="100%"
                  resizable
                  sortable
                  :formatter="
                    (row) =>
                      row.mappedCount > 0
                        ? `已配置 ${row.mappedCount} / 待配置 ${
                            row.totalCount - row.mappedCount
                          }`
                        : `待配置 ${row.totalCount - row.mappedCount}`
                  "
                  :render-header="renderHeader"
                >
                  <!--<template slot-scope="scope">
                    <div style="display: flex">
                      <div v-if="scope.row.mappedCount > 0">
                        已配置&nbsp;{{ scope.row.mappedCount }} /
                      </div>
                      <div>
                        &nbsp;待配置&nbsp;{{
                          scope.row.totalCount - scope.row.mappedCount
                        }}
                      </div>
                    </div>
                  </template>-->
                </el-table-column-with-tooltip>

                <el-table-column
                  label="操作"
                  align="center"
                  min-width="80px"
                  :resizable="false"
                >
                  <template slot-scope="scope">
                    <el-tooltip
                      content="字段设置"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="openSemanticPageClick(scope.row)"
                      >
                        <base-icon name="settings" :size="15" />
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
                        @click="deleteFieldMappingsTables(scope.row)"
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
                @pagination="getFieldMappingList"
              />
            </div>
          </div>

      </div>
    </div>

    <el-drawer
      title="字段设置"
      :visible.sync="isSemanticPage"
      direction="rtl"
      size="50%"
      :wrapperClosable="false"
      :show-close="false"
      append-to-body
      class="my-custom-style"
    >
      <span>
        <semantic-Registration
          :semanticItem="semanticItem"
          @close="closeSemanticPage"
          v-if="isSemanticPage"
          @sure="sureSemanticPage"
        />
      </span>
    </el-drawer>

    <el-dialog
      :close-on-click-modal="false"
      top="10vh"
      title="注册物理表"
      :visible.sync="isPhysicalPage"
      :show-close="false"
      width="60%"
      append-to-body
      class="my-custom-style"
    >
      <physicalRegistered-Page
        @close="closePhysicalPage"
        v-if="isPhysicalPage"
        @sure="surePhysicalPage"
      />
    </el-dialog>

    <el-dialog
      :close-on-click-modal="false"
      top="5vh"
      title="注册视图"
      :visible.sync="isViewPage"
      :show-close="false"
      width="60%"
      append-to-body
      class="my-custom-style"
    >
      <viewRegistered-Page
        @close="closeViewPage"
        v-if="isViewPage"
        @sure="sureViewPage"
      />
    </el-dialog>
  </div>
</template>


<script>
import semanticRegistration from "@/views/fieldMappingManager/semanticRegistration";
import physicalRegisteredPage from "@/views/fieldMappingManager/physicalRegisteredPage";
import viewRegisteredPage from "@/views/fieldMappingManager/viewRegisteredPage";

import {
  getDataSourcesListAPI,
  getFieldMappingListAPI,
  deleteFieldMappingsTablesAPI,
  typeBusinessEnum,
} from "@/api/fieldMappingManager/fieldMappingAPI.js";
import toast from "@/utils/toast";

export default {
  name: "fieldMappingManager",
  components: {
    semanticRegistration,
    physicalRegisteredPage,
    viewRegisteredPage,
  },
  data() {
    return {
      typeBusinessEnum,

      loading: false,

      isPhysicalPage: false,
      isViewPage: false,
      isSemanticPage: false,

      semanticItem: null,

      fieldMappingList: [],

      queryParams: {
        sourceId: null,
        keyword: "",
        tbTypeKey: "",
        typeKey: "",
        status: "",

        total: 0,
        page: 1,
        pageSize: 10,
      },

      dataSourceList: [],
    };
  },

  mounted() {
    this.getDataSourceList();
    this.getFieldMappingList();
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

    openPhysicalPageClick() {
      this.isPhysicalPage = true;
    },

    closePhysicalPage() {
      this.isPhysicalPage = false;
    },

    surePhysicalPage() {
      this.isPhysicalPage = false;
      this.getFieldMappingList();
    },

    openViewPageClick() {
      this.isViewPage = true;
    },

    closeViewPage() {
      this.isViewPage = false;
    },

    sureViewPage() {
      this.isViewPage = false;
      this.getFieldMappingList();
    },

    openSemanticPageClick(row) {
      this.semanticItem = row;
      this.isSemanticPage = true;
    },

    closeSemanticPage() {
      this.isSemanticPage = false;
    },

    sureSemanticPage() {
      this.isSemanticPage = false;
      this.getFieldMappingList();
    },

    handleEnter(){
      this.queryParams.page = 1
      this.getFieldMappingList()
    },

    getFieldMappingList() {
      this.loading = true;
      getFieldMappingListAPI(this.queryParams)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.fieldMappingList = [];

              this.queryParams.total = response.data.total;
              this.queryParams.page = response.data.page;
              this.queryParams.pageSize = response.data.pageSize;

              for (let i = 0; i < response.data.list.length; i++) {
                this.fieldMappingList.push(response.data.list[i]);
              }
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

    deleteFieldMappingsTables(row) {
      this.$confirm("确定要删除 " + row.cnName + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          deleteFieldMappingsTablesAPI(row.id)
            .then((response) => {
              if (response.code == 200) {
                toast.success("删除成功");
                this.getFieldMappingList();
              } else {
                toast.error(response.message);
              }
            })
            .catch(() => {})
            .finally(() => {});
        })
        .catch(() => {});
    },

    //获取数据源列表
    getDataSourceList() {
      getDataSourcesListAPI()
        .then((response) => {
          if (response.code == 200) {
            this.dataSourceList = response.data;
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
</style>