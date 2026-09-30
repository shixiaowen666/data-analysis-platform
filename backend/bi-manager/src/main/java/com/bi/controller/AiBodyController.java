package com.bi.controller;

import com.bi.dto.AiBodyQueryDTO;
import com.bi.dto.AiBodySaveDTO;
import com.bi.service.IAiBodyService;
import com.bi.vo.*;
import com.common.base.UserThreadLocal;
import com.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 智能体管理
 * <p>
 * Base: /v1/ai-bodies
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/aibody")
@RequiredArgsConstructor
public class AiBodyController {

    private final IAiBodyService aiBodyService;

    /**
     * 智能体分页列表
     */
    @GetMapping("/list")
    public R<ApiPageResult<AiBodyVO>> list(@Valid AiBodyQueryDTO query) {
        return R.ok(aiBodyService.listAgents(query, getCurrentTenantId()));
    }

    /**
     * 智能体详情
     */
    @GetMapping("/info/{code}")
    public R<AiBodyDetailVO> detail(@PathVariable String code) {
        return R.ok(aiBodyService.getAgentDetail(code, getCurrentTenantId()));
    }

    /**
     * 创建智能体
     */
    @PostMapping("/save")
    public R<Void> save(@Valid @RequestBody AiBodySaveDTO dto) {
        if(StringUtils.isNotBlank(dto.getCode())){
            aiBodyService.updateAgent(dto,getCurrentTenantId());
        } else {
            aiBodyService.createAgent(dto, getCurrentTenantId());
        }
        return R.ok();
    }

    /**
     * 删除智能体
     */
    @GetMapping("/del/{code}")
    public R<Void> delete(@PathVariable String code) {
        aiBodyService.deleteAgent(code, getCurrentTenantId());
        return R.ok();
    }

    /**
     * 删除智能体关联表
     */
    @GetMapping("/relation/del")
    public R<Void> deleteRelation(@RequestParam String code,
                                  @RequestParam(required = false) Long id) {
        aiBodyService.deleteRelation(code, id, getCurrentTenantId());
        return R.ok();
    }

    /**
     * 获取数据源下的可用数据表表列表
     */
    @GetMapping("/candidate-tables")
    public R<List<OlapModelRelationVO>> candidateTables(
            @RequestParam Long sourceId,
            @RequestParam(required = false) String keyword) {
        return R.ok(aiBodyService.availableTables(sourceId, keyword, getCurrentTenantId()));
    }

    /**
     * 知识库导出为 Excel
     */
    @GetMapping("/{aiBodyId}/knowledge/export")
    public void exportKnowledge(
            @PathVariable Long aiBodyId,
            HttpServletResponse response) throws IOException {
        byte[] data = aiBodyService.exportKnowledge(aiBodyId, getCurrentTenantId());
        String fileName = URLEncoder.encode("知识库.xlsx", String.valueOf(StandardCharsets.UTF_8));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }

    private Long getCurrentTenantId() {
        return UserThreadLocal.get().getTenantId();
    }
}
