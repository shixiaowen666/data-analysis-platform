package com.quality.dto;

import lombok.Data;

/** 通用分页筛选 */
@Data
public class PageQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String aiBodyCode;
    private String keyword;
    private String errorType;
    private Integer status;
    private String statusStr;
    /** 诊断列表快捷筛选：feedback / failed / undiagnosed */
    private String quick;
    private String startTime;
    private String endTime;
}
