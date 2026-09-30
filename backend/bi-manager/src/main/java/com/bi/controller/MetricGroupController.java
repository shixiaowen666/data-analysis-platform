package com.bi.controller;

import com.bi.dto.MetricGroupSaveDTO;
import com.bi.service.IMetricGroupService;
import com.bi.vo.ApiPageResult;
import com.bi.vo.MetricGroupVO;
import com.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 指标组合管理（数据预览保存的组合）
 * <p>
 * Base: /api/v1/report_groups
 */
@Validated
@Tag(name = "指标组合管理")
@RestController
@RequestMapping("/v1/report_groups")
@RequiredArgsConstructor
public class MetricGroupController {

    private final IMetricGroupService metricGroupService;

    @Operation(summary = "保存指标组合", description = "id 为空新建，非空编辑；groupConfig 为数据预览查询上下文 JSON")
    @PostMapping("/save")
    public R<Long> save(@Valid @RequestBody MetricGroupSaveDTO dto) {
        return R.ok(metricGroupService.save(dto, getCurrentTenantId()));
    }

    @Operation(summary = "指标组合分页列表", description = "keyword 搜组合名称/编码；status 0-草稿 1-审批中 2-已上线 3-已下线")
    @GetMapping("/list")
    public R<ApiPageResult<MetricGroupVO>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer pageSize) {
        return R.ok(metricGroupService.listGroups(keyword, status, page, pageSize, getCurrentTenantId()));
    }

    @Operation(summary = "指标组合详情", description = "编辑时跳转数据预览页，用 groupConfig 回填查询条件")
    @GetMapping("/{id}")
    public R<MetricGroupVO> detail(@PathVariable Long id) {
        return R.ok(metricGroupService.getDetail(id, getCurrentTenantId()));
    }

    @Operation(summary = "上线指标组合")
    @PostMapping("/{id}/online")
    public R<Void> online(@PathVariable Long id) {
        metricGroupService.online(id, getCurrentTenantId());
        return R.ok();
    }

    @Operation(summary = "下线指标组合")
    @PostMapping("/{id}/offline")
    public R<Void> offline(@PathVariable Long id) {
        metricGroupService.offline(id, getCurrentTenantId());
        return R.ok();
    }

    @Operation(summary = "删除指标组合", description = "仅草稿/审批中/已下线可删除")
    @GetMapping("/{id}/delete")
    public R<Void> delete(@PathVariable Long id) {
        metricGroupService.delete(id, getCurrentTenantId());
        return R.ok();
    }

    private Long getCurrentTenantId() {
        return 1L;
    }
}
