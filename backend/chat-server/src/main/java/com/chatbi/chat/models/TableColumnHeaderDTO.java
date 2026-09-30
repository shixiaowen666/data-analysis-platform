package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;

/**
 * 表格列头实体
 *
 * @author jixk
 * @version v1.0.0
 * @date 2019-11-25 16:31
 */
@Data
public class TableColumnHeaderDTO implements Serializable {

    /**
     * 对应字段id
     */
    private Long id;
    /**
     * 对应字段key
     */
    private String key;

    /**
     * 对应字段显示名称
     */
    private String value;

    /**
     * 序号
     */
    private Integer sortId;

    private Boolean isShow;
    /**
     * 指标隐藏标识
     */
    private Boolean hidden;

    private Boolean dimFlag;
    /**
     * 是否支持排序
     */
    private Boolean sortFlag;

    private String fieldExplain;

    private Integer fieldClazz;

    /**
     * 是否为对比指标标志
     * true:是，false: 否
     */
    private Boolean isCompare;

    /**
     * 0 未知，1 维度， 2 指标
     */
    private Integer catalog;

//    @ApiModelProperty(value = "下钻维度")
//    private OlapDimensionDTO drillDownDimension;

    /**
     * 时间类型标志：1:是； 2:否
     * 兼容 数据大盘的 字段
     */
    private Integer dateDimFlag;

}
