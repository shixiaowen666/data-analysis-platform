package com.bi.controller;

import com.bi.dto.DataModelQueryDTO;
import com.bi.dto.DataModelSaveDTO;
import com.bi.service.IDataModelService;
import com.bi.vo.*;
import com.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 数据模型管理
 * <p>
 * Base: /api/v1/data-models
 */
@Slf4j
@Validated
@Tag(name = "数据模型管理")
@RestController
@RequestMapping("/v1/data-models")
@RequiredArgsConstructor
public class DataModelController {

    private final IDataModelService dataModelService;

    @Operation(summary = "模型列表", operationId = "dataModelList")
    @GetMapping
    public R<ApiPageResult<DataModelVO>> list(@Valid DataModelQueryDTO query) {
        return R.ok(dataModelService.listModels(query, getCurrentTenantId()));
    }

    @Operation(summary = "模型详情", operationId = "dataModelDetail")
    @GetMapping("/{id}")
    public R<DataModelDetailVO> detail(@PathVariable Long id) {
        return R.ok(dataModelService.getDetail(id, getCurrentTenantId()));
    }

    @Operation(summary = "新建模型", operationId = "dataModelCreate")
    @PostMapping
    public R<IdVO> create(@Valid @RequestBody DataModelSaveDTO dto) {
        Long id = dataModelService.createModel(dto, getCurrentTenantId());
        return R.ok(new IdVO(id));
    }

    @Operation(summary = "编辑模型", operationId = "dataModelUpdate")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DataModelSaveDTO dto) {
        dataModelService.updateModel(id, dto, getCurrentTenantId());
        return R.ok();
    }

    @Operation(summary = "删除模型", operationId = "dataModelDelete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dataModelService.deleteModel(id, getCurrentTenantId());
        return R.ok();
    }

    @Operation(summary = "可选主表列表", operationId = "dataModelCandidateFactTables")
    @GetMapping("/candidate-fact-tables")
    public R<List<CandidateTableVO>> candidateFactTables(@RequestParam Long sourceId) {
        return R.ok(dataModelService.listCandidateFactTables(sourceId, getCurrentTenantId()));
    }

    @Operation(summary = "可选关联表及字段", operationId = "dataModelCandidateDimTables")
    @GetMapping("/candidate-dim-tables")
    public R<CandidateDimTablesVO> candidateDimTables(
            @RequestParam Long sourceId,
            @RequestParam(required = false) Long factTableId) {
        return R.ok(dataModelService.listCandidateDimTables(sourceId, factTableId, getCurrentTenantId()));
    }

    private Long getCurrentTenantId() {
        // TODO: 从 SecurityContext / Header 获取当前租户
        return 1L;
    }
}
