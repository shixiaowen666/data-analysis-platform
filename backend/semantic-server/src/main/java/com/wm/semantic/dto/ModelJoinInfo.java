package com.wm.semantic.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型 join 关系信息
 *
 * 表示一对 (factTable, dimTable) 之间的 join 配置。
 * 对应 olap_fact_dim_mapping_pro 表中按 (fact_tb_id, dim_tb_id) 聚合后的多行复合关联键。
 */
@Data
public class ModelJoinInfo {

    /** 事实表ID。 */
    private Long factTableId;
    /** 事实表所属库名。 */
    private String factDbName;
    /** 事实表名。 */
    private String factTableName;

    /** 维度表ID。 */
    private Long dimTableId;
    /** 维度表所属库名。 */
    private String dimDbName;
    /** 维度表名。 */
    private String dimTableName;

    /** 关联类型：LEFT JOIN / INNER JOIN / RIGHT JOIN */
    private String joinType;

    /** 复合关联键列表，多对 (factFieldKey, dimFieldKey) 用 AND 串接。 */
    private List<JoinKeyPair> joinKeys = new ArrayList<JoinKeyPair>();

    /**
     * 单对 join key 关联。
     */
    @Data
    public static class JoinKeyPair {
        /** 事实表关联字段名（物理列名）。 */
        private String factFieldKey;
        /** 维度表关联字段名（物理列名）。 */
        private String dimFieldKey;
    }
}
