package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DateDimTypeDTO implements Serializable {

    @Schema(description = "时间颗粒度  1：实时时段（选粒度）  htime  2：离线（选粒度） ptdate 3：实时累计  stime  4：实时时段（不选粒度）")
    private Integer timeType;

    @Schema(description = "时间筛选器类型 ")
    private String timeFilterType;

    @Schema(description = "时间，开始和结束日期")
    private List<Object> data;
}
