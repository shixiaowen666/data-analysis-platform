package com.metadata.engine.model;

import lombok.Data;

/**
 * 采集日志条目（与原型 HTML 详情格式一致）
 */
@Data
public class CollectLogEntry {

    private String time;

    private String level;

    private String msg;
}
