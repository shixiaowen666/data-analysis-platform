package com.metadata.dto.meta;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 采集日志列表查询参数
 */
@Data
@Schema(description = "采集日志列表查询参数")
public class MetaCollectLogQueryDTO {

    @Schema(description = "数据源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceId;

    @Schema(description = "采集状态：RUNNING / SUCCESS / FAIL")
    private String status;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}
