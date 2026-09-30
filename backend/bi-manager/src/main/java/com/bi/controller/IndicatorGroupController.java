package com.bi.controller;

import com.bi.dto.IndicatorGroupQueryDTO;
import com.bi.dto.IndicatorGroupSaveDTO;
import com.bi.service.IIndicatorGroupService;
import com.bi.vo.*;
import com.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 指标组合管理
 * <p>
 * Base: /api/v1/indicator-groups
 */
@Slf4j
@Validated
@Tag(name = "指标组合管理")
@RestController
@RequestMapping("/v1/indicator-groups")
@RequiredArgsConstructor
public class IndicatorGroupController {

    private final IIndicatorGroupService indicatorGroupService;

    @Operation(summary = "组合列表", operationId = "indicatorGroupList")
    @GetMapping("/list")
    public R<ApiPageResult<IndicatorGroupVO>> list(@Valid IndicatorGroupQueryDTO query) {
        return R.ok(indicatorGroupService.listGroups(query, getCurrentTenantId()));
    }

    @Operation(summary = "字段候选列表", operationId = "indicatorGroupCandidates")
    @GetMapping("/candidates")
    public R<GroupCandidatesVO> candidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String candidateType,
            @RequestParam(required = false) Long excludeGroupId) {
        return R.ok(indicatorGroupService.listCandidates(keyword, candidateType, excludeGroupId, getCurrentTenantId()));
    }

    @Operation(summary = "组合详情", operationId = "indicatorGroupDetail")
    @GetMapping("/{id}/detail")
    public R<IndicatorGroupDetailVO> detail(@PathVariable Long id) {
        return R.ok(indicatorGroupService.getDetail(id, getCurrentTenantId()));
    }

    @Operation(summary = "保存组合", description = "id 为空新建，非空编辑；保存后直接上线 status=2")
    @PostMapping("/save")
    public R<GroupSaveResultVO> save(@Valid @RequestBody IndicatorGroupSaveDTO dto) {
        return R.ok(indicatorGroupService.saveGroup(dto, getCurrentTenantId()));
    }

    @Operation(summary = "上线组合", description = "审批通过后上线，仅 status=1 可操作")
    @PostMapping("/{id}/online")
    public R<GroupSaveResultVO> online(@PathVariable Long id) {
        return R.ok(indicatorGroupService.onlineGroup(id, getCurrentTenantId()));
    }

    @Operation(summary = "下线组合", operationId = "indicatorGroupOffline")
    @PostMapping("/{id}/offline")
    public R<Void> offline(@PathVariable Long id) {
        indicatorGroupService.offlineGroup(id, getCurrentTenantId());
        return R.ok();
    }

    @Operation(summary = "删除组合", operationId = "indicatorGroupDelete")
    @GetMapping("/{id}/delete")
    public R<Void> delete(@PathVariable Long id) {
        indicatorGroupService.deleteGroup(id, getCurrentTenantId());
        return R.ok();
    }


    private Long getCurrentTenantId() {
        return 1L;
    }
}
