package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据模型列表项
 */
@Data
public class DataModelVO {

    private Long id;

    private String name;

    private String factTableName;

    private Integer dimTableCount;

    private Long sourceId;

    private String sourceName;

    private Integer status;

    private LocalDateTime updatedAt;
}
