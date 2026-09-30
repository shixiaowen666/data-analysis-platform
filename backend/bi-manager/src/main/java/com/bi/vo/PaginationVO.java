package com.bi.vo;

import lombok.Data;

/**
 * 分页信息
 */
@Data
public class PaginationVO {

    private Long current;

    private Long pageSize;

    private Long total;
}
