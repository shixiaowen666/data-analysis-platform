package com.metadata.engine.service;

import org.springframework.stereotype.Component;

/**
 * 采集异步执行器
 */
@Component
public class MetadataCollectExecutor {

    public void execute(Runnable task) {
        Thread thread = new Thread(task, "metadata-collect-" + System.currentTimeMillis());
        thread.setDaemon(true);
        thread.start();
    }
}
