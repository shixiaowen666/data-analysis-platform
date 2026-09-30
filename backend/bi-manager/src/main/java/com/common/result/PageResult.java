package com.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long total;
    private List<T> records;
    private Long page;
    private Long pageSize;

    public PageResult() {}

    public PageResult(Long total, List<T> records, Long page, Long pageSize) {
        this.total = total;
        this.records = records;
        this.page = page;
        this.pageSize = pageSize;
    }

    @SuppressWarnings("unchecked")
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getTotal(),
                page.getRecords(),
                page.getCurrent(),
                page.getSize()
        );
    }
}
