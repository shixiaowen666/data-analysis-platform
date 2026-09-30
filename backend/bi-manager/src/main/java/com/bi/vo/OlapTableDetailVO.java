package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析表详情
 */
@Data
public class OlapTableDetailVO {

    private Long id;

    private Long metaTableId;

    private String dbName;

    private String tbName;

    private String cnName;

    private String tbCnName;

    private String note;

    private String theme;

    private String themeKey;

    private String type;

    private String typeKey;

    private Integer modelType;

    private String tbType;

    private String tbTypeKey;

    private Integer status;

    private Long sourceId;

    private String querySql;

    private String viewSql;

    private Long tenantId;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    /**
     * 字段映射列表
     */
    private List<FieldMappingVO> fields;
}
