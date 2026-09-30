package com.metadata.vo.meta;

import lombok.Data;

/**
 * 采集日志条目 VO
 */
@Data
public class CollectLogEntryVO {

    private String time;

    private String level;

    private String msg;
}
