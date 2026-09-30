<template>
  <div style="display: flex; flex-direction: column; width: 100%">

    <div
      style="max-height: 60vh; overflow-y: auto; overflow-x: hidden;margin-top:10px;"
      class="no-scrollbar"
    >
      <div style="display: flex">
        <div class="three-front-bgdiv">字段</div>

        <div class="three-middle-bgdiv">条件</div>
        <div class="three-middle-bgdiv">值</div>

        <div
          style="width: 10%; text-align: left; padding: 0px 30px 5px 5px"
        ></div>
      </div>

      <div
        style="display: flex"
        v-for="(filterItem, index) in filterDataItem"
        :key="index"
      >
        <div class="three-front-bgdiv">
          <el-select
            v-model="filterItem.filterField.id"
            placeholder="请选择字段"
            @change="onFieldConfirm(filterItem)"
          >
            <el-option
              v-for="item in allTagItem"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            >
            </el-option>
          </el-select>
        </div>

        <div class="three-middle-bgdiv">
          <el-select v-model="filterItem.operator" placeholder="请选择条件" @change="onOperatorConfirm(filterItem)">
            <el-option
              v-for="item in operatorEnum"
              :key="item.value"
              :label="`${item.name} ${item.symbol}`"
              :value="item.value"
            >
            </el-option>
          </el-select>
        </div>

                            <div
                              class="three-middle-bgdiv"
                                style="position: relative"
                                v-if="isMulti(filterItem)"
                              >
                                <el-select
                                  placeholder="请选择值"
                                  v-model="filterItem.filterValue"
                                  multiple
                                  collapse-tags
                                  filterable
                                  class="filter-cond__value--select"
                                >
                                  <el-option
                                    v-for="item in filterItem.options"
                                    :key="item"
                                    :label="item"
                                    :value="item"
                                  >
                                  </el-option>
                                </el-select>
                              </div>
                              
                              <div class="three-middle-bgdiv" v-else>
          <el-input
            v-model="filterItem.filterValue"
            placeholder="如:10000"
          ></el-input>
        </div>
        <div style="padding-right: 24px;flex:1; text-align: left">
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
              @click="deleteFilter(index)"
              ><base-icon name="trash" :size="14"
            /></el-button>
          </el-tooltip>
        </div>
      </div>

      <div style="display: flex;padding-top: 5px;">
        <div class="one-bgdiv">
          <el-button class="toolbar-btn" @click="newFilter()"
            ><base-icon name="plus" :size="14" />&nbsp;添加</el-button
          >
        </div>
      </div>
    </div>

    <div class="horizontal-line"></div>

    <div style="display: flex; padding-bottom: 5px">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="close()">取消</el-button>
        <el-button type="primary" @click="sureFilter()">确定</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  operatorEnum,
  filterTypeEnum,
} from "@/api/metricDataPreview/metricPreviewAPI.js";
import toast from "@/utils/toast";

import {
  isMultiValueOperator,
} from "@/api/newBI/newBIAPI.js";
import { getDimensionValuesAPI } from "@/api/dimensionManager/dimensionAPI.js";

