package com.metadata.vo.meta;

import lombok.Data;

/**
 * 启动采集响应
 */
@Data
public class MetaCollectStartVO {

    private Long collectLogId;

    private Long sourceId;

    private String status;

    private String message;
}
