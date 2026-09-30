package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartBaseDTO implements Serializable {

    @Schema(description = "图表id")
    private Long chartId;

    @Schema(description = "是否缓存数据")
    private Boolean cacheData;

    @Schema(description = "图表查询sql")
    private String sql;

//    @Schema(description = "可视化配置信息")
//    private VisualizationInfo visualizationInfo;

    @Schema(description = "minio数据路径")
    private String minioFilePath;

    private String factIds;

    private Integer dataNum;
}
