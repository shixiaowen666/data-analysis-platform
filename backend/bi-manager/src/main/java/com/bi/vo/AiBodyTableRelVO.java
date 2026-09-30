package com.bi.vo;

import lombok.Data;

/**
 * 智能体关联数据表
 */
@Data
public class AiBodyTableRelVO {

    private Long id;

    /**
     * 数据源ID
     */
    private Long sourceId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释
     */
    private String tableComment;

    /**
     * 关联类型 0-关联事实表 1-关联分析模型
     */
    private Integer relationType;
}
