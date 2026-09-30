<template>
  <div class="view-reg">
    <div
      style="
        max-height: 70vh;
        overflow-y: auto;
        overflow-x: hidden;
        padding: 5px 24px 0;
      "
      class="no-scrollbar"
    >
      <el-form label-position="top" size="small" class="view-reg__form">
        <el-form-item required>
          <template slot="label">数据源</template>
          <el-select
            placeholder="请选择数据源"
            v-model="viewData.sourceId"
            style="width: 100%; max-width: 480px"
          >
            <el-option
              v-for="item in dataSourceList"
              :key="item.id"
              :label="`${item.name}(${item.dbType})`"
              :value="item.id"
            >
            </el-option>
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item required>
              <template slot="label"
                >视图英文名
                <span class="view-reg__hint">(固定 view_ 前缀)</span></template
              >
              <el-input
                v-model="viewData.viewEnName"
                placeholder="view_"
              ></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="视图中文名" required>
              <el-input
                v-model="viewData.viewCnName"
                placeholder="如:患者汇总视图"
              ></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="视图SQL" required>
          <sql-editor v-model="viewData.sql" title="视图 SQL" :rows="5" />
          <div class="view-reg__sql-actions">
            <el-button
              type="primary"
              class="toolbar-btn"
              size="small"
              @click="sureParseTableSql"
              ><base-icon name="play" :size="14" />&nbsp;执行并解析</el-button
            >
            <span class="view-reg__parse-text">{{ parseText }}</span>
          </div>
        </el-form-item>
      </el-form>

      <template v-if="parseData.length > 0">
        <div class="view-reg__section-title">解析字段列表</div>

        <div class="drag-table">
          <el-table
            empty-text='请先编写SQL并点击"执行并解析"'
            :data="parseData"
            border
            v-loading="loading"
            element-loading-text="加载中..."
            element-loading-background="rgb(248 248 248 / 50%)"
            @header-dragend="onDragEnd"
            ref="tableRef"
          >
            <el-table-column-with-tooltip
              label="字段名"
              prop="fieldKey"
              width="auto"
              min-width="40%"
              resizable
              sortable
              :render-header="renderHeader"
              :formatter="
                (row) => {
                  if (!row) return '';

                  let tagHtml = '';
                  if (row.compareStatus == compareStatusEnum['0'].value) {
                    tagHtml = `<span class='view-reg__tag view-reg__tag--keep'>${compareStatusEnum['0'].name}</span>`;
                  } else if (
                    row.compareStatus == compareStatusEnum['1'].value
                  ) {
                    tagHtml = `<span class='view-reg__tag view-reg__tag--new'>${compareStatusEnum['1'].name}</span>`;
                  } else if (
                    row.compareStatus == compareStatusEnum['2'].value
                  ) {
                    tagHtml = `<span class='view-reg__tag view-reg__tag--drop'>${compareStatusEnum['2'].name}</span>`;
                  }
                  return `${tagHtml} ${row.fieldKey}`;
                }
              "
            >
              <template slot-scope="scope">
                <div style="display: flex; align-items: center; gap: 6px">
                  <span
                    v-if="
                      scope.row.compareStatus == compareStatusEnum['0'].value
                    "
                    class="view-reg__tag view-reg__tag--keep"
                    >{{ compareStatusEnum["0"].name }}</span
                  >
                  <span
                    v-if="
                      scope.row.compareStatus == compareStatusEnum['1'].value
                    "
                    class="view-reg__tag view-reg__tag--new"
                    >{{ compareStatusEnum["1"].name }}</span
                  >
                  <span
                    v-if="
                      scope.row.compareStatus == compareStatusEnum['2'].value
                    "
                    class="view-reg__tag view-reg__tag--drop"
                    >{{ compareStatusEnum["2"].name }}</span
                  >
                  <span>{{ scope.row.fieldKey }}</span>
                </div>
              </template>
            </el-table-column-with-tooltip>

            <el-table-column-with-tooltip
              label="字段类型"
              prop="fieldType"
              width="auto"
              min-width="20%"
              resizable
              sortable
              :render-header="renderHeader"
            />
            <el-table-column
              label="字段中文名"
              prop="fieldName"
              width="auto"
              min-width="40%"
              sortable
              resizable
              :render-header="renderHeader"
            >
              <template slot-scope="scope">
                <el-input
                  v-model="scope.row.fieldName"
                  :disabled="
                    scope.row.compareStatus == compareStatusEnum['2'].value
                  "
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </div>
    <div class="horizontal-line"></div>

    <div class="view-reg__footer one-bgdiv">
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="sureView">保存并注册</el-button>
    </div>
  </div>
</template>

<script>
import {
  newRegisterViewAPI,
  parseTableSqlAPI,
  getDataSourcesListAPI,
  getViewDetalAPI,
  compareStatusEnum,
} from "@/api/fieldMappingManager/fieldMappingAPI.js";
import SqlEditor from "@/components/SqlEditor";
import toast from "@/utils/toast";

