package com.chatbi.chat.controller;

import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.service.IndicatorGroupService;
import com.chatbi.chat.service.IndicatorGroupService.ItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/chat-server"})
@Slf4j
public class IndicatorGroupController {

    @Autowired
    private IndicatorGroupService indicatorGroupService;

    @PostMapping("/group/preview")
    public JSONObject preview(@RequestBody PreviewRequest request) {
        log.info(">>> POST /group/preview items: {}", request.getItems());
        return indicatorGroupService.preview(request.getItems());
    }

    @lombok.Data
    public static class PreviewRequest {
        private List<ItemRequest> items;
    }
}
