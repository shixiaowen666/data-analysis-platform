package com.bi.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 语义映射批量保存
 */
@Data
public class SemanticMappingSaveDTO {

    @NotNull(message = "表 ID 不能为空")
    private Long tableId;

    private String cnName;

    private String note;

    private String viewSql;
    /**
     * 业务类型 key：fact/dim
     */
    private String typeKey;

//    @NotEmpty(message = "请配置至少一个字段映射")
    private List<FieldMappingItemDTO> fields;
}