export default {
  name: "viewRegisteredPage",
  props: ["viewID"],
  components: { SqlEditor },
  data() {
    return {
      compareStatusEnum,

      loading: false,

      parseText: "",
      viewData: {
        id: null,
        sourceId: "",
        viewEnName: "",
        viewCnName: "",
        sql: "",
      },
      parseData: {},
      dataSourceList: [],
    };
  },

  mounted() {
    this.getDataSourceList();
    this.getViewDetal();
  },

  methods: {
    onDragEnd(newWidth, oldWidth, column) {
      if (column.property == "fieldName") {
        const minWidth = 120;
        if (newWidth < minWidth) {
          // 修正列宽
          column.width = minWidth;
          this.$nextTick(() => {
            this.$refs.tableRef.doLayout();
          });
        }
      }
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

    sureView() {
      this.viewData.columns = [];
      for (let i = 0; i < this.parseData.length; i++) {
        let data = {
          fieldKey: this.parseData[i].fieldKey,
          fieldName: this.parseData[i].fieldName,
          fieldType: this.parseData[i].fieldType,
        };
        if (this.parseData[i].hasOwnProperty("compareStatus")) {
          data.compareStatus = this.parseData[i].compareStatus;
        }
        this.viewData.columns.push(data);
      }

      if (!this.viewData.sourceId) {
        toast.error("数据源不能为空");
        return;
      }
      if (!this.viewData.viewEnName?.toString().trim()) {
        toast.error("视图英文名不能为空");
        return;
      }

      if (!this.viewData.viewEnName?.toString().toLowerCase().trim().startsWith("view_")) {
        toast.error("视图英文名要以view_开头");
        return;
      }

      if (!this.viewData.viewCnName?.toString().trim()) {
        toast.error("视图中文名不能为空");
        return;
      }

      if (!this.viewData.sql?.toString().trim()) {
        toast.error("sql语句不能为空");
        return;
      }
      
      if (!this.viewData.columns?.length) {
        toast.error("数据表不能为空");
        return;
      }

      newRegisterViewAPI(this.viewData)
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

    sureParseTableSql() {
      if (this.viewData.id != null && this.viewData.id != "") {
        this.$confirm(
          `<div style="padding:10px;">本次重新解析会自动匹配新旧输出字段：<br/>
        · <span style="color:rgb(0 124 0);">【保留】</span>：该字段标识（fieldKey）与修改前一致，系统保留已填写的指标 / 维度映射关系；<br/>
        · <span style="color:rgb(52 98 228);">【新增】</span>：本次 SQL 解析新增输出字段，后续需要手动补充中文名和映射配置；<br/>
        · <span style="color:rgb(255 8 8);">【废弃】</span>：本次SQL 解析后已不输出的字段，页面标记为废弃，保存后该字段会从该视图移除，其指标维度映射关系也会同步移除。<br/><br/>
        确认要执行解析吗？</div>`,
          "字段结构变更确认",
          {
            customClass: "custom-confirm",
            dangerouslyUseHTMLString: true,
            confirmButtonText: "确认解析",
            cancelButtonText: "取消",
          }
        )
          .then(() => {
            this.parseTableSql();
          })
          .catch(() => {});
      } else {
        this.parseTableSql();
      }
    },

    //执行解析SQL
    parseTableSql() {
      this.parseText = "";

      let data = { sourceId: this.viewData.sourceId, sql: this.viewData.sql };
      if (this.viewData.id != null && this.viewData.id != "") {
        data.tableId = this.viewData.id;
      }

      if (!data.sourceId) {
        toast.error("数据源不能为空");
        return;
      }
      if (!data.sql?.toString().trim()) {
        toast.error("sql语句不能为空");
        return;
      }

      this.loading = true;
      parseTableSqlAPI(data)
        .then((response) => {
          if (response.code == 200) {
            this.parseData = response.data;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false;
        });
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

    getViewDetal() {
      if (this.viewID != null) {
        let query = { id: this.viewID };
        getViewDetalAPI(query)
          .then((response) => {
            if (response.code == 200) {
              this.viewData = response.data;
              this.parseData = [];
              for (let i = 0; i < response.data.columns.length; i++) {
                this.parseData.push({
                  fieldKey: response.data.columns[i].fieldKey,
                  fieldName: response.data.columns[i].fieldName,
                  fieldType: response.data.columns[i].fieldType,
                });
              }
              this.viewData.id = this.viewID;
            } else {
              //访问失败
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
      }
    },
  },
};
</script>

 <style scoped lang="scss">
.view-reg__form {
  ::v-deep .el-form-item__label {
    padding-bottom: 6px;
    font-weight: 600;
    color: #334155;
  }
  ::v-deep .el-form-item {
    margin-bottom: 22px;
  }
}

.view-reg__hint {
  font-weight: 400;
  color: #94a3b8;
  font-size: 12px;
}

.view-reg__sql-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
}

.view-reg__parse-text {
  font-size: 12.5px;
  color: #16a34a;
}

.view-reg__section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;

  &::before {
    content: "";
    width: 4px;
    height: 14px;
    border-radius: 2px;
    background: var(
      --accent-gradient,
      linear-gradient(180deg, #3b82f6, #06b6d4)
    );
  }
}

.view-reg__tag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;

  &--keep {
    background: #dcfce7;
    color: #15803d;
  }
  &--new {
    background: #dbeafe;
    color: #2563eb;
  }
  &--drop {
    background: #fee2e2;
    color: #dc2626;
  }
}

.view-reg__footer {
  display: flex;
  justify-content: flex-end;
  //gap: 10px;
  //padding: 16px 28px 18px;
  //border-top: 1px solid var(--border-color, #e5eaf1);
  //margin-top: 8px;
}
</style>
