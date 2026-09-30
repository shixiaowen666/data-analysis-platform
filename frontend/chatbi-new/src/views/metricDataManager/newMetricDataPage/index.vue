<template>
  <div
    style="
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      width: 100%;
      height: 100%;
    "
  >
    <div style="flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 0">

      <div
        style="flex: 1; overflow-y: auto; overflow-x: hidden; min-height: 0;margin-top:10px;"
        class="no-scrollbar" ref="mainContent"
      >
        <div style="display: flex">
          <div class="two-front-bgdiv">
            指标英文名<span style="color: red">*</span>
          </div>

          <div class="two-back-bgdiv">
            指标标准名称<span style="color: red">*</span>
          </div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv">
            <el-input
              v-model="metricData.englishName"
              placeholder="请输入英文名"
              @input="handleMetricsKeyInput(metricData.englishName)"
              :disabled="metricDataItem != null"
            ></el-input>
            <div style="color: red">{{ englishNameText }}</div>
          </div>

          <div class="two-back-bgdiv">
            <el-input
              v-model="metricData.chineseName"
              placeholder="如月度总收入"
              @input="handleMetricsNameInput(metricData.chineseName)"
            ></el-input>
            <div style="color: red">{{ chineseNameText }}</div>
          </div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv">
            指标别名<span style="color: silver">(多个别名用|分割)</span>
          </div>
          <div class="two-back-bgdiv">指标类型</div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv">
            <el-input
              v-model="metricData.alias"
              placeholder="月度收入|月营收"
            ></el-input>
          </div>
          <div class="two-back-bgdiv">
            <el-select
              placeholder="请选择指标类型"
              v-model="metricData.type"
              style="width: 100%"
              :disabled="metricData.id != null"
            >
              <el-option
                v-for="item in typeMetricEnum"
                :key="item.value"
                :label="item.name"
                :value="item.value"
              >
              </el-option>
            </el-select>
          </div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv">小数点位数</div>

          <div class="two-back-bgdiv">默认计量单位</div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv">
            <el-select
              placeholder="请选择小数点位数"
              v-model="metricData.decimalPlaces"
              style="width: 100%"
            >
              <el-option
                v-for="item in decimalEnum"
                :key="item.value"
                :label="item.name"
                :value="item.value"
              >
              </el-option>
            </el-select>
          </div>

          <div class="two-back-bgdiv">
            <el-input
              v-model="metricData.unit"
              placeholder="如元、%、次"
            ></el-input>
          </div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv" style="display: flex">阈值规则</div>

          <div class="two-back-bgdiv" style="display: flex">空值处理</div>
        </div>

        <div style="display: flex">
          <div class="two-front-bgdiv">
            <el-input
              v-model="metricData.thresholdRule"
              placeholder="如:≤7.5政策,7.5-9关注,>9异常"
            ></el-input>
          </div>

          <div class="two-back-bgdiv">
            <el-input
              v-model="metricData.nullHandling"
              placeholder="如:分母为 0 展示 --，不参与排名"
            ></el-input>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">指标标签</div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <el-input
              v-model="metricData.classificationLabelName"
              placeholder="核心指标、监管指标(逗号分隔)"
            ></el-input>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">口径描述</div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <el-input
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 5 }"
              resize="none"
              placeholder="统计范围、统计规则、特殊说明"
              v-model="metricData.caliberDescription"
            >
            </el-input>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div style="display: flex; align-items: center">
              <div class="vertical-line"></div>
              <div class="title16-one-bgdiv">&nbsp;&nbsp;映射字段</div>
            </div>

            <div class="horizontal-line"></div>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div class="drag-table">
              <el-table :data="metricData.mappingList" border>
                <el-table-column-with-tooltip
                  label="来源表"
                  prop="soucreTableName"
                  width="auto"
                  min-width="40%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="映射字段"
                  prop="sourceField"
                  width="auto"
                  min-width="30%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  prop="summary"
                  label="聚合函数"
                  width="auto"
                  min-width="30%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
              </el-table>
            </div>
          </div>
        </div>

        <div
          style="display: flex"
          v-if="
            metricData.type === typeMetricEnum['derive'].value ||
            metricData.type === typeMetricEnum['calc'].value
          "
        >
          <div class="one-bgdiv">
            <div style="display: flex; align-items: center">
              <div class="vertical-line"></div>
              <div class="title16-one-bgdiv">&nbsp;&nbsp;计算逻辑</div>
            </div>

            <div class="horizontal-line"></div>
          </div>
        </div>

        <div v-if="metricData.type === typeMetricEnum['derive'].value">
          <div style="display: flex">
            <div class="four-bgdiv" style="padding: 0px 10px 10px 24px">
              指标<span style="color: red">*</span>
            </div>

            <div class="four-bgdiv" style="padding: 0px 10px 10px 0px">
              维度<span style="color: red">*</span>
            </div>

            <div class="four-bgdiv" style="padding: 0px 10px 10px 0px">
              运算符
            </div>

            <div class="four-bgdiv" style="padding: 0px 24px 10px 0px">
              维度值
            </div>
          </div>

          <div style="display: flex">
            <div class="four-bgdiv" style="padding: 0px 10px 10px 24px">
              <el-select
                placeholder="请选择指标"
                v-model="derivativeFormula.indicatorId"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="item in metricItem"
                  :key="item.id"
                  :label="item.chineseName"
                  :value="item.id"
                >
                </el-option>
              </el-select>
            </div>

            <div class="four-bgdiv" style="padding: 0px 10px 10px 0px">
              <el-select
                placeholder="请选择维度"
                v-model="derivativeFormula.dimensionList[0].dimensionId"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="item in dimensionItem"
                  :key="item.id"
                  :label="item.chineseName"
                  :value="item.id"
                >
                </el-option>
              </el-select>
            </div>

            <div class="four-bgdiv" style="padding: 0px 10px 10px 0px">
              <el-select
                placeholder="请输入运算符"
                v-model="derivativeFormula.dimensionList[0].symbol.key"
                style="width: 100%"
              >
                <el-option
                  v-for="item in symbolEnum"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </div>

            <div class="four-bgdiv" style="padding: 0px 24px 10px 0px">
              <el-input
                v-model="derivativeFormula.dimensionList[0].dimensionValueList"
                placeholder="如元、%、次"
              ></el-input>
            </div>
          </div>

          <div style="display: flex">
            <div class="one-bgdiv">
              <div class="warntext-bg">
                示例:总收入+地区=北京>生成派生指标「北京地区收入」
              </div>
            </div>
          </div>
        </div>

        <div v-if="metricData.type === typeMetricEnum['calc'].value">
          <div style="display: flex">
            <div class="one-bgdiv">
              计算公式 (${A}+${B})/${C})<span style="color: red">*</span>
            </div>
          </div>

          <div style="display: flex">
            <div class="one-bgdiv">
              <el-input
                placeholder=""
                v-model="calculateFormula.displayFormula"
              ></el-input>
            </div>
          </div>

          <div style="display: flex">
            <div class="one-bgdiv">依赖指标</div>
          </div>


          <div
            style="display: flex;" 
            v-for="(item, index) in calculateFormula.indicatorList"
            :key="item.id+index"
          >
            <div class="one-bgdiv" style="display: flex; align-items: center">
              <div style="margin-right: 10px;width: 45px;">
                <!--指标-{{ String.fromCharCode(65 + item.letter) }}-->
                指标-{{ item.letter }}
              </div>

              <el-select
                placeholder="请选择指标"
                style="width: 50%"
                v-model="item.id"
                filterable
              >
                <el-option
                  v-for="item in metricItem"
                  :key="item.id"
                  :label="item.chineseName"
                  :value="item.id"
                >
                </el-option>
              </el-select>

              <div style="margin-left: 10px;width:30px">
                <el-tooltip
                  content="删除"
                  :placement="$toolTipPlacement"
                  :effect="$toolTipEffect"
                  :open-delay="$toolTipOpenDelay"
                >
                  <el-button
                    type="text"
                    style="color: red;padding: 0;"
                    icon="el-icon-delete"
                    @click="deleteMetric(index)"
                  ></el-button>
                </el-tooltip>
              </div>
            </div>
          </div>
          <div style="display: flex">
            <div class="one-bgdiv">
              <el-button @click="newMetric()">添加指标</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

      <div class="horizontal-line"></div>

      <div style="display: flex">
        <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
          <el-button @click="close()">取消</el-button>
          <el-button type="primary" @click="newMetricData()">
            <div v-if="metricDataItem == null">提交</div>
            <div v-else>更新</div>
          </el-button>
        </div>
      </div>

  </div>
