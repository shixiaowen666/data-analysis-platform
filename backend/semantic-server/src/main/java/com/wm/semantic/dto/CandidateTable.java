package com.wm.semantic.dto;

import lombok.Data;

/**
 * 候选数据源信息。
 */
@Data
public class CandidateTable {
    /** 数据源主键ID；type=table 时为表ID，type=model 时为模型ID。 */
    private Long id;
    /** 数据源名称，兼容现有候选查询结果映射。 */
    private String name;
    /** 数据源类型：table-物理表，model-模型。 */
    private String type;
    /** 物理表名，type=table 时使用。 */
    private String tableName;
    /** 模型ID，type=model 时使用。 */
    private Long modelId;
}
