package com.bi.controller;

import com.bi.dto.RecommendQuestionQueryDTO;
import com.bi.dto.RecommendQuestionSaveDTO;
import com.bi.service.IRecommendQuestionService;
import com.bi.vo.ApiPageResult;
import com.bi.vo.RecommendQuestionVO;
import com.common.base.UserThreadLocal;
import com.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/v1/recommend/question")
@RequiredArgsConstructor
public class RecommendQuestionController {

    private final IRecommendQuestionService recommendQuestionService;

    @GetMapping("/page")
    public R<ApiPageResult<RecommendQuestionVO>> listQuestions(@Valid RecommendQuestionQueryDTO query) {
        return R.ok(recommendQuestionService.listQuestions(query, getTenantId()));
    }

    @PostMapping("/save")
    public R<Void> saveQuestion(@Valid @RequestBody RecommendQuestionSaveDTO dto) {
        recommendQuestionService.saveQuestion(dto, getTenantId());
        return R.ok();
    }

    @PostMapping("/status")
    public R<Void> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        recommendQuestionService.updateStatus(id, status, getTenantId());
        return R.ok();
    }

    @GetMapping("/del/{id}")
    public R<Void> deleteQuestion(@PathVariable Long id) {
        recommendQuestionService.deleteQuestion(id, getTenantId());
        return R.ok();
    }

    private Long getTenantId() {
        return UserThreadLocal.get().getTenantId();
    }
}
