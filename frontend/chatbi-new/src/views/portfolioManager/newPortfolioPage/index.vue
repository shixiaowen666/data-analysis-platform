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
    <div>
      <div style="height: 5px"></div>

      <div
        style="max-height: 82vh; overflow-y: auto; overflow-x: hidden"
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
              v-model="portfolioData.groupCode"
              placeholder="组合编码"
              :disabled="portfolioDataItem != null"
            ></el-input>
          </div>

          <div class="two-back-bgdiv">
            <el-input
              v-model="portfolioData.groupName"
              placeholder="如:运营成绩单"
            ></el-input>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">描述</div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <el-input
              v-model="portfolioData.description"
              placeholder="组合说明"
            ></el-input>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div style="display: flex; align-items: center">
              <div class="vertical-line"></div>
              <div class="title16-one-bgdiv">&nbsp;&nbsp;字段选择</div>
            </div>

            <div class="horizontal-line"></div>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">维度候选</div>
        </div>

        <div style="display: flex; padding-bottom: 10px">
          <div class="one-bgdiv">
            <el-select
              v-model="dimensionSelectItem"
              multiple
              placeholder="请选择维度"
              class="select-one"
              @change="dimensionSelectClick"
            >
              <el-option
                v-for="item in dimensionSelectOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
              </el-option>
            </el-select>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">指标候选</div>
        </div>

        <div style="display: flex; padding-bottom: 10px">
          <div class="one-bgdiv">
            <el-select
              v-model="metricSelectItem"
              multiple
              placeholder="请选择指标"
              class="select-two"
              @change="metricSelectClick"
            >
              <el-option
                v-for="item in metricSelectOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
              </el-option>
            </el-select>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">指标组合候选</div>
        </div>

        <div style="display: flex; padding-bottom: 10px">
          <div class="one-bgdiv">
            <el-select
              v-model="protfolioSelectItem"
              multiple
              placeholder="请选择指标组合"
              class="select-three"
              @change="protfolioSelectClick"
            >
              <el-option
                v-for="item in protfolioSelectOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
              </el-option>
            </el-select>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div style="display: flex; align-items: center">
              <div class="vertical-line"></div>
              <div class="title16-one-bgdiv">&nbsp;&nbsp;结构配置</div>
            </div>
            <div class="horizontal-line"></div>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div class="drag-table">
              <el-table
                :data="portfolioData.items"
                border
                @header-dragend="onDragEnd"
                ref="tableRef"
              >
                <el-table-column-with-tooltip
                  label="顺序"
                  align="center"
                  prop="displayOrder"
                  width="65px"
                  min-width="65"
                />
                <el-table-column-with-tooltip
                  label="显示名称"
                  prop="displayName"
                  width="auto"
                  min-width="30%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="类型"
                  prop="itemTypeName"
                  width="auto"
                  min-width="15%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />

                <el-table-column-with-tooltip
                  label="格式"
                  prop="formatType"
                  width="auto"
                  min-width="15%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column-with-tooltip
                  label="单位"
                  prop="unit"
                  width="auto"
                  min-width="15%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                />
                <el-table-column
                  label="排序方式"
                  prop="defaultSort"
                  width="auto"
                  min-width="15%"
                  resizable
                  sortable
                  :render-header="renderHeader"
                >
                  <template slot-scope="scope">
                    <el-select
                      placeholder="默认"
                      v-model="scope.row.defaultSort"
                      @change="changeSort(scope.row)"
                    >
                      <el-option label="默认" value="default"></el-option>

                      <el-option
                        v-for="item in orderEnum"
                        :key="item.value"
                        :label="item.name"
                        :value="item.value"
                      >
                      </el-option>
                    </el-select>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div style="display: flex; align-items: center">
              <div class="vertical-line"></div>
              <div class="title16-one-bgdiv">&nbsp;&nbsp;预览</div>
            </div>

            <div class="horizontal-line"></div>
          </div>
        </div>

        <div style="display: flex">
          <div class="one-bgdiv">
            <div class="drag-table">
              <el-table :data="metricsDataPreview.records" border stripe>
                <el-table-column-with-tooltip
                  resizable
                  sortable
                  width="auto"
                  v-for="firstColumn in metricsDataPreview.columns"
                  :key="firstColumn.key"
                  :prop="firstColumn.key"
                  :label="
                    firstColumn.hasOwnProperty('unit')
                      ? firstColumn.name + '(' + firstColumn.unit + ')'
                      : firstColumn.name
                  "
                  :render-header="renderHeader"
                >
                  <el-table-column-with-tooltip
                    resizable
                    sortable
                    width="auto"
                    v-for="secondColumn in firstColumn.children"
                    :key="secondColumn.key"
                    :prop="secondColumn.key"
                    :label="
                      secondColumn.hasOwnProperty('unit')
                        ? secondColumn.name + '(' + secondColumn.unit + ')'
                        : secondColumn.name
                    "
                    :render-header="renderHeader"
                  >
                    <el-table-column-with-tooltip
                      resizable
                      sortable
                      width="auto"
                      v-for="thirdColumn in secondColumn.children"
                      :key="thirdColumn.key"
                      :prop="thirdColumn.key"
                      :label="
                        thirdColumn.hasOwnProperty('unit')
                          ? thirdColumn.name + '(' + thirdColumn.unit + ')'
                          : thirdColumn.name
                      "
                      :render-header="renderHeader"
                    >
                    </el-table-column-with-tooltip>
                  </el-table-column-with-tooltip>
                </el-table-column-with-tooltip>
              </el-table>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div>
      <div class="horizontal-line"></div>

      <div style="display: flex">
        <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
          <el-button @click="close">取消</el-button>

          <!--<el-button @click="newPortfolioData('draft')">保存草稿</el-button>-->
          <el-button type="primary" @click="newPortfolioData()">
            <div v-if="portfolioDataItem == null">提交</div>
            <div v-else>更新</div>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>


