<template>
  <div style="display: flex; flex-direction: column; width: 100%">
    <div
      style="max-height: 60vh; overflow-y: auto; overflow-x: hidden;margin-top:10px;"
      class="no-scrollbar"
    >
      <div style="display: flex">
        <div class="two-front-bgdiv">
          组合编码<span style="color: red">*</span>
        </div>
        <div class="two-back-bgdiv">
          组合名称<span style="color: red">*</span>
        </div>
      </div>

      <div style="display: flex">
        <div class="two-front-bgdiv">
          <el-input
            v-model="groupCode"
            placeholder="month_report"
            :disabled="groupItem.id != null"
          ></el-input>
        </div>

        <div class="two-back-bgdiv">
          <el-input v-model="groupName" placeholder="如:月报"></el-input>
        </div>
      </div>

      <div style="display: flex">
        <div class="one-bgdiv">描述</div>
      </div>

      <div style="display: flex">
        <div class="one-bgdiv">
          <el-input v-model="description" placeholder="组合描述"></el-input>
        </div>
      </div>

      <div style="display: flex">
        <div class="one-bgdiv">
          <div
            style="
              background: rgb(248 250 252 / 1);
              border: solid 1px #e5e7eb;
              padding: 0.75rem;
            "
          >
          <div style="padding: 3px 5px;display:flex;">
            <div style="text-wrap: nowrap;width:60px;">
              ✅&nbsp;维度:&nbsp;
  
            </div>
            <div style="flex:1;">            <span v-for="(dim, index) in query.dimList" :key="dim.id">
                <span v-if="index > 0">,</span> {{ dim.dimName }}</span
              >
            </div>
          </div>

            <div style="padding: 3px 5px;display:flex;">
            <div style="text-wrap: nowrap;width:60px;">
              ✅&nbsp;指标:&nbsp;
              
            </div>
            <div style="flex:1;"> <span v-for="(metric, index) in query.indexList" :key="metric.id"
                ><span v-if="index > 0">,</span> {{ metric.indName }}</span
              ></div>
            </div>

          </div>
        </div>
      </div>
    </div>
    <div class="horizontal-line"></div>

    <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="close()">取消</el-button>
        <el-button type="primary" @click="saveGroupData()">确定</el-button>
      </div>
    </div>
  </div>
</template>


<script>
import { saveGroupDataAPI } from "@/api/portfolioManager/portfolioAPI.js";
import toast from "@/utils/toast";

export default {
  name: "newProtfolioPage",
  props: ["query", "groupItem"],
  components: {},
  data() {
    return {
      groupCode: "",
      groupName: "",
      description: "",
    };
  },

  mounted() {
    this.groupCode = this.groupItem.groupCode;
    this.groupName = this.groupItem.groupName;
    this.description = this.groupItem.description;
  },

  methods: {
    //关闭窗口
    close() {
      this.$emit("close");
    },

    detectError() {
      if (!this.groupCode?.toString().trim()) {
        toast.error("请输入组合编码");
        return false;
      }

      if (!this.groupName?.toString().trim()) {
        toast.error("请输入组合名称");
        return false;
      }

      return true;
    },

    saveGroupData() {
      if (!this.detectError()) return;

      let groupConfig = {
        timeRange: {
          start: this.query.timeRange.start,
          end: this.query.timeRange.end,
        },
        timeGranularity: this.query.dateGranularity,
        dimensions: [],
        indicators: [],
        indicatorComparison: this.query.indicatorComparison,
        filters: this.query.filters,
        sorts: this.query.orderList,
        topN: null,
      };

      if (Object.keys(this.query.top).length !== 0) {
        groupConfig.topN = this.query.top;
      }

      for (let i = 0; i < this.query.dimList.length; i++) {
        groupConfig.dimensions.push({
          id: this.query.dimList[i].id,
          key: this.query.dimList[i].dimKey,
          name: this.query.dimList[i].dimName,
        });
      }
      for (let i = 0; i < this.query.indexList.length; i++) {
        groupConfig.indicators.push({
          id: this.query.indexList[i].id,
          key: this.query.indexList[i].indKey,
          name: this.query.indexList[i].indName,
        });
      }

      let params = {
        id: this.groupItem.id,
        groupCode: this.groupCode,
        groupName: this.groupName,
        description: this.description,
        groupConfig: JSON.stringify(groupConfig),
      };

      saveGroupDataAPI(params)
        .then((response) => {
          if (response.code == 200) {
            this.$emit("sureProtfolio");
            toast.success("保存成功");
          } else {
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