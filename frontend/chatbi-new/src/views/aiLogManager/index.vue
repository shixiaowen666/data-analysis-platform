
<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <div class="tablebg">
        <div class="searchBox">
          <div style="display: flex; gap: 10px;width:100%;">
            <div style="min-width: 100px;">
              <el-input
                v-model="queryParams.question"
                placeholder="搜索问题..."
                @keyup.enter.native="handleEnter"
                suffix-icon="el-icon-search"
              />
            </div>

            <div style="min-width: 100px;">
              <el-input
                v-model="queryParams.user_id"
                placeholder="搜索用户ID..."
                @keyup.enter.native="handleEnter"
                suffix-icon="el-icon-search"
              />
            </div>
            <div style="min-width: 100px;">
              <el-input
                v-model="queryParams.username"
                placeholder="搜索用户名称..."
                @keyup.enter.native="handleEnter"
                suffix-icon="el-icon-search"
              />
            </div>
            <div style="display: flex; align-items: center; gap: 5px">
              <span style="text-wrap: nowrap;">时间范围: </span>

              <el-date-picker
                v-model="aiLogRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                :value-format="'yyyy-MM-dd'"
                style="width: 250px"
                :picker-options="pickerOptions"
                @change="handleDateChange"
              >
                <template slot="suffix">
                  <i class="el-input__icon el-icon-date"></i>
                </template>
              </el-date-picker>
            </div>
            <div style="margin-left: 10px">
              <el-button @click="getAILogList"
                ><base-icon name="refresh" :size="14"
              /></el-button>
            </div>
          </div>
        </div>

          <div class="table-container">
            <div style="display: flex; padding: 0px 0px 5px; align-items: center">

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

            <div style="padding: 9px 8px" :style="(allMultipleTables.length + multipleTables.length)> 100 ? 'color:red':''">
              已选
              {{
                allMultipleTables.length +
                multipleTables.length
              }}
              条
            </div>

            <div>
              <el-button
                type="text"
                style="padding: 9px 8px"
                @click="downloadLogList"
                :disabled="allMultipleTables.length + multipleTables.length == 0"
                >
                <base-icon name="download" :size="16" style="margin-top:-3px;" />
                批量下载</el-button
              >
            </div>

          </div>

            <div class="normal-table">
              <el-table
                :data="aiLogList"
                border
                v-loading="loading"
                element-loading-text="加载中..."
                element-loading-background="rgb(248 248 248 / 50%)"

                :reserve-selection="true"
                ref="multipleTable"
                @selection-change="handleSelectionChange"
              >
              <el-table-column label="序号" type="selection" width="55" />
                <!--<el-table-column
                  prop="id"
                  label="ID"
                  align="center"
                  width="65"
                  min-width="65"
                  fixed="left"
                  :resizable="false"
                />-->

                <el-table-column-with-tooltip
                  prop="user_id"
                  label="用户ID"
                  width="auto"
                  min-width="80%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                
                <el-table-column-with-tooltip
                  prop="username"
                  label="用户名"
                  width="auto"
                  min-width="80%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  prop="question"
                  label="问题"
                  width="auto"
                  min-width="150%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />

                <el-table-column-with-tooltip
                  prop="log_date"
                  label="日志日期"
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
                  min-width="80px"
                  :resizable="false"
                >
                  <template slot-scope="scope">
                    <el-tooltip
                      content="预览"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="logPreview(scope.row)"
                      >
                        <base-icon name="eye" :size="15" />
                      </el-button>
                    </el-tooltip>

                    <el-tooltip
                      content="下载"
                      :placement="$toolTipPlacement"
                      :effect="$toolTipEffect"
                      :open-delay="$toolTipOpenDelay"
                    >
                      <el-button
                        type="text"
                        class="op-icon-btn"
                        style="margin: 0px"
                        @click="downloadLog(scope.row)"
                      >
                        <base-icon name="download" :size="15" />
                      </el-button>
                    </el-tooltip>

                    <!--<el-tooltip content="删除" :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay">
                      <el-button
                        type="text"
                        class="op-icon-btn is-danger"
                        style="margin: 0px"
                        @click="deleteAILogList(scope.row)"
                      >
                        <base-icon name="trash" :size="15" />
                      </el-button>
                    </el-tooltip>-->

                  </template>
                </el-table-column>
              </el-table>

              <pagination
                v-show="queryParams.total > 0"
                :total="queryParams.total"
                :page.sync="queryParams.page"
                :limit.sync="queryParams.page_size"
                @pagination="pageSave"
              />
            </div>
        </div>
        
      </div>
    </div>


    <el-dialog
      width="60%"
      :show-close="false"
      :close-on-click-modal="false"
      :title="`查看日志-${title}`"
      :visible.sync="isLogPreview"
      append-to-body
      class="my-custom-style"
      height="550px"
    >
    <div style="padding:10px 24px;">
      <WangEditor
        ref="editor"
        v-model="content"
        :readOnly="true"
        :height="500"
        :showToolbar="false"
      />
    </div>
      <div class="horizontal-line"></div>

      <div style="display: flex;">
        <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
          <el-button @click="close()">关闭</el-button>
        </div>
      </div>
      
    </el-dialog>

  </div>
