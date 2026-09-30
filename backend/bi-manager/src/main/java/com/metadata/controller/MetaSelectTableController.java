package com.metadata.controller;

import com.common.result.R;
import com.metadata.service.MetaSelectTableService;
import com.metadata.vo.meta.MetaSelectTableVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源选中表 Controller
 */
@Tag(name = "元数据采集")
@RestController
@RequestMapping("/v1/meta/select-table")
@RequiredArgsConstructor
public class MetaSelectTableController {

    private final MetaSelectTableService metaSelectTableService;

    @Operation(summary = "查询已选中的表",
            description = "从 meta_select_table 读取，用于选表采集弹窗回显；选中表在点击「开始采集」时自动保存")
    @GetMapping("/list")
    public R<MetaSelectTableVO> list(@Parameter(description = "数据源 ID") @RequestParam Integer datasourceId) {
        return R.ok(metaSelectTableService.listSelected(datasourceId));
    }
}
