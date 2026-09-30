<template>
  <div>

    <div
      style="max-height: 68vh; overflow-y: auto; overflow-x: hidden;margin-top:10px;"
      class="no-scrollbar"
    >
      <div style="display: flex">
        <div class="one-bgdiv">
          <div class="warntext-bg">
            确认后将自动导入该表全部字段到字段映射表，业务类型（fact/dim）后续在字段设置中指定。
          </div>
        </div>
      </div>

      <div style="display: flex">
        <div class="one-bgdiv">数据源<span style="color: red">*</span></div>
      </div>

      <div style="display: flex">
        <div class="one-bgdiv">
          <el-select
            placeholder="请选择数据源"
            v-model="queryParams.sourceId"
            @change="getFieldMappingunRegisterTable"
            style="width: 70%"
          >
            <el-option
              v-for="item in dataSourceList"
              :key="item.id"
              :label="`${item.name}(${item.dbType})`"
              :value="item.id"
            >
            </el-option>
          </el-select>
        </div>
      </div>

      <div style="display: flex;flex-direction: column;" v-if="isTable">
        <div class="one-bgdiv" style="display:flex;">
            <el-input
              placeholder="搜索表名..."
              v-model="queryParams.keyword"
              @keyup.enter.native="handleEnter"
              style="width: 240px"
              suffix-icon="el-icon-search"
            >
            </el-input>

            <div>
              <el-button
                type="text"
                style="padding: 9px 8px"
                @click="toggleAllSelection"
                >全选</el-button
              >
            </div>
            <div>
              <el-button
                type="text"
                style="padding: 9px 8px"
                @click="clearAllSelection"
                >取消全选</el-button
              >
            </div>
            <div style="padding: 9px 8px; line-height: 1">
              已选 {{ multipleCollectRemoteTables.length }} 张表
            </div>
          </div>

          <div class="one-bgdiv">
            <div class="drag-table">
              <el-table
                :reserve-selection="true"
                ref="multipleTable"
                :data="collectRemoteTables"
                @selection-change="handleSelectionChange"
                border
              >
                <el-table-column
                  label="序号"
                  type="selection"
                  width="55"
                  :selectable="selectableMethod"
                />

                <el-table-column-with-tooltip
                  prop="tableName"
                  label="表名"
                  width="auto"
                  min-width="35%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  prop="tableComment"
                  label="注释"
                  width="auto"
                  min-width="40%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  prop="ifRegister"
                  label="是否已注册"
                  width="auto"
                  min-width="25%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
              </el-table>
            </div>

        </div>
      </div>
    </div>

    <div class="horizontal-line"></div>

    <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" @click="surePhysical">确认注册</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  getFieldMappingunTableBySourceIDAPI,
  newRegisterPhysicalAPI,
  getDataSourcesListAPI,
} from "@/api/fieldMappingManager/fieldMappingAPI.js";
import toast from "@/utils/toast";

export default {
  name: "physicalRegisteredPage",
  components: {},
  data() {
    return {
      isTable: false,

      queryParams: {
        sourceId: "",
        keyword: "",
      },

      dataSourceList: [],
      collectRemoteTables: [],
      multipleCollectRemoteTables: [],
    };
  },

  mounted() {
    this.getDataSourceList();
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

    handleEnter() {
      this.getFieldMappingunRegisterTable();
    },

    selectableMethod(row) {
      if (row.ifRegister === "已注册") return false;
      return true;
    },

    handleSelectionChange(val) {
      this.multipleCollectRemoteTables = [];
      for (let i = 0; i < val.length; i++) {
        this.multipleCollectRemoteTables.push(val[i]);
      }
    },

    // 切换全选状态
    toggleAllSelection() {
      // 通过 $refs 获取表格实例，并调用 toggleAllSelection 方法
      this.$refs.multipleTable.toggleAllSelection();
    },

    // 清空所有选中
    clearAllSelection() {
      this.$refs.multipleTable.clearSelection();
    },

    //获取数据源列表
    getDataSourceList() {
      getDataSourcesListAPI()
        .then((response) => {
          if (response.code == 200) {
            this.dataSourceList = [];

            this.dataSourceList = response.data;
          } else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    //获取数据源列表
    getFieldMappingunRegisterTable() {
      this.collectRemoteTables = [];

      this.isTable = false;
      if (this.queryParams.sourceId != "") {
        this.isTable = true;
        getFieldMappingunTableBySourceIDAPI(this.queryParams)
          .then((response) => {
            if (response.code == 200) {
              this.collectRemoteTables = response.data;
            } else {
              //访问失败
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
      }
    },

    surePhysical() {
      let data = { sourceId: this.queryParams.sourceId, tableIds: [] };
      for (let i = 0; i < this.multipleCollectRemoteTables.length; i++) {
        data.tableIds.push(this.multipleCollectRemoteTables[i].tableId);
      }

      if (!data.sourceId) {
        toast.error("数据源不能为空");
        return;
      }
      if (!data.tableIds?.length) {
        toast.error("数据表不能为空");
        return;
      }

      newRegisterPhysicalAPI(data)
        .then((response) => {
          if (response.code == 200) {
            toast.success("添加成功");

            this.$emit("sure");
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    //关闭窗口
    close() {
      this.$emit("close");
    },
  },
};
</script>

 <style scoped lang="scss">
</style>