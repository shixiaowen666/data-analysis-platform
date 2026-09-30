<template>
  <div
    ref="lineChart"
    :style="`min-width:98%;width: ${chartWidth}px; height: ${chartHeight}px;`"
  ></div>
</template>

<script>
// 引入 ECharts 主模块
import * as echarts from "echarts";

export default {
  name: "lineChart",
  props: ["chartHeight", "previewData"],
  data() {
    return {
      dataChart: null,
      chartWidth: 0,

      chartDataTemp: {
        title: "",
        legend: [],
        xData: [],
        yData: [{ name: "", data: [] }],
      },
    };
  },
  mounted() {
    //this.initChart();
    window.addEventListener("resize", this.resize);
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.resize);
    if (this.dataChart != null) {
      this.dataChart.dispose();
      this.dataChart = null;
    }
  },
  methods: {
    initChart() {
      this.parseData();

      // 基于准备好的dom，初始化echarts实例
      if (this.dataChart != null) {
        this.dataChart.dispose();
        this.dataChart = null;
      }
      this.dataChart = echarts.init(this.$refs.lineChart);

      const option = {
        title: {
          text: this.chartData.title,
        },
        tooltip: {
          trigger: "axis",
          axisPointer: { type: "shadow" },

          formatter: (params) => {
            let str = "";
            for (let i = 0; i < params.length; i++) {
              let p = params[i];
              const seriesIndex = p.seriesIndex; // 取第一个系列索引
              const series = this.dataChart.getModel().getSeries()[seriesIndex]; // 获取系列模型
              const unit = series.get("unit");

              str += p.marker + p.seriesName + ":" + p.value + unit + "<br/>";
            }
            str += `<strong>${params[0].axisValue
              .split("\n")
              .join("<br/>")}</strong>`;
            return str;
          },
        },
        legend: {
          top: "top",
          data: this.chartData.legend, //["线上", "线下", "其他"],
        },
        grid: {
          left: "0%",
          right: "0%",
          bottom: "0%",

          containLabel: true,
        },

        dataZoom: [
          {
            type: "inside",
            xAxisIndex: [0],
            zoomLock: false,
          },
        ],
        xAxis: {
          type: "category",
          data: this.chartData.xData, //["一月", "二月", "三月", "四月", "五月"],
          axisLabel: {
            rotate: 45, // 旋转角度（度），正值顺时针旋转
            interval: 0, // 强制显示所有标签
            margin: 20,
          },
        },
        axisLabel: {
          formatter: function (value) {
            if (typeof value === "string") {
              let str = "";
              let list = value.split("\n");

              for (let i = 0; i < list.length; i++) {
                if (list[i].length > 6) {
                  str += list[i].substring(0, 5) + "..." + "\n";
                } else {
                  str += list[i] + "\n";
                }
              }

              return str;
            }
            return value;
          },
        },
        yAxis: {
          type: "value",
        },
        series: [],
        barCategoryGap: "20px",
      };
      let width1 = 150;
      let width2 = 150;

      for (let i = 0; i < this.chartData.yData.length; i++) {
        let obj = {
          name: this.chartData.yData[i].name,
          unit: this.chartData.yData[i].unit,
          type: "bar",
          barWidth: 20,

          //barGap: 10,
          data: this.chartData.yData[i].data, //[120, 132, 101, 134, 90, 230, 210]
        };
        width1 +=
          this.chartData.yData[i].data.length * 20 +
          (this.chartData.yData[i].data.length - 1) * 20;

        if (i == 0)
          width2 +=
            this.chartData.xData[0].split("\n").length *
              12 *
              this.chartData.xData.length +
            (this.chartData.yData[i].data.length - 1) * 20;

        option.series.push(obj);
      }

      this.chartWidth = Math.max(width1, width2);

      if (this.dataChart != null) {
        // 使用刚指定的配置项和数据显示图表。
        this.dataChart.setOption(option);

        this.dataChart.resize();
      }
    },

    parseData() {
      this.chartData = { title: "", legend: [], xData: [], yData: [] };

      let dimList = this.previewData.columns.filter(
        (item) => item.type == "dimension"
      );
      let indList = this.previewData.columns.filter(
        (item) => item.type == "indicator"
      );

      for (let i = 0; i < this.previewData.records.length; i++) {
        let xData = "";
        for (let j = 0; j < dimList.length; j++) {
          xData += "\n" + this.previewData.records[i][dimList[j].key];
        }
        this.chartData.xData.push(xData.slice(1));
      }

      for (let j = 0; j < indList.length; j++) {
        this.chartData.legend.push(indList[j].name);
        let obj = { name: indList[j].name, unit: indList[j].unit, data: [] };

        for (let i = 0; i < this.previewData.records.length; i++) {
          obj.data.push(this.previewData.records[i][indList[j].key]);
        }
        this.chartData.yData.push(obj);
      }
    },

    resize() {
      if (this.dataChart != null) {
        this.dataChart.resize();
      }
    },
  },
};
</script>