package com.bi.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bi.dto.MetricQueryDTO;
import com.bi.dto.MetricReq;
import com.bi.dto.NameCheckReq;
import com.bi.service.IMetricService;
import com.bi.vo.MetricDetailVO;
import com.bi.vo.MetricVO;
import com.bi.vo.NameCheckVO;
import com.common.base.LoginUser;
import com.common.base.UserThreadLocal;
import com.common.exception.BizException;
import com.common.models.SaasUser;
import com.common.result.PageResult;
import com.common.result.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;

/**
 * 指标管理 Controller
 */
@Slf4j
@Tag(name = "指标管理")
@RestController
@RequestMapping("/v1/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final IMetricService metricService;

    /**
     * 分页查询指标列表
     * GET /api/v1/metrics?keyword=&type=&status=&page=1&pageSize=10
     */
    @GetMapping
    public R<PageResult<MetricVO>> listMetrics(MetricQueryDTO query) {
        String keyword = query.getKeyword();
        String type = query.getType();
        Integer status = query.getStatus();
        long page = query.getPage() != null ? query.getPage() : 1;
        long pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        // 从 Header 获取租户 ID（实际应从 SecurityContext 获取）
        Long tenantId = getCurrentTenantId();

        IPage<MetricVO> pageResult = metricService.listMetrics(keyword, type, status, tenantId, page, pageSize);
        PageResult<MetricVO> result = new PageResult<>(
                pageResult.getTotal(),
                pageResult.getRecords(),
                page,
                pageSize
        );
        return R.ok(result);
    }

    /**
     * 获取指标详情
     * GET /api/v1/metrics/{id}
     */
    @GetMapping("/{id}")
    public R<MetricDetailVO> getDetail(@PathVariable Long id) {
        MetricDetailVO detail = metricService.getMetricDetail(id);
        return R.ok(detail);
    }

    /**
     * 新建指标
     * POST /api/v1/metrics
     */
    @PostMapping
    public R<Object> createMetric(@Valid @RequestBody MetricReq req) {
        Long tenantId = getCurrentTenantId();
        metricService.createMetric(req, tenantId);
        return R.ok();
    }

    /**
     * 编辑指标
     * PUT /api/v1/metrics/{id}
     */
    @PutMapping("/{id}")
    public R<Object> updateMetric(@PathVariable Long id, @Valid @RequestBody MetricReq req) {
        Long tenantId = getCurrentTenantId();
        metricService.updateMetric(id, req, tenantId);
        return R.ok();
    }

    /**
     * 上线指标
     * POST /api/v1/metrics/{id}/online
     */
    @GetMapping("/{id}/online")
    public R<Object> onlineMetric(@PathVariable Long id) {
        Long tenantId = getCurrentTenantId();
        metricService.onlineMetric(id, tenantId);
        return R.ok();
    }

    /**
     * 查询所有已上线的指标列表（不分页）
     * GET /api/v1/metrics/list?keyword=xxx
     */
    @GetMapping("/list")
    public R<List<MetricVO>> listAllMetrics(@RequestParam(required = false) String keyword) {
        Long tenantId = getCurrentTenantId();
        List<MetricVO> list = metricService.listAllMetrics(keyword, tenantId);
        return R.ok(list);
    }

    /**
     * 查询所有已上线指标 + 已上线指标组合（不分页，组合 type=group）
     * GET /api/v1/metrics/list-with-groups?keyword=xxx
     */
    @GetMapping("/list-with-groups")
    public R<List<MetricVO>> listAllMetricsWithGroups(@RequestParam(required = false) String keyword) {
        Long tenantId = getCurrentTenantId();
        return R.ok(metricService.listAllMetricsWithGroups(keyword, tenantId));
    }

    /**
     * 下线指标
     * POST /api/v1/metrics/{id}/offline
     */
    @GetMapping("/{id}/offline")
    public R<Object> offlineMetric(@PathVariable Long id) {
        Long tenantId = getCurrentTenantId();
        metricService.offlineMetric(id, tenantId);
        return R.ok();
    }

    /**
     * 删除指标
     * DELETE /api/v1/metrics/{id}
     */
    @DeleteMapping("/{id}")
    public R<Object> deleteMetric(@PathVariable Long id) {
        Long tenantId = getCurrentTenantId();
        metricService.deleteMetric(id, tenantId);
        return R.ok();
    }

    /**
     * 生成指标编码（辅助接口）
     * GET /api/v1/metrics/code
     */
    @GetMapping("/code")
    public R<String> generateCode() {
        String code = metricService.generateMetricCode();
        return R.ok(code);
    }

    /**
     * 校验英文名是否已存在
     * POST /v1/metrics/check-ename
     */
    @PostMapping("/check-ename")
    public R<NameCheckVO> checkEnglishName(@RequestBody NameCheckReq req) {
        Long tenantId = getCurrentTenantId();
        return R.ok(metricService.checkEnglishName(req.getName(), req.getId(), tenantId));
    }

    /**
     * 校验中文名是否已存在
     * POST /v1/metrics/check-cname
     */
    @PostMapping("/check-cname")
    public R<NameCheckVO> checkChineseName(@RequestBody NameCheckReq req) {
        Long tenantId = getCurrentTenantId();
        return R.ok(metricService.checkChineseName(req.getName(), req.getId(), tenantId));
    }

    /**
     * 获取当前租户 ID（实际应从上下文获取）
     */
    private Long getCurrentTenantId() {
        // TODO: 从 SecurityContext / Header 获取当前租户
        SaasUser loginUser = UserThreadLocal.get();
        if(Objects.nonNull(loginUser)){
            return loginUser.getTenantId();
        }
        return 1L;
    }
}
