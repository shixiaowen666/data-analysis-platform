package com.chatbi.chat.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    private ThreadPoolExecutor chatAsyncExecutor;

    @Bean("chatAsyncExecutor")
    public ExecutorService chatAsyncExecutor() {
        chatAsyncExecutor = new ThreadPoolExecutor(
                20, 40,
                120L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactoryBuilder().setNameFormat("chat-async-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return chatAsyncExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (chatAsyncExecutor != null) {
            chatAsyncExecutor.shutdown();
            try {
                if (!chatAsyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    chatAsyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                chatAsyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Scheduled(fixedRate = 30000)
    public void logThreadPoolStats() {
        if (chatAsyncExecutor == null) {
            return;
        }
        log.info("ThreadPool[chat-async] core={}, max={}, pool={}, active={}, queue={}/{}, completed={}, largest={}",
                chatAsyncExecutor.getCorePoolSize(),
                chatAsyncExecutor.getMaximumPoolSize(),
                chatAsyncExecutor.getPoolSize(),
                chatAsyncExecutor.getActiveCount(),
                chatAsyncExecutor.getQueue().size(),
                ((LinkedBlockingQueue<?>) chatAsyncExecutor.getQueue()).remainingCapacity() + chatAsyncExecutor.getQueue().size(),
                chatAsyncExecutor.getCompletedTaskCount(),
                chatAsyncExecutor.getLargestPoolSize());
    }
}
