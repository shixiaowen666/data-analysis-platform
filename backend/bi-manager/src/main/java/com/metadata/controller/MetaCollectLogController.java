package com.metadata.controller;

import com.common.result.PageResult;
import com.common.result.R;
import com.metadata.dto.meta.MetaCollectLogQueryDTO;
import com.metadata.service.MetaCollectLogService;
import com.metadata.vo.meta.MetaCollectLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采集日志 Controller
 */
@Tag(name = "元数据采集")
@RestController
@RequestMapping("/v1/meta/collect-log")
@RequiredArgsConstructor
public class MetaCollectLogController {

    private final MetaCollectLogService metaCollectLogService;

    @Operation(summary = "采集日志分页列表", description = "JSON Body：sourceId、status、page、pageSize")
    @PostMapping("/page")
    public R<PageResult<MetaCollectLogVO>> page(@RequestBody MetaCollectLogQueryDTO query) {
        return R.ok(metaCollectLogService.pageList(query));
    }

    @Operation(summary = "采集日志详情", description = "返回终端风格步骤日志 logLines")
    @GetMapping("/{id}")
    public R<MetaCollectLogVO> detail(@Parameter(description = "采集日志 ID") @PathVariable Long id) {
        return R.ok(metaCollectLogService.getDetail(id));
    }
}

