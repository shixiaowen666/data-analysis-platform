<template>
  <div ref="pieChart" :style="`width: 98%; height: ${chartHeight}px;`"></div>
</template>

<script>
// 引入 ECharts 主模块
import * as echarts from "echarts";

export default {
  name: "PieChart",
  props: ["chartHeight", "previewData"],
  data() {
    return {
      dataChart: null,
      chartData: null,

      serie: {
        type: "pie",
        radius: ["0%", "80%"],
        label: {
          show: true,
          position: "outside",

          formatter: function (params) {
            // params 是当前数据项的信息
            let str = "";
            let list = params.name.split("\n");

            for (let i = 0; i < list.length; i++) {
              if (list[i].length > 6) {
                str += list[i].substring(0, 5) + "..." + "\n";
              } else {
                str += list[i] + "\n";
              }
            }

            return str;
          },
        },

        data: null,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: "rgba(0, 0, 0, 0.5)",
          },
        },
      },

      chartDataTemp: {
        title: "",
        data: [],
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

      this.dataChart = echarts.init(this.$refs.pieChart);

      // 指定图表的配置项和数据
      const option = {
        title: {
          text: this.chartData.title,
          left: "center",
        },
        tooltip: {
          trigger: "item",

          axisPointer: { type: "shadow" },

          formatter: (params) => {
            // params 是包含当前数据项信息的对象
            // 返回 HTML 字符串

            let str = `<strong>${params.data.showName}:${params.value}${params.data.unit}<br/>占比：${params.percent}%</strong> <br/>`;
            str += `<span style='color:${params.color}'>${params.name
              .split("\n")
              .join("<br/>")}</span>`;
            return str;
          },
        },

        legend: {
          orient: "vertical",
          left: "left",
          //orient: "horizontal",
          top: "5%",
          type: "scroll", // 启用滚动
          height: "90%",

          textStyle: {
            padding: [10, 0, 10, 0],
            backgroundColor: "transparent", // 关键：必须设置背景色
          },

          pageButtonItemGap: 5, // 按钮与图例项间隔
          pageIconColor: "#2f4554", // 按钮颜色
          pageIconInactiveColor: "#aaa", // 按钮不可用颜色
          pageIconSize: 15, // 按钮大小
          pageTextStyle: {
            // 页码文字样式
            color: "#333",
          },
        },

        series: [],
      };

      let step = 0;
      let radius = 80;
      if (this.chartData.data.length == 1) {
        step = 0;
        radius = 80;
      } else {
        radius = 80 / this.chartData.data.length;
        step = radius / 2;
      }

      for (let i = 0; i < this.chartData.data.length; i++) {
        let obj = JSON.parse(JSON.stringify(this.serie));
        obj.data = this.chartData.data[i].data;

        if (i == this.chartData.data.length - 1) {
          for (let j = 0; j < this.chartData.data[i].data.length; j++) {
            this.$set(
              this.chartData.data[i].data[j],
              "showName",
              this.chartData.data[i].name
            );
          }
        } else {
          for (let j = 0; j < this.chartData.data[i].data.length; j++) {
            this.$set(this.chartData.data[i].data[j], "label", { show: false });
            this.$set(
              this.chartData.data[i].data[j],
              "showName",
              this.chartData.data[i].name
            );
          }
        }

        if (i == 0) {
          obj.radius = [`${radius * i}%`, `${radius * (i + 1)}%`];
        } else {
          obj.radius = [`${radius * i + step}%`, `${radius * (i + 1)}%`];
        }
        option.series.push(obj);
      }

      if (this.dataChart != null) {
        // 使用刚指定的配置项和数据显示图表。
        this.dataChart.setOption(option);
        this.dataChart.resize();
      }
    },

    parseData() {
      this.chartData = { title: "", data: [] };

      let dimList = this.previewData.columns.filter(
        (item) => item.type == "dimension"
      );
      let indList = this.previewData.columns.filter(
        (item) => item.type == "indicator"
      );
      for (let j = 0; j < indList.length; j++) {
        let obj = { name: "", data: [] };

        obj.name = indList[j].name;
        for (let i = 0; i < this.previewData.records.length; i++) {
          let xData = "";
          for (let j = 0; j < dimList.length; j++) {
            xData += "\n" + this.previewData.records[i][dimList[j].key];
          }

          obj.data.push({
            name: xData.slice(1),
            value: this.previewData.records[i][indList[j].key],
            unit: indList[j].unit,
          });
        }

        this.chartData.data.push(obj);
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