</template>

<script>
import {
  getMetricDataDetailAPI,
  newMetricDataAPI,
  editMetricDataAPI,
  getShowMetricDataListAPI,
  decimalEnum,
  symbolEnum,
  typeMetricEnum,
} from "@/api/metricDataManager/metricDataAPI.js";

import {
  checkMetricsEnameAPI,
  checkMetricsCnameAPI,
} from "@/api/fieldMappingManager/fieldMappingAPI.js";

import { getShowDimensionListAPI } from "@/api/dimensionManager/dimensionAPI.js";
import toast from "@/utils/toast";
export default {
  name: "newMetricDataPage",
  props: ["metricDataItem"],
  components: {},
  data() {
    return {
      decimalEnum,
      symbolEnum,
      typeMetricEnum,

      englishNameText: "",
      chineseNameText: "",

      metricItem: [],
      dimensionItem: [],

      metricData: {
        chineseName: "",
        englishName: "",
        alias: "",
        decimalPlaces: "",
        unit: "",
        thresholdRule: "",
        nullHandling: "",
        classificationLabelName: "",
        caliberDescription: "",
        mappingList: [],
        aggregateFunction: "",
        type: "atom",
      },

      //派生指标
      derivativeFormula: {
        indicatorId: null,
        indicatorName: "",
        dimensionList: [
          {
            dimensionId: null,
            dimensionName: "",
            dimensionValueList: "",
            symbol: {
              key: "",
              value: "",
            },
          },
        ],
      },

      //计算指标
      calculateFormula: {
        displayFormula: "",
        formula: "",
        indicatorList: [],
      },
    };
  },
  mounted() {
    this.getShowMetricDataList();
    this.getShowDimensionList();

    this.getMetricDataDetail();
    this.newMetric();
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

    async handleMetricsKeyInput(name) {
      let id = null;
      if (this.metricDataItem != null) {
        id = this.metricDataItem.id;
      }
      await this.checkMetricsEname(name, id);
    },
    async handleMetricsNameInput(name) {
      let id = null;
      if (this.metricDataItem != null) {
        id = this.metricDataItem.id;
      }
      await this.checkMetricsCname(name, id);
    },

    async checkMetricsEname(name, id) {
      let data = { name: name, id: id };
      return checkMetricsEnameAPI(data)
        .then((response) => {
          if (response.code == 200) {
            if (response.data.exist) {
              this.englishNameText = response.data.errMsg;
            } else {
              this.englishNameText = "";
            }
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    async checkMetricsCname(name, id) {
      let data = { name: name, id: id };
      return checkMetricsCnameAPI(data)
        .then((response) => {
          if (response.code == 200) {
            if (response.data.exist) {
              this.chineseNameText = response.data.errMsg;
            } else {
              this.chineseNameText = "";
            }
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    //新增指标，字符
    newMetric() {
      if (!this.calculateFormula.hasOwnProperty("indicatorList")) {
        this.$set(this.calculateFormula, "indicatorList", []);
      }

      for (let i = 0; i < this.calculateFormula.indicatorList.length; i++) {
        if (this.calculateFormula.indicatorList[i].id == null) {
          return;
        }
      }

      let letter = "";
      for (let j = 65; j < 65 + 26; j++) {
        let isExist = false;
        for (let i = 0; i < this.calculateFormula.indicatorList.length; i++) {
          let char =
            this.calculateFormula.indicatorList[i].letter.charCodeAt(0);
          if (char == j) {
            isExist = true;
            break;
          }
        }

        if (!isExist) {
          letter = String.fromCharCode(j);
          break;
        }
      }

      if (letter == "") return;
      this.calculateFormula.indicatorList.push({
        id: null,
        letter: letter,
        name: "",
      });
      this.scrollToBottom(true)
    },

    scrollToBottom(smooth) {
      this.$nextTick(() => {
        const el = this.$refs.mainContent;
        if (!el) return;
        el.scrollTo({
          top: el.scrollHeight,
          behavior: smooth ? "smooth" : "auto",
        });
        this.followBottom = true;
      });
    },

    deleteMetric(index) {
      this.calculateFormula.indicatorList.splice(index, 1);
    },

    //关闭窗口
    close() {
      this.$emit("close");
    },

    getMetricDataDetail() {
      if (this.metricDataItem != null) {
        getMetricDataDetailAPI(this.metricDataItem.id)
          .then((response) => {
            if (response.code == 200) {
              this.metricData = response.data;
              if (response.data.hasOwnProperty("calculateFormula")) {
                this.calculateFormula = response.data.calculateFormula;
              }
              if (response.data.hasOwnProperty("derivativeFormula")) {
                this.derivativeFormula = response.data.derivativeFormula;
              }

              if (!(this.calculateFormula.indicatorList?.length > 0))
                this.newMetric();
            } else {
              //访问失败
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
      }
    },

    newMetricData() {
      if (this.englishNameText.toString().trim() || this.chineseNameText.toString().trim()) {
        toast.error("不能提交重复的名称");
        return;
      }

      if (!this.metricData.chineseName?.toString().trim()) {
        toast.error("指标标准名称不能为空");
        return;
      }
      if (!this.metricData.englishName?.toString().trim()) {
        toast.error("指标英文名不能为空");
        return;
      }
      if (this.metricData.type == typeMetricEnum["calc"].value) {
        if (!this.calculateFormula.displayFormula) {
          toast.error("计算逻辑中，计算公式不能为空");
          return;
        }
      }
      if (this.metricData.type == typeMetricEnum["derive"].value) {
        if (!this.derivativeFormula.indicatorId) {
          toast.error("计算逻辑中，指标不能为空");
          return;
        }
        if (!this.derivativeFormula.dimensionList[0].dimensionId) {
          toast.error("计算逻辑中，维度不能为空");
          return;
        }
      }

      //计算指标，替换
      if (this.metricData.type == typeMetricEnum["calc"].value) {
        if (this.calculateFormula) {
          this.calculateFormula.formula = this.calculateFormula.displayFormula;
          for (let i = 0; i < this.calculateFormula.indicatorList.length; i++) {
            let temp =
              "${" + this.calculateFormula.indicatorList[i].letter + "}";
            this.calculateFormula.formula =
              this.calculateFormula.formula.replace(
                temp,
                "{" + this.calculateFormula.indicatorList[i].id + "}"
              );
          }
        }
        this.metricData.calculateFormula = this.calculateFormula;
      }
      //衍生指标，获取
      if (this.metricData.type == typeMetricEnum["derive"].value) {
        let index = this.metricItem.findIndex(
          (item) => item.id == this.derivativeFormula.indicatorId
        );
        if (index > -1) {
          this.derivativeFormula.indicatorName =
            this.metricItem[index].chineseName;
        }
        index = this.dimensionItem.findIndex(
          (item) =>
            item.id == this.derivativeFormula.dimensionList[0].dimensionId
        );
        if (index > -1) {
          this.derivativeFormula.dimensionList[0].dimensionName =
            this.dimensionItem[index].chineseName;
        }
        this.derivativeFormula.dimensionList[0].symbol.value =
          this.derivativeFormula.dimensionList[0].symbol.key;
        this.metricData.derivativeFormula = this.derivativeFormula;
      }

      if (this.metricDataItem == null) {
        newMetricDataAPI(this.metricData)
          .then((response) => {
            if (response.code == 200) {
              //成功
              toast.success("添加成功");
              this.$emit("sure");
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
      } else {
        editMetricDataAPI(this.metricDataItem.id, this.metricData)
          .then((response) => {
            if (response.code == 200) {
              toast.success("修改成功");
              this.$emit("sure");
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
      }
    },

    getShowMetricDataList() {
      let queryParams = { keyword: "" };
      getShowMetricDataListAPI(queryParams)
        .then((response) => {
          if (response.code == 200) {
            this.metricItem = response.data;
          } else {
            //访问失败
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    getShowDimensionList() {
      let queryParams = { keyword: "" };
      getShowDimensionListAPI(queryParams)
        .then((response) => {
          if (response.code == 200) {
            this.dimensionItem = response.data;
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