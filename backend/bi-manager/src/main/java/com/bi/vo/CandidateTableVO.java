package com.bi.vo;

import lombok.Data;

/**
 * 可选表信息（数据源下的表列表）
 */
@Data
public class CandidateTableVO {

    private Long tableId;
    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释
     */
    private String tableComment;
}
