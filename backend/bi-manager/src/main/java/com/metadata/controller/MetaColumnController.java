package com.metadata.controller;

import com.common.result.R;
import com.metadata.service.MetaColumnService;
import com.metadata.vo.meta.MetaColumnVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据-字段 Controller
 */
@Tag(name = "已采集元数据")
@RestController
@RequestMapping("/v1/meta/column")
@RequiredArgsConstructor
public class MetaColumnController {

    private final MetaColumnService metaColumnService;

    @Operation(summary = "元数据字段列表", description = "按 meta_table.id 展开字段列表")
    @GetMapping("/list")
    public R<List<MetaColumnVO>> list(@Parameter(description = "元数据表 ID") @RequestParam Long tableId) {
        return R.ok(metaColumnService.listByTableId(tableId));
    }
}
