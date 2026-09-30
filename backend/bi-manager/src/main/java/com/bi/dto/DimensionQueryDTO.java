package com.bi.dto;

import lombok.Data;

/**
 * 维度列表查询参数
 */
@Data
public class DimensionQueryDTO {

    /**
     * 按维度名称模糊匹配
     */
    private String keyword;

    /**
     * 类型筛选：1-标准维 2-杂项维
     */
    private Integer dimensionType;

    /**
     * 状态筛选：0-草稿 1-审批中 2-已上线 3-已下线
     */
    private Integer status;

    private Integer page = 1;

    private Integer pageSize = 10;
}
