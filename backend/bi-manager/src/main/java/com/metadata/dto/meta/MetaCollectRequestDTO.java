package com.metadata.dto.meta;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 元数据采集请求
 */
@Data
@Schema(description = "元数据采集请求")
public class MetaCollectRequestDTO {

    @Schema(description = "采集方式：full-全库采集，select-选表采集", example = "select", requiredMode = Schema.RequiredMode.REQUIRED)
    private String collectType;

    @Schema(description = "选表采集时勾选的表名，点击采集时自动保存；不传则读取上次已保存的选中表",
            example = "[\"fact_operation_daily\",\"dim_department\"]")
    private List<String> tableNames;
}
