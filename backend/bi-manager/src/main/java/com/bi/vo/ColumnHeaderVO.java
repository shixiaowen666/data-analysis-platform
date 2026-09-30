package com.bi.vo;

import lombok.Data;

/**
 * 表格列头
 */
@Data
public class ColumnHeaderVO {

    private String key;

    private String title;

    private Integer width;
}
