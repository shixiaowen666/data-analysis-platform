package com.bi.controller;

import com.bi.dto.CompatibleFieldQueryDTO;
import com.bi.dto.DimMetricQueryVO;
import com.bi.service.IReportPreviewService;
import com.bi.vo.CompatibleFieldsVO;
import com.bi.vo.DimMetricTreeDTO;
import com.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 指标数据预览
 * <p>
 * Base: /api/v1/report-preview
 */
@Validated
@Tag(name = "指标数据预览")
@RestController
@RequestMapping("/v1/report-preview")
@RequiredArgsConstructor
public class ReportPreviewController {

    private final IReportPreviewService reportPreviewService;

    @Operation(summary = "指标维度互滤候选列表",
            description = "传入已选 metricIds 过滤可用维度；传入 dimensionIds 过滤可用指标；均不传则返回全部已上线字段")
    @GetMapping("/compatible-fields")
    public R<CompatibleFieldsVO> compatibleFields(CompatibleFieldQueryDTO query) {
        return R.ok(reportPreviewService.listCompatibleFields(query, getCurrentTenantId()));
    }


    @Operation(summary = "维度指标树", description = "报表设计器左侧数据源树；keyword 搜中文名")
    @PostMapping("/dim-metric")
    public R<List<DimMetricTreeDTO>> fieldTree(@Valid @RequestBody DimMetricQueryVO queryVo) {
        return R.ok(reportPreviewService.dimAndMetric(queryVo, getCurrentTenantId()));
    }

    private Long getCurrentTenantId() {
        return 1L;
    }
}
