package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * olap 表格数据请求实体
 * Created by zjd on 2026/6/18 20:04
 */

@Data
public class DataRequestDTO  {

    @Schema(description = "下载开关，0：查询；1：下载")
    @NotNull(message = "下载开关不能为空")
    private Integer downloadFlag;

    @Schema(description = "维度")
    @NotEmpty(message = "筛选条件不能为空")
    @Valid
    private List<DimDTO> dimList;

    @Schema(description = "指标")
    @NotEmpty(message = "指标不能为空")
    @Valid
    private List<IndexDTO> indexList;

    @Schema(description = "图表类型 1-表格 2-柱状图 3-折线图 4-饼图 5-指标卡 6-交叉表")
    private Integer chartType = 1;


    @Schema(description = "排序集合")
    @Valid
    private List<OrderByDTO> orderList;

    @Schema(description = "TOPN配置")
    private TopnDto top;

    @Schema(description = "过滤条件")
    @Valid
    private List<FilterDTO> filters;

    @Schema(description = "日期粒度 day/week/month/quarter/year，默认day")
    private String dateGranularity = "day";

    @Schema(description = "指标对比类型映射: indicatorId → none/pop/yoy/both，不传/null 表示全不对比")
    private Map<Long, String> indicatorComparison;

    @Schema(description = "时间范围")
    private TimeRangeDTO timeRange;

    @Data
    public static class TimeRangeDTO {
        private String start;
        private String end;
    }
    @Schema(description = "当前页数")
    private Integer page = 1;

    @Schema(description = "分页数量")
    private Integer pageSize = 20;
}

