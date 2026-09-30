<template>
  <div style="display: flex; flex-direction: column; width: 100%">
    <div style="height: 10px"></div>
  
    <div style="display: flex">
      <div class="three-front-bgdiv">排序字段</div>

      <div class="three-middle-bgdiv">N值</div>
      <div class="three-back-bgdiv">排序方式</div>
    </div>

    <div style="display: flex">
      <div class="three-front-bgdiv">
        <el-select
          v-model="topNDataItem.orders[0].id"
          placeholder="请选择排序字段"
          clearable
        >
          <el-option
            v-for="item in metricsTagItem"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          >
          </el-option>
        </el-select>
      </div>

      <div class="three-middle-bgdiv">
        <el-input
          v-model="topNDataItem.topNum"
          placeholder="请输入内容"
        ></el-input>
      </div>

      <div class="three-back-bgdiv">
        <el-select
          v-model="topNDataItem.orders[0].order"
          placeholder="请选择排序方式"
        >
          <el-option
            v-for="item in orderEnum"
            :key="item.value"
            :label="item.name"
            :value="item.value"
          >
          </el-option>
        </el-select>
      </div>
    </div>

    <div style="display: flex">
      <div class="one-bgdiv">分组字段</div>
    </div>

    <div style="display: flex">
      <div class="one-bgdiv">
        <el-select
          v-model="topNDataItem.groupDims[0].id"
          placeholder="请选择分组字段"
          clearable
        >
          <el-option label="不分组" :value="null"></el-option>
          <el-option
            v-for="item in dimensionsTagItem"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          >
          </el-option>
        </el-select>
      </div>
    </div>

    <div class="horizontal-line"></div>
    <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="close()">取消</el-button>
        <el-button type="primary" @click="sureTopN()">确定</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { orderEnum } from "@/api/metricDataPreview/metricPreviewAPI.js";

export default {
  name: "topNPage",
  props: ["dimensionsTagItem", "metricsTagItem", "topNData"], //topNData固定有个0
  components: {},
  data() {
    return {
      orderEnum,

      topNDataItem: null,
    };
  },

  mounted() {},

  created() {
    this.topNDataItem = JSON.parse(JSON.stringify(this.topNData));
  },

  methods: {
    sureTopN() {
      let index = this.metricsTagItem.findIndex(
        (item) => item.id == this.topNDataItem.orders[0].id
      );
      if (index > -1) {
        this.topNDataItem.orders[0].key = this.metricsTagItem[index].key;
        this.topNDataItem.orders[0].name = this.metricsTagItem[index].name;
      }
      this.$emit("sureTopN", this.topNDataItem);
    },

    //关闭窗口
    close() {
      this.$emit("close");
    },
  },
};
</script>

 <style scoped lang="scss">
.three-front-bgdiv {
  width: 33%;
  text-align: left;
  padding: 0px 5px 5px 24px;
}

.three-middle-bgdiv {
  width: 33%;
  text-align: left;
  padding: 0px 5px 5px 5px;
}

.three-back-bgdiv {
  width: 34%;
  text-align: left;
  padding: 0px 24px 5px 5px;
}
</style>