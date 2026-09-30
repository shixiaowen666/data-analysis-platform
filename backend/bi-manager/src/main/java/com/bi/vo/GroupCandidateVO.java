package com.bi.vo;

import lombok.Data;

@Data
public class GroupCandidateVO {

    private Long id;

    private String name;

    private String typeLabel;

    /**
     * 保存组合时应使用的 itemType
     */
    private String itemType;

    // ---------- 结构配置区（B 区）默认值 ----------

    private String displayName;

    private Integer displayOrder;

    private String itemTypeName;

    private Integer isRequired;

    private Integer isDefaultVisible;

    private String formatType;

    private String unit;

    private String defaultSort;
}
