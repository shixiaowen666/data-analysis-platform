package com.senses.permission.model.vo;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 表行权限配置信息
 *
 * @author wanjie
 * @date 2025-08-27 10:42
 * @version 1.0
 */
@Data
public class TableTagRelationVo {
    private Long id;

    @Schema(description = "条件间关系")
    private String relationType;

    @Schema(description = "数据表ID")
    private Long tableId;

    @Schema(description = "标签关系")
    private List<TagFieldRelationVo> tagFieldRelations;
}