<script>
import {
  getPortfolioDataDetailAPI,
  savePortfolioDataAPI,
  getCandidatesAPI,
  itemTypeEnum,
  orderEnum,
} from "@/api/portfolioManager/portfolioAPI.js";

import { getMetricsPreviewAPI } from "@/api/metricDataPreview/metricPreviewAPI.js";
import toast from "@/utils/toast";

export default {
  name: "newPortfolioPage",
  props: ["portfolioDataItem"],
  components: {},
  data() {
    return {
      itemTypeEnum,
      orderEnum,

      portfolioData: {
        id: null,
        status: 2,
        groupCode: "",
        groupName: "",
        subjectDomain: "",
        description: "",
        items: [],
        createdAt: "",
        updatedAt: "",
      },
      //选择的数据
      dimensionSelectItem: [],
      metricSelectItem: [],
      protfolioSelectItem: [],

      //可选数据
      dimensionSelectOptions: [],
      metricSelectOptions: [],
      protfolioSelectOptions: [],
      //预览
      metricsDataPreview: {
        columns: [],
        records: [],
      },
    };
  },

  mounted() {
    this.getCandidates();
    this.getPortfolioDataDetail();
  },
  methods: {
    onDragEnd(newWidth, oldWidth, column) {
      if (column.property == "defaultSort") {
        const minWidth = 80;
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

    /*loadingScreen() {
      const loading = this.$loading({
        lock: true,
        text: "Loading",
        spinner: "el-icon-loading",
        background: "rgba(0, 0, 0, 0.7)",
      });

      return loading;
    },*/

    //关闭窗口
    close() {
      this.$emit("close");
    },

    dimensionSelectClick(index) {
      this.evenSelectClick(
        this.dimensionSelectItem,
        this.dimensionSelectOptions,
        [itemTypeEnum.DIMENSION.value]
      );
    },

    metricSelectClick(index) {
      this.evenSelectClick(this.metricSelectItem, this.metricSelectOptions, [
        itemTypeEnum.ATOMICMETRIC.value,
        itemTypeEnum.CALCULATEDMETRIC.value,
        itemTypeEnum.DERIVEDMETRIC.value,
      ]);
    },

    protfolioSelectClick(index) {
      this.evenSelectClick(
        this.protfolioSelectItem,
        this.protfolioSelectOptions,
        [itemTypeEnum.INDICATORGROUP.value]
      );
    },
    //指标、维度、组合点击
    evenSelectClick(selectItem, selectOption, type) {
      //把不是本类型得排除掉，本类型的下边重新加
      this.portfolioData.items = this.portfolioData.items.filter(
        (item) => !type.includes(item.itemType)
      );

      for (let i = 0; i < selectItem.length; i++) {
        //为了防止万一，看其他项里是否有相同objectId的，如果有不加入
        let isExist = false;
        for (let j = 0; j < this.portfolioData.items.length; j++) {
          if (selectItem[i] == this.portfolioData.items[j].objectId) {
            isExist = true;
            break;
          }
        }

        if (!isExist) {
          //筛选出当前选的对象
          let option = selectOption.filter((item) => item.id == selectItem[i]);

          if (option.length > 0) {
            //判断下当前数组中是否有该项，没有则添加
            let obj = this.portfolioData.items.filter(
              (item) => item.objectId == option[0].id
            );

            if (obj.length == 0) {
              //后台给的列表中，没有objectId，所以需要把objectId赋下值，其实objectId就是后台列表的id
              option[0].objectId = option[0].id;
              this.portfolioData.items.push(option[0]);
            }
          }
        }
      }

      for (let i = 0; i < this.portfolioData.items.length; i++) {
        this.portfolioData.items[i].displayOrder = i + 1;
      }

      this.getMetricsPreview();
    },

    //改变排序
    changeSort(row) {
      this.getMetricsPreview();
    },

    //预览
    getMetricsPreview() {
      let data = { items: [] };

      for (let i = 0; i < this.portfolioData.items.length; i++) {
        if (
          this.portfolioData.items[i].itemType ==
          itemTypeEnum.INDICATORGROUP.value
        ) {
          data.items.push({
            itemType: this.portfolioData.items[i].itemType,
            objectId: this.portfolioData.items[i].objectId,
            defaultSort: this.portfolioData.items[i].defaultSort,
            displayName: this.portfolioData.items[i].displayName,
          });
        } else {
          data.items.push({
            itemType: this.portfolioData.items[i].itemType,
            objectId: this.portfolioData.items[i].objectId,
            defaultSort: this.portfolioData.items[i].defaultSort,
          });
        }
      }

      //const loading = this.loadingScreen();
      getMetricsPreviewAPI(data)
        .then((response) => {
          if (response.code == 200) {
            this.metricsDataPreview.columns = [];
            this.metricsDataPreview.records = [];

            this.metricsDataPreview.columns = response.data.columns;
            this.metricsDataPreview.records = response.data.records;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    //获取指标、维度、组合
    getCandidates() {
      let queryParams = { keyword: "", candidateType: "" };
      if (this.portfolioDataItem) {
        queryParams.excludeGroupId = this.portfolioDataItem.id; //避免嵌套
      }
      //const loading = this.loadingScreen();
      getCandidatesAPI(queryParams)
        .then((response) => {
          if (response.code == 200) {
            this.dimensionSelectOptions = response.data.dimensions;
            this.metricSelectOptions = response.data.metrics;
            this.protfolioSelectOptions = response.data.groups;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    //获取详情
    getPortfolioDataDetail() {
      if (this.portfolioDataItem != null) {
        //const loading = this.loadingScreen();
        getPortfolioDataDetailAPI(this.portfolioDataItem.id)
          .then((response) => {
            if (response.code == 200) {
              this.portfolioData = response.data;

              for (let i = 0; i < this.portfolioData.items.length; i++) {
                if (
                  this.portfolioData.items[i].itemType ==
                  itemTypeEnum.DIMENSION.value
                ) {
                  this.dimensionSelectItem.push(
                    this.portfolioData.items[i].objectId
                  );
                }
                if (
                  this.portfolioData.items[i].itemType ==
                    itemTypeEnum.ATOMICMETRIC.value ||
                  this.portfolioData.items[i].itemType ==
                    itemTypeEnum.CALCULATEDMETRIC.value ||
                  this.portfolioData.items[i].itemType ==
                    itemTypeEnum.DERIVEDMETRIC.value
                ) {
                  this.metricSelectItem.push(
                    this.portfolioData.items[i].objectId
                  );
                }

                if (
                  this.portfolioData.items[i].itemType ==
                  itemTypeEnum.INDICATORGROUP.value
                ) {
                  this.protfolioSelectItem.push(
                    this.portfolioData.items[i].objectId
                  );
                }
              }
              this.getMetricsPreview();
            } else {
              //访问失败
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {
            //loading.close();
          });
      }
    },

    newPortfolioData() {
      if (!this.portfolioData.groupName?.toString().trim()) {
        toast.error("组合名称不能为空");
        return;
      }
      if (!this.portfolioData.groupCode?.toString().trim()) {
        toast.error("组合编码不能为空");
        return;
      }

      let tempItems = [];
      for (let i = 0; i < this.portfolioData.items.length; i++) {
        tempItems.push({
          itemType: this.portfolioData.items[i].itemType,
          objectId:
            this.portfolioData.items[i].objectId ||
            this.portfolioData.items[i].id,
          displayName: this.portfolioData.items[i].displayName,
          displayOrder: this.portfolioData.items[i].displayOrder,
          isRequired: this.portfolioData.items[i].isRequired,
          isDefaultVisible: this.portfolioData.items[i].isDefaultVisible,
          defaultSort: this.portfolioData.items[i].defaultSort,
        });
      }

      this.portfolioData.items = tempItems;

      //const loading = this.loadingScreen();
      savePortfolioDataAPI(this.portfolioData)
        .then((response) => {
          if (response.code == 200) {
            //成功
            toast.success("成功");
            this.$emit("sure");
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },
  },
};
</script>


 <style scoped lang="scss">
.select-one ::v-deep .el-tag {
  background: #e6f1ff;
  border-radius: 4px 4px 4px 4px;
}

.select-two ::v-deep .el-tag {
  background: #e6f8ea;
  border-radius: 4px 4px 4px 4px;
}

.select-three ::v-deep .el-tag {
  background: #fef2d2;
  border-radius: 4px 4px 4px 4px;
}

::v-deep .el-select .el-tag__close.el-icon-close {
  background-color: transparent;
}
</style>

