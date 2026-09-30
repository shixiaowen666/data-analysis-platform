package com.chatbi.chat.controller;

import com.chatbi.chat.response.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public R<Map<String, Object>> health() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "UP");
        map.put("message", "Service is running");
        return R.ok(map);
    }
}
