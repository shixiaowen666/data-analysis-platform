package com.bi.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bi.dto.DimensionQueryDTO;
import com.bi.dto.DimensionReq;
import com.bi.dto.NameCheckReq;
import com.bi.service.IDimensionService;
import com.bi.vo.DimensionDetailVO;
import com.bi.vo.DimensionVO;
import com.bi.vo.NameCheckVO;
import com.common.result.PageResult;
import com.common.result.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 维度管理 Controller
 */
@Slf4j
@Tag(name = "维度管理")
@RestController
@RequestMapping("/v1/dimensions")
@RequiredArgsConstructor
public class DimensionController {

    private final IDimensionService dimensionService;

    /**
     * 分页查询维度列表
     * GET /api/v1/dimensions?keyword=&dimensionType=&collectStatus=&page=1&pageSize=10
     */
    @GetMapping("/page")
    public R<PageResult<DimensionVO>> listDimensions(DimensionQueryDTO query) {
        Long tenantId = getCurrentTenantId();
        IPage<DimensionVO> pageResult = dimensionService.listDimensions(query, tenantId);
        PageResult<DimensionVO> result = new PageResult<>(
                pageResult.getTotal(),
                pageResult.getRecords(),
                pageResult.getCurrent(),
                pageResult.getSize()
        );
        return R.ok(result);
    }

    /**
     * 获取维度详情
     * GET /api/v1/dimensions/{id}
     */
    @GetMapping("/{id}")
    public R<DimensionDetailVO> getDetail(@PathVariable Long id) {
        DimensionDetailVO detail = dimensionService.getDimensionDetail(id);
        return R.ok(detail);
    }

    /**
     * 新建维度
     * POST /api/v1/dimensions
     */
    @PostMapping("/save")
    public R<Object> saveDimension(@Valid @RequestBody DimensionReq req) {
        Long tenantId = getCurrentTenantId();
        if(req.getId()==null){
            dimensionService.createDimension(req,tenantId);
        }else {
            dimensionService.updateDimension(req, tenantId);
        }
        return R.ok();
    }

    /**
     * 编辑维度
     * PUT /api/v1/dimensions/{id}
     * 废弃 20260622
     */
    @Deprecated
    @PutMapping("/feiqi")
    public R<Object> updateDimension(@PathVariable Long id, @Valid @RequestBody DimensionReq req) {
        Long tenantId = getCurrentTenantId();
        dimensionService.updateDimension( req, tenantId);
        return R.ok();
    }

    /**
     * 上线维度
     * POST /api/v1/dimensions/{id}/online
     */
    @GetMapping("/{id}/online")
    public R<Object> onlineDimension(@PathVariable Long id) {
        Long tenantId = getCurrentTenantId();
        dimensionService.onlineDimension(id, tenantId);
        return R.ok();
    }

    /**
     * 查询所有已上线的维度列表（不分页）
     * GET /api/v1/dimensions/list?keyword=xxx
     */
    @GetMapping("/list")
    public R<List<DimensionVO>> listAllDimensions(@RequestParam(required = false) String keyword) {
        Long tenantId = getCurrentTenantId();
        List<DimensionVO> list = dimensionService.listAllDimensions(keyword, tenantId);
        return R.ok(list);
    }

    /**
     * 维度下线
     * POST /api/v1/dimensions/{id}/offline
     */
    @GetMapping("/{id}/offline")
    public R<Object> offlineDimension(@PathVariable Long id) {
        Long tenantId = getCurrentTenantId();
        dimensionService.offlineDimension(id, tenantId);
        return R.ok();
    }

    /**
     * 删除维度
     * DELETE /api/v1/dimensions/{id}
     */
    @DeleteMapping("/{id}")
    public R<Object> deleteDimension(@PathVariable Long id) {
        Long tenantId = getCurrentTenantId();
        dimensionService.deleteDimension(id, tenantId);
        return R.ok();
    }

    /**
     * 获取维值列表
     * GET /v1/dimensions/{id}/values
     */
    @GetMapping("/{id}/values")
    public R<List<String>> getDimensionValues(@PathVariable Long id) {
        List<String> values = dimensionService.getDimensionValues(id);
        return R.ok(values);
    }

    /**
     * 生成维度编码（辅助接口）
     * GET /api/v1/dimensions/code
     */
    @GetMapping("/code")
    public R<String> generateCode() {
        String code = dimensionService.generateDimensionCode();
        return R.ok(code);
    }

    /**
     * 校验英文名是否已存在
     * POST /v1/dimensions/check-ename
     */
    @PostMapping("/check-ename")
    public R<NameCheckVO> checkEnglishName(@RequestBody NameCheckReq req) {
        Long tenantId = getCurrentTenantId();
        return R.ok(dimensionService.checkEnglishName(req.getName(), req.getId(), tenantId));
    }

    /**
     * 校验中文名是否已存在
     * POST /v1/dimensions/check-cname
     */
    @PostMapping("/check-cname")
    public R<NameCheckVO> checkChineseName(@RequestBody NameCheckReq req) {
        Long tenantId = getCurrentTenantId();
        return R.ok(dimensionService.checkChineseName(req.getName(), req.getId(), tenantId));
    }

    /**
     * 获取当前租户 ID（实际应从上下文获取）
     */
    private Long getCurrentTenantId() {
        // TODO: 从 SecurityContext / Header 获取当前租户
        return 1L;
    }
}
