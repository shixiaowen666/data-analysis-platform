package com.metadata.controller;

import com.common.result.PageResult;
import com.common.result.R;
import com.metadata.dto.meta.MetaTableQueryDTO;
import com.metadata.service.MetaTableService;
import com.metadata.vo.meta.MetaTableVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元数据-表 Controller
 */
@Tag(name = "已采集元数据")
@RestController
@RequestMapping("/v1/meta/table")
@RequiredArgsConstructor
public class MetaTableController {

    private final MetaTableService metaTableService;

    @Operation(summary = "元数据表分页列表", description = "JSON Body：sourceId、tableName、page、pageSize")
    @PostMapping("/page")
    public R<PageResult<MetaTableVO>> page(@RequestBody MetaTableQueryDTO query) {
        return R.ok(metaTableService.pageList(query));
    }
}

