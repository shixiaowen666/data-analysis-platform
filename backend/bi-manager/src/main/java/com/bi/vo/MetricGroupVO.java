package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标组合 VO
 */
@Data
public class MetricGroupVO {

    private Long id;

    private String groupCode;

    private String groupName;

    private String description;

    private String groupConfig;

    private Integer status;

    private String statusName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
