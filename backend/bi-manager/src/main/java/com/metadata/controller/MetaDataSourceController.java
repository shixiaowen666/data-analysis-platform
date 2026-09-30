package com.metadata.controller;

import com.bi.vo.ApiPageResult;
import com.common.result.PageResult;
import com.common.result.R;
import com.metadata.dto.meta.MetaCollectRequestDTO;
import com.metadata.dto.meta.MetaDataSourceQueryDTO;
import com.metadata.dto.meta.MetaDataSourceSaveDTO;
import com.metadata.dto.meta.RemoteTableQueryDTO;
import com.metadata.engine.service.MetadataCollectOrchestrator;
import com.metadata.engine.service.RemoteTableQueryService;
import com.metadata.service.MetaDataSourceService;
import com.metadata.vo.meta.DataSourceTestResultVO;
import com.metadata.vo.meta.MetaCollectStartVO;
import com.metadata.vo.meta.MetaDataSourceVO;
import com.metadata.vo.meta.RemoteTableVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 数据源 Controller
 * <p>
 * Base: /api/v1/data-sources
 */
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/v1/data-sources")
@RequiredArgsConstructor
public class MetaDataSourceController {

    private final MetaDataSourceService metaDataSourceService;
    private final MetadataCollectOrchestrator metadataCollectOrchestrator;
    private final RemoteTableQueryService remoteTableQueryService;

    @Operation(summary = "数据源分页列表")
    @GetMapping
    public R<ApiPageResult<MetaDataSourceVO>> page(MetaDataSourceQueryDTO query) {
        PageResult<MetaDataSourceVO> page = metaDataSourceService.pageList(query);
        return R.ok(new ApiPageResult<>(page.getRecords(), page.getTotal(), page.getPage(), page.getPageSize()));
    }

    @Operation(summary = "数据源详情")
    @GetMapping("/{id}")
    public R<MetaDataSourceVO> detail(@Parameter(description = "数据源 ID") @PathVariable Long id) {
        return R.ok(metaDataSourceService.getDetail(id));
    }

    @Operation(summary = "新增数据源")
    @PostMapping
    public R<Long> create(@Validated @RequestBody MetaDataSourceSaveDTO dto) {
        return R.ok(metaDataSourceService.create(dto));
    }

    @Operation(summary = "修改数据源")
    @PutMapping
    public R<Void> update(@RequestBody MetaDataSourceSaveDTO dto) {
        metaDataSourceService.updateDataSource(dto);
        return R.ok();
    }

    @Operation(summary = "修改数据源（路径 ID）")
    @PutMapping("/{id}")
    public R<Void> updateById(@Parameter(description = "数据源 ID") @PathVariable Long id,
                              @RequestBody MetaDataSourceSaveDTO dto) {
        dto.setId(id);
        metaDataSourceService.updateDataSource(dto);
        return R.ok();
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "数据源 ID") @PathVariable Long id) {
        metaDataSourceService.deleteDataSource(id);
        return R.ok();
    }

    @Operation(summary = "测试连接")
    @PostMapping("/test-connection")
    public R<DataSourceTestResultVO> testConnection(@RequestBody MetaDataSourceSaveDTO dto) {
        return R.ok(metaDataSourceService.testConnection(dto));
    }

    @Operation(summary = "预览 JDBC URL")
    @PostMapping("/jdbc-url")
    public R<String> previewJdbcUrl(@RequestBody MetaDataSourceSaveDTO dto) {
        return R.ok(metaDataSourceService.previewJdbcUrl(dto));
    }

    @Operation(summary = "启动元数据采集",
            description = "异步采集，立即返回 RUNNING。collectType=full 全库采集；collectType=select 选表采集，"
                    + "tableNames 传入本次勾选表名并自动保存至 meta_select_table，仅采集这些表")
    @PostMapping("/{id}/collect")
    public R<MetaCollectStartVO> collect(@Parameter(description = "数据源 ID") @PathVariable Long id,
                                         @RequestBody(required = false) MetaCollectRequestDTO request) {
        return R.ok(metadataCollectOrchestrator.startCollect(id, request));
    }


    @Operation(summary = "远程库表分页列表", description = "POST JSON Body：sourceId、tableName、page、pageSize")
    @PostMapping("/remote-tables")
    public R<PageResult<RemoteTableVO>> remoteTables(@RequestBody RemoteTableQueryDTO query) {
        return R.ok(remoteTableQueryService.pageRemoteTables(query));
    }
}
