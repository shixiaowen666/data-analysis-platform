package com.metadata.dto.meta;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 远程库表分页查询参数
 */
@Data
@Schema(description = "远程库表分页查询参数")
public class RemoteTableQueryDTO {

    @Schema(description = "数据源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceId;

    @Schema(description = "表名模糊搜索")
    private String tableName;

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    public int resolvedPage() {
        return page != null && page > 0 ? page : 1;
    }

    public int resolvedPageSize() {
        return pageSize != null && pageSize > 0 ? pageSize : 10;
    }
}
