package com.bi.vo;

import lombok.Data;

/**
 * 未注册表列表项
 */
@Data
public class UnregisteredTableVO {

    private Long tableId;

    /**
     * 表英文名
     */
    private String tableName;

    /**
     * 表中文名
     */
    private String tableComment;

    /**
     * 是否已注册 true-已注册
     */
    private Boolean registered;
}
