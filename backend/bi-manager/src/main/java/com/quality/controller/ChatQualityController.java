package com.quality.controller;

import com.alibaba.fastjson.JSONObject;
import com.common.result.PageResult;
import com.common.result.R;
import com.quality.dto.DiagnosisSaveDTO;
import com.quality.dto.FeedbackSubmitDTO;
import com.quality.dto.PageQuery;
import com.quality.entity.ChatErrorDiagnosis;
import com.quality.entity.ChatErrorTypeDict;
import com.quality.entity.ChatFeedback;
import com.quality.service.ChatQualityService;
import com.quality.service.TuningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 问答质量管理（01 文档 §6）  Base: /v1/chat
 */
@Validated
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatQualityController {

    private final ChatQualityService service;
    private final TuningService tuningService;

    @GetMapping("/error-types")
    public R<List<ChatErrorTypeDict>> errorTypes(@RequestParam(required = false) String scope) {
        return R.ok(service.errorTypes(scope));
    }

    /** 用户提交 / 更新反馈 */
    @PostMapping("/feedback/submit")
    public R<ChatFeedback> submit(@Valid @RequestBody FeedbackSubmitDTO dto) {
        return R.ok(service.submitFeedback(dto));
    }

    @GetMapping("/feedback/mine")
    public R<ChatFeedback> mine(@RequestParam String chatId) {
        return R.ok(service.myFeedback(chatId));
    }

    @GetMapping("/feedback/page")
    public R<PageResult<JSONObject>> feedbackPage(PageQuery q) {
        return R.ok(service.feedbackPage(q));
    }

    @PutMapping("/feedback/{id}/status")
    public R<Void> feedbackStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.updateFeedbackStatus(id, body.get("status"));
        return R.ok();
    }

    @GetMapping("/trace/{chatId}")
    public R<JSONObject> trace(@PathVariable String chatId) {
        return R.ok(service.trace(chatId));
    }

    @GetMapping("/trace/{chatId}/system-b-log")
    public R<JSONObject> systemBLog(@PathVariable String chatId) {
        return R.ok(service.systemBLog(chatId));
    }

    @GetMapping("/diagnosis/page")
    public R<PageResult<JSONObject>> diagnosisPage(PageQuery q) {
        return R.ok(service.diagnosisPage(q));
    }

    /** 保存定位；generateSuggestions=true 时同时返回调优建议 */
    @PostMapping("/diagnosis/save")
    public R<JSONObject> saveDiagnosis(@Valid @RequestBody DiagnosisSaveDTO dto) {
        ChatErrorDiagnosis d = service.saveDiagnosis(dto);
        JSONObject out = new JSONObject();
        out.put("diagnosis", d);
        if (Boolean.TRUE.equals(dto.getGenerateSuggestions())) {
            out.put("suggestions", tuningService.suggest(d.getId()));
        }
        return R.ok(out);
    }

    @PutMapping("/diagnosis/{chatId}/fix-status")
    public R<Void> fixStatus(@PathVariable String chatId, @RequestBody Map<String, Integer> body) {
        service.updateFixStatus(chatId, body.get("fixStatus"));
        return R.ok();
    }

    @GetMapping("/quality/stats")
    public R<JSONObject> stats(@RequestParam(required = false) String aiBodyCode, @RequestParam(required = false) Integer days) {
        return R.ok(service.stats(aiBodyCode, days));
    }
}
