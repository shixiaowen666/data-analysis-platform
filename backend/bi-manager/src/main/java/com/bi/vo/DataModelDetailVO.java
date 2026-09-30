package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据模型详情
 */
@Data
public class DataModelDetailVO {

    private Long id;

    private String name;

    private String description;

    private Long sourceId;

    private String sourceName;

    private Long factTableId;

    private String factTableName;

    private Integer status;

    private List<DataModelJoinVO> joins;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