</template>

<script>
import { getAILogListAPI, deleteAILogListAPI, previewAILogAPI} from "@/api/aiLogManager/aiLogAPI.js";
import { logShortcutDate, formatDate, formatDateTime } from "@/utils/common";
import toast from "@/utils/toast";
import WangEditor from '@/components/wangEditor'; // 上面的组件

export default {
  name: "aiLogManager",
  components: { WangEditor},
  data() {
    return {
      loading: false,
      aiLogRange: [
        formatDate(),
        formatDate(),
      ],

      pickerOptions: {
        shortcuts: logShortcutDate,
      }, //日期选择快捷键

      aiLogList: [],
      queryParams: {
        user_id: "",
        username: "",
        log_date_start: "",
        log_date_end: "",
        question:"",
        page: 1,
        page_size: 10,
        total: 0,
      },

      multipleTables: [],
      allMultipleTables: [],

      isLogPreview: false,
      content: '',
      title: '',
    };
  },

  mounted() {
    this.getAILogList();
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

    // 切换全选状态
    toggleAllSelection() {
      // 通过 $refs 获取表格实例，并调用 toggleAllSelection 方法
      this.$refs.multipleTable.toggleAllSelection();
    },
    // 清空所有选中
    clearAllSelection() {
      this.$refs.multipleTable.clearSelection();
    },
    
    handleSelectionChange(val) {
      this.multipleTables = val; // 更新选中项数组
    },

    pageSave() {
      for (let i = 0; i < this.multipleTables.length; i++) {
        this.allMultipleTables.push({
          ...this.multipleTables[i]
      });
      }
      this.multipleTables = [];
      this.getAILogList();
    },

    downloadLog(row) {
      /*let tempQuery = { user_id: row.user_id, log_date: row.log_date };
      this.download(
        "/webapp/api/v1/logs/download",
        tempQuery,
        `日志导出_${row.username}_${row.log_date}.txt`
      );*/
      let tempQuery = { paths:[row.log_path] };

      this.download(
        "/webapp/api/v1/logs/download",
        tempQuery,
        `日志导出_${row.username}_${row.log_date}.txt`
      );
    },

    downloadLogList(){
      let paths = []
      for(let i =0;i< this.allMultipleTables.length;i++){
        paths.push(this.allMultipleTables[i].log_path)
      }
      for(let i =0;i< this.multipleTables.length;i++){
        paths.push(this.multipleTables[i].log_path)
      }
      if(paths.length==0){
        toast.warn('请选择要下载的记录')
        return
      }
      if(paths.length>100){
        toast.warn('单词下载最大为100条')
        return
      }

      let tempQuery = {paths: paths}
      this.download(
        "/webapp/api/v1/logs/download",
        tempQuery,
        `日志导出_${formatDateTime()}.txt`
      );
    },

    textToHtml(text) {
      // 如果内容包含换行，转换为 HTML
      if (text.includes("\n")) {
        return text
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/\n/g, "<br>");
      }

      return `<p>${text}</p>`;
    },
    
    logPreview(row){
      this.isLogPreview = true
      let params = {log_path: row.log_path}
      
      previewAILogAPI(params)
        .then((response) => {
          if (response.code == 200) {
            this.title = row.question
            this.isLogPreview = true
            this.$nextTick(() => {
              this.content = this.textToHtml(response.data.content);
            })
          }
          else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },

    close(){
      this.title = ''
      this.content = ''
      this.isLogPreview = false
    },

    deleteAILogList(row){
      let params = {user_id: row.user_id,log_date:row.log_date}
      deleteAILogListAPI(params)
        .then((response) => {
          if (response.code == 200) {
            toast.success('删除成功')
            this.getAILogList()
          }
          else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },

    //搜索条件变化
    handleEnter() {
      this.queryParams.page = 1
      this.getAILogList();
    },

    handleDateChange(val){
      this.handleEnter()
    },

    //获取数据源列表
    getAILogList() {
      if (this.aiLogRange == null) {
        this.queryParams.log_date_start = "";
        this.queryParams.log_date_end = "";
      } else {
        this.queryParams.log_date_start = this.aiLogRange[0];
        this.queryParams.log_date_end = this.aiLogRange[1];
      }
      this.loading = true;
      getAILogListAPI(this.queryParams)
        .then((response) => {
          if (response.code == 200) {
            this.aiLogList = [];

            this.queryParams.total = response.data.total;
            this.queryParams.page = response.data.page;
            this.queryParams.page_size = response.data.page_size;
            //this.aiLogList = response.data.list;
            for (let i = 0; i < response.data.list.length; i++) {
              this.aiLogList.push(response.data.list[i]);
              const index = this.allMultipleTables.findIndex((item) =>item.id == this.aiLogList[i].id);
              if (index > -1) {
                const rowToSelect = this.aiLogList[i];
                this.$nextTick(() => {
                  this.$refs.multipleTable.toggleRowSelection(
                    rowToSelect,
                    true
                  ); 
                })
                this.allMultipleTables.splice(index, 1);
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
  },
};
</script>

 <style scoped lang="scss">
</style>