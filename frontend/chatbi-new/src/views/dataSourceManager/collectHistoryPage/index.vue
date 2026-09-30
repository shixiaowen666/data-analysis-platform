<template>
  <div>
    <div
      style="max-height: 68vh; overflow-y: auto; overflow-x: hidden;margin-top:5px;"
      class="no-scrollbar"
    >
      <div style="display: flex">
        <div class="table-container">
          <div class="drag-table">
            <el-table :data="collectHistory" style="width: 100%" border>
              <el-table-column-with-tooltip
                prop="startedAt"
                label="开始时间"
                width="auto"
                min-width="20%"
                resizable
                sortable

                :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                prop="finishedAt"
                label="结束时间"
                width="auto"
                min-width="20%"
                resizable
                sortable
  
                :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                prop="collectTypeLabel"
                label="采集方式"
                width="auto"
                min-width="14%"
                resizable
                sortable
      
                :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                prop="tableCount"
                label="采集表数"
                width="auto"
                min-width="14%"
                resizable
                sortable
   
                :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                prop="diffSummary"
                label="新增/变更/删除"
                width="auto"
                min-width="18%"
                resizable
                sortable
        
                :render-header="renderHeader"
              />
              <el-table-column-with-tooltip
                prop="status"
                label="状态"
                width="auto"
                min-width="10%"
                resizable
                sortable
       
                :render-header="renderHeader"
              >
                <!--<template slot-scope="scope">
                  {{ scope.row.status }}
                </template>-->
              </el-table-column-with-tooltip>

              <el-table-column-with-tooltip
                prop="durationSeconds"
                label="耗时"
                width="auto"
                min-width="10%"
                resizable
                sortable

                :render-header="renderHeader"
              />

              <el-table-column
                label="操作"
                align="center"
                :resizable="false"
                min-width="10%"
              >
                <template slot-scope="scope">
                  <el-tooltip content="详情" :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay">
                  <el-button
                    type="text"
                    @click="showCollectHistoryDetail(scope.row)"
                    >详情
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
              @pagination="getCollectHistoryList"
            />
          </div>
        </div>
      </div>
    </div>

    <div class="horizontal-line"></div>

    <div style="display: flex">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="close">关闭</el-button>
      </div>
    </div>

    <el-dialog
      title="采集日志详情"
      :modal="false"
      :visible.sync="isCollectHistoryDetail"
      :show-close="false"
      width="70%"
      append-to-body
      class="my-custom-style"
    >
    <div class="table-container">
      <div style="width: 100%; display: flex; justify-content: center">
        <div
          style="
            height: 40vh;
            overflow-y: auto;
            background-color: #f6f6f3;
            width: 100%;
          "
        >
          <div style="text-align: left; margin-bottom: 5px; color: blue;margin-left: 8px;margin-top: 5px;">
            # 采集日志
          </div>

          <div
            v-for="(item, index) in collectHistoryDetailList"
            :key="index + item.time"
            style="
              display: flex;
              align-items: flex-start;
              text-align: left;
              margin-bottom: 5px;
              margin-left: 15px;
            "
          >
            <div style="min-width: 70px">[{{ item.time }}]</div>
            &nbsp;
            <div style="min-width: 80px">[{{ item.level }}]</div>
            &nbsp;
            <div>{{ item.msg }}</div>
            <div>&nbsp;&nbsp;</div>
          </div>
        </div>
      </div>
    </div>

      <div class="horizontal-line"></div>

      <div style="display: flex;">
        <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
          <el-button @click="isCollectHistoryDetail = false">关闭</el-button>
        </div>
      </div>
    
    </el-dialog>
  </div>
</template>


<script>
import {
  getCollectHistoryListAPI,
  getCollectHistoryDetailListAPI,
} from "@/api/dataSourceManager/dataSourceAPI.js";
import toast from '@/utils/toast';

export default {
  name: "collectHistoryPage",
  props: ["dataSourceItem"],
  components: {},
  data() {
    return {
      queryParams: {
        sourceId: null,
        status: null,
        page: 1,
        pageSize: 10,
        total: 0,
      },

      collectHistory: [],

      isCollectHistoryDetail: false,
      collectHistoryDetailList: [],
    };
  },

  mounted() {
    this.getCollectHistoryList();
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

    close() {
      this.$emit("close");
    },

    findNthOccurrence(str, char, n) {
      const arr = str.split(char); 
      if (arr.length - 1 < n) return -1; 
      let position = 0;
      for (let i = 0; i < n; i++) {
        position += arr[i].length + char.length; 
      }
      return position - char.length;
    },

    //采集历史列表
    getCollectHistoryList() {
      if (this.dataSourceItem != null) {
        this.queryParams.sourceId = this.dataSourceItem.id;
        //const loading = this.loadingScreen()
        getCollectHistoryListAPI(this.queryParams)
          .then((response) => {
            if (response.code == 200) {
              if (response.data != null) {
                this.collectHistory = [];

                this.queryParams.total = response.data.total;
                this.queryParams.page = response.data.page;
                this.queryParams.pageSize = response.data.pageSize;
                for (let i = 0; i < response.data.records.length; i++) {
                  let index = this.findNthOccurrence(
                    response.data.records[i].diffSummary,
                    "/",
                    3
                  );
                  if (index > -1) {
                    response.data.records[i].diffSummary =
                      response.data.records[i].diffSummary.substring(0, index);
                  }
                  this.collectHistory.push(response.data.records[i]);
                }
              }
            } else {
              toast.error(response.message)
            }
          })
          .catch(() => {})
          .finally(() => {
          });
      }
    },

    showCollectHistoryDetail(row) {
      this.getCollectHistoryDetailList(row);
      this.isCollectHistoryDetail = true;
    },

    getCollectHistoryDetailList(row) {
      getCollectHistoryDetailListAPI(row.id)
        .then((response) => {
          if (response.code == 200) {
            if (response.data != null) {
              this.collectHistoryDetailList = [];
              for (let i = 0; i < response.data.logLines.length; i++) {
                this.collectHistoryDetailList.push(response.data.logLines[i]);
              }
            }
          } else {
            toast.error(response.message)
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },
  },
};
</script>

 <style scoped lang="scss">
</style>