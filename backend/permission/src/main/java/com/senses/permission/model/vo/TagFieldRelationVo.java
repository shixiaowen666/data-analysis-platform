package com.senses.permission.model.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 标签字段关系表;
 * @author : wanjie
 * @date : 2025-08-26
 */
@Data
public class TagFieldRelationVo {

    /** 标签ID */
    @Schema(description = "标签ID")
    private Long tagId;

    /** 标签名称 */
    @Schema(description = "标签名称")
    private String tagName;


    /** 表名 */
    @Schema(description = "表名")
    private String tableName;

    /** 字段名 */
    @Schema(description = "字段名")
    private String columnName;

}
