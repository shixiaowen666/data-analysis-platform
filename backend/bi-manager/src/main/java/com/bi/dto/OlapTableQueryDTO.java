package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 分析表列表查询
 */
@Data
public class OlapTableQueryDTO {

    /**
     * 数据源 ID
     */
    private Long sourceId;

    /**
     * 表名关键词
     */
    private String keyword;

    /**
     * 存储类型：0-物理表 1-视图
     */
    private String tbTypeKey;

    /**
     * 业务类型 key：fact/dim
     */
    private String typeKey;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否已注册
     */
    private Boolean registered;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer page;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为 1")
    private Integer pageSize;
}
