package com.chatbi.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Component
@Slf4j
public class QueryTaskManager {

    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public void register(String requestId, Future<?> future) {
        runningTasks.put(requestId, future);
        log.info("任务注册: requestId={}, 当前任务数={}", requestId, runningTasks.size());
    }

    public void remove(String requestId) {
        runningTasks.remove(requestId);
        log.info("任务移除: requestId={}, 当前任务数={}", requestId, runningTasks.size());
    }

    public boolean cancel(String requestId) {
        Future<?> future = runningTasks.remove(requestId);
        if (future != null) {
            log.info("任务取消: requestId={}", requestId);
            return future.cancel(true);
        }
        log.warn("任务取消失败，任务不存在: requestId={}", requestId);
        return false;
    }
}
