package com.bi.vo;

import lombok.Data;

import java.util.List;

/**
 * 接口文档分页结构（list / total / page / pageSize）
 */
@Data
public class ApiPageResult<T> {

    private List<T> list;
    private Long total;
    private Long page;
    private Long pageSize;

    public ApiPageResult() {
    }

    public ApiPageResult(List<T> list, Long total, Long page, Long pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
