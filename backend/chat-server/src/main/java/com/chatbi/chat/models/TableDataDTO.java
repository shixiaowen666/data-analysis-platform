package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TableDataDTO extends ChartBaseDTO {

    @Schema(description = "总数量")
    private Long total;

    @Schema(description = "表格title列表")
    private List<TableColumnHeaderDTO> titleMap;

    @Schema(description = "表格数据列表")
    private List<Map<String, Object>> dataList;

//    @Schema(description = "着色数据信息")
//    private List<ColorDataInfo> colorDataInfoList;
//
//    @Schema(description = "表头颜色")
//    private TableKeyColorInfo titleColorConfig;
//
//    @Schema(description = "合并单元格")
//    private TableKeyMergeInfo cellMergeConfig;
}