export default {
  name: "filterPage",
  props: ["dimensionsTagItem", "metricsTagItem", "filterData"],
  components: {},
  data() {
    return {
      operatorEnum,
      filterTypeEnum,

      allTagItem: [],
      filterDataItem: null,

    };
  },

  mounted() {
    /*this.allTagItem = []
    for(let i =0;i < this.metricsTagItem.length;i++){
      let temp = Object.assign({}, this.metricsTagItem[i])
      temp.type = 1
      this.allTagItem.push(temp)
    }
    for(let i =0;i < this.dimensionsTagItem.length;i++){
          let temp = Object.assign({}, this.dimensionsTagItem[i])
      temp.type = 0
      this.allTagItem.push(temp)
    }*/

    this.allTagItem = [...this.metricsTagItem, ...this.dimensionsTagItem]; //将两个合并
    this.filterDataItem = JSON.parse(JSON.stringify(this.filterData));

    if (!(this.filterDataItem?.length > 0)) this.newFilter();
  },

  methods: {
    sureFilter() {
      for (let i = 0; i < this.filterDataItem.length; i++) {
        if (
          !this.filterDataItem[i].filterField.id ||
          !this.filterDataItem[i].filterValue
        ) {
          toast.error("有空值,请填好值在提交");
          return;
        }
      }

      for (let i = 0; i < this.filterDataItem.length; i++) {
        let index = this.metricsTagItem.findIndex(
          (item) => item.id == this.filterDataItem[i].filterField.id
        );
        if (index > -1) {
          this.filterDataItem[i].filterField.key =
            this.metricsTagItem[index].key;
          this.filterDataItem[i].filterField.name =
            this.metricsTagItem[index].name;
          this.filterDataItem[i].filterField.fieldClazz =
            filterTypeEnum["metric"].value;
        }
      }

      for (let i = 0; i < this.filterDataItem.length; i++) {
        let index = this.dimensionsTagItem.findIndex(
          (item) => item.id == this.filterDataItem[i].filterField.id
        );
        if (index > -1) {
          this.filterDataItem[i].filterField.key =
            this.dimensionsTagItem[index].key;
          this.filterDataItem[i].filterField.name =
            this.dimensionsTagItem[index].name;
          this.filterDataItem[i].filterField.fieldClazz =
            filterTypeEnum["dim"].value;
        }
      }

      this.$emit("sureFilter", this.filterDataItem);
    },

    //关闭窗口
    close() {
      this.$emit("close");
    },

    newFilter() {
      if (this.filterDataItem == null) this.filterDataItem = [];
      for (let i = 0; i < this.filterDataItem.length; i++) {
        if (
          !this.filterDataItem[i].filterField.id ||
          !this.filterDataItem[i].filterValue
        ) {
          return;
        }
      }

      this.filterDataItem.push({
        filterField: { fieldClazz: null, id: null, key: "", name: "" },
        operator: 2,
        filterValue: "",
        options: [],
      });
    },

    deleteFilter(index) {
      this.filterDataItem.splice(index, 1);
    },

    async onOperatorConfirm(f) {
      //f.operator = op;
      // 值类型随运算符切换（数组 <-> 标量）
      if (this.isMulti(f)) {
        f.filterValue = Array.isArray(f.filterValue) ? f.filterValue : f.filterValue ? [f.filterValue] : [];
      } else {
        f.filterValue = Array.isArray(f.filterValue)
          ? f.filterValue[0] || ""
          : f.filterValue == null
          ? ""
          : f.filterValue;
      }
    },

    async onFieldConfirm(f){
      if (this.isMulti(f)) {
        f.filterValue = []
      }else {
        f.filterValue = ''
      }
      
      await this.loadFilterValues(f)
    },

    isMulti(f) {
      //console.log(f)
      const index = this.dimensionsTagItem.findIndex(item => item.id == f.filterField.id)
      if(index > -1)
        return isMultiValueOperator(f.operator);
      
      return false
    },

    async loadFilterValues(f) {
      if (f.operator && f.operator.length) return;
      const index = this.dimensionsTagItem.findIndex(item => item.id == f.filterField.id)
      if (index < 0 || !f.filterField.id) return;

      //f.loadingOptions = true;
      try {
        const res = await getDimensionValuesAPI(f.filterField.id);
        if (res && res.code === 200) f.options = res.data || [];
      } catch (e) {
        /* handled */
      } finally {
        //f.loadingOptions = false;

      }
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

.filter-cond__value--select {
  width: 100%;
  
  ::v-deep .el-input__inner {
    height: 30px !important;
  }
  
  ::v-deep .el-select__tags {
    display: flex;
    flex-wrap: nowrap !important;
    overflow: hidden !important;
    height: 28px;
    padding: 0 4px;
    gap: 2px;
  }
  
  ::v-deep .el-tag {
    display: inline-flex;
    align-items: center;
    height: 20px;
    line-height: 18px;
    margin: 0;
    padding: 0 6px;
    flex: 0 1 auto;               // 不固定宽度，自动收缩
    min-width: 32px;
    max-width: 300px;             // 限制最大宽度，避免单个标签太长
    background: #eef3ff;
    border-color: #dbe4ff;
    color: #2b5cff;
    font-size: 12px;
  }
  
  ::v-deep .el-tag .el-select__tags-text {
    display: inline-block;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }
}
</style>