package com.chatbi.chat.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 指标和视角实体
 * Created by zjd on 2026/6/18 10:31
 */

@Data
public class IndexDTO implements Serializable {

    @Schema(description ="指标id")
    private Long id;

    @Schema(description ="指标英文名")
    private String indKey;

    @Schema(description ="指标中文名")
    private String indName;

    /**
     * 格式化和单位
     *
     * @see
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description ="格式化和单位,0-无 1-原始值 2-%（计算） 4-千 5-万 6-% 7-时分秒 8-时分秒舍去0时")
    private Integer fieldStyle;

    /**
     * 小数点位数
     *
     * @see
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description ="小数点位数 -1: -1，0: 0，1: 0.0，2: 0.00 ")
    private Integer decimalPoint;

}
