package com.bi.controller;

import com.bi.dto.*;
import com.bi.service.IFieldMappingService;
import com.bi.vo.*;
import com.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 字段映射管理
 * <p>
 * Base: /api/v1/field-mappings
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/field-mappings")
@RequiredArgsConstructor
public class FieldMappingController {

    private final IFieldMappingService fieldMappingService;

    // ===================== F1: 分析表列表 =====================

    /**
     * 分析表列表（分页）
     */
    @GetMapping("/tables")
    public R<ApiPageResult<OlapTableVO>> listTables(@Valid OlapTableQueryDTO query) {
        return R.ok(fieldMappingService.listTables(query, getCurrentTenantId()));
    }

    /**
     * 分析表详情（含字段映射）
     */
    @GetMapping("/tables/{id}")
    public R<OlapTableDetailVO> tableDetail(@PathVariable Long id) {
        return R.ok(fieldMappingService.getTableDetail(id, getCurrentTenantId()));
    }

    // ===================== F2: 注册物理表 =====================

    /**
     * 查询未注册表列表
     */
    @GetMapping("/tables/unregistered")
    public R<List<UnregisteredTableVO>> unregisteredTables(@RequestParam Long sourceId) {
        return R.ok(fieldMappingService.listUnregisteredTables(sourceId, getCurrentTenantId()));
    }

    /**
     * 注册物理表
     */
    @PostMapping("/tables/register-physical")
    public R<IdVO> registerPhysicalTable(@Valid @RequestBody PhysicalTableRegisterDTO dto) {
        Long id = fieldMappingService.registerPhysicalTable(dto, getCurrentTenantId());
        return R.ok(new IdVO(id));
    }

    // ===================== F3: 注册视图 =====================

    /**
     * 注册视图
     */
    @PostMapping("/tables/register-view")
    public R<IdVO> registerView(@Valid @RequestBody ViewRegisterDTO dto) {
        Long id = fieldMappingService.registerView(dto, getCurrentTenantId());
        return R.ok(new IdVO(id));
    }

    /**
     * 执行 SQL 解析字段
     */
    @PostMapping("/tables/parse")
    public R<List<ParseColumnVO>> parseSql(@Valid @RequestBody FieldExecuteParseDTO dto) {
        return R.ok(fieldMappingService.executeParse(dto));
    }

    // ===================== F4: 编辑视图 =====================

    /**
     * 编辑视图 - 查询回显
     */
    @GetMapping("/tables/view/info")
    public R<ViewInfoVO> viewInfo(@RequestParam Long id) {
        return R.ok(fieldMappingService.getViewInfo(id, getCurrentTenantId()));
    }

    // ===================== F5: 语义映射 =====================

    /**
     * 获取字段映射列表（语义映射弹窗数据）
     */
    @GetMapping("/tables/{id}/fields")
    public R<List<FieldMappingVO>> fieldMappings(@PathVariable Long id) {
        return R.ok(fieldMappingService.getFieldMappings(id, getCurrentTenantId()));
    }

    /**
     * 保存语义映射
     */
    @PostMapping("/mappings")
    public R<Void> saveMappings(@Valid @RequestBody SemanticMappingSaveDTO dto) {
        fieldMappingService.saveSemanticMappings(dto, getCurrentTenantId());
        return R.ok();
    }

    // ===================== F6/F7: 批量注册 =====================

    /**
     * 批量注册维度/指标
     */
    @PostMapping("/batch-register")
    public R<Void> batchRegister(@Valid @RequestBody BatchRegisterDTO dto) {
        fieldMappingService.batchRegister(dto, getCurrentTenantId());
        return R.ok();
    }

    // ===================== 通用 =====================

    /**
     * 删除分析表
     */
    @DeleteMapping("/tables/{id}")
    public R<Void> deleteTable(@PathVariable Long id) {
        fieldMappingService.deleteTable(id, getCurrentTenantId());
        return R.ok();
    }

    /**
     * 获取数据源选项
     */
    @GetMapping("/data-sources")
    public R<List<DataSourceOptionVO>> dataSources() {
        return R.ok(fieldMappingService.listDataSources());
    }

    @GetMapping("/tablesBySourceId")
    public R<List<TableMappingVo>> tablesBySourceId(@RequestParam("sourceId") Long sourceId, @RequestParam(value = "keyword" ,required = false) String keyword) {
        return R.ok(fieldMappingService.listTablesBySourceId(sourceId, keyword, getCurrentTenantId()));
    }

    private Long getCurrentTenantId() {
        // TODO: 从 SecurityContext / Header 获取当前租户
        return 1L;
    }

    @GetMapping("/ptdate")
    public R<DimensionVO> ptdate() {
        return R.ok(fieldMappingService.ptdate());
    }
}
