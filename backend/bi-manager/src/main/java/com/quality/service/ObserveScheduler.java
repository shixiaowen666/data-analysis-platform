package com.quality.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 观察期巡检：每 30 分钟检查已发布任务的同类 👎 是否超阈值 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ObserveScheduler {

    private final TuningService tuningService;

    @Scheduled(fixedDelayString = "${quality.observe-interval-ms:1800000}", initialDelay = 60_000)
    public void tick() {
        try {
            int n = tuningService.observeCheck();
            if (n > 0) log.info("[quality] observe check raised {} alert(s)", n);
        } catch (Exception e) {
            log.warn("[quality] observe check failed: {}", e.getMessage());
        }
    }
}
