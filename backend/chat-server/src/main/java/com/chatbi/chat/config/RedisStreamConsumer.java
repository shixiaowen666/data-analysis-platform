package com.chatbi.chat.config;

import cn.hutool.json.JSONObject;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream消费者组件
 * 负责从Redis Stream读取消息并按照requestId分组顺序处理，
 * 并通过WebSocket推送和写入Redis List。
 */
@Slf4j
@Component
public class RedisStreamConsumer {

    @Resource
    private WebSocketServer webSocketServer;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Value("${redis.stream.queue.capacity:1000}")
    private int queueCapacity;
    
    private static final String STREAM_KEY_PREFIX = "llm_progress_";
    private static final String GROUP_NAME = "llm_group";
    private static final String CONSUMER_NAME_PREFIX = "consumer-";
    private static final String CONSUMER_NAME;
    private static final String POD_ID;
    private static String STREAM_KEY;
    private Thread consumerThread;
    private volatile boolean running = true;

    static {
        String podName = System.getenv("POD_NAME");
        if (podName != null && !podName.isEmpty()) {
            POD_ID = podName;
        } else {
            String hostname;
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                hostname = java.util.UUID.randomUUID().toString();
            }
            POD_ID = hostname;
        }
        CONSUMER_NAME = CONSUMER_NAME_PREFIX + POD_ID;
        STREAM_KEY = STREAM_KEY_PREFIX + POD_ID;
    }

    /**
     * 初始化方法，创建消费者组并启动消费线程
     */
    @PostConstruct
    public void init() {
        try {
            log.info("==============0427================{}",STREAM_KEY);
            ensureStreamAndGroup();
            // 启动消费线程
            consumerThread = new Thread(this::consumeLoop);
            consumerThread.setName("Stream-Consumer-Thread");
            consumerThread.setDaemon(true);
            consumerThread.start();
            log.info("Redis Stream 消费者已启动, Pod ID: {}, Consumer Name: {}", POD_ID, CONSUMER_NAME);
        } catch (Exception e) {
            log.error("初始化Redis Stream消费者失败: {}", e.getMessage(), e);
        }
    }

    public void ensureStreamAndGroup() {
        try {
            // 显式声明 RedisCallback<Void> 解决泛型推断标红
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                // key 需要序列化，groupName 和 readOffset 按接口定义直接传原生类型
                byte[] key = stringRedisTemplate.getStringSerializer().serialize(STREAM_KEY);
                connection.streamCommands().xGroupCreate(
                        key,
                        GROUP_NAME,              // String
                        ReadOffset.from("$"),    // ReadOffset 对象
                        true                     // mkStream = true
                );
                return null; // 忽略 @Nullable String 返回值，初始化无需关心
            });
            log.info("Redis Stream & Group 初始化成功: stream={}, group={}", STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
            if (msg.contains("BUSYGROUP") || causeMsg.contains("BUSYGROUP")) {
                log.debug("Redis Stream Group 已存在: stream={}, group={}", STREAM_KEY, GROUP_NAME);
            } else {
                log.error("ensureStreamAndGroup 失败: stream={}, group={}", STREAM_KEY, GROUP_NAME, e);
            }
        }
    }


    /**
     * 主循环，持续从Redis Stream消费消息
     */
    private void consumeLoop() {
        log.info("开始消费Redis Stream: {}", STREAM_KEY);
        // 异常退避参数
        long backoffMs = 1000;
        final long maxBackoffMs = 30_000;
        String lastErrorType = null;
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                        .read(Consumer.from(GROUP_NAME, CONSUMER_NAME),
                                StreamReadOptions.empty().count(25).block(Duration.ofMillis(200)),
                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
                backoffMs = 1000;
                lastErrorType = null;
                if (records != null && !records.isEmpty()) {
                    for (MapRecord<String, Object, Object> record : records) {
                        handleMessage(record);
                        acknowledgeMessage(record.getId());
                    }
                }
            } catch (Exception e) {
                String errorType = e.getClass().getSimpleName();
                String msg = e.getMessage();
                // 只在错误类型变化时打印完整堆栈，其余降级为 warn 无堆栈
                if (!errorType.equals(lastErrorType)) {
                    log.error("消费异常类型变更: {} -> {}, 当前退避{}ms", lastErrorType, errorType, backoffMs, e);
                    lastErrorType = errorType;
                } else {
                    log.warn("消费异常(退避{}ms): {}", backoffMs, msg);
                }
                if (msg != null && (msg.contains("NOGROUP") || msg.contains("no such key"))) {
                    log.warn("Stream或Group不存在，尝试重建: stream={}, group={}", STREAM_KEY, GROUP_NAME);
                    ensureStreamAndGroup();
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = Math.min(maxBackoffMs, backoffMs * 2);
            }
        }
        log.info("Redis Stream 消费线程已停止");
    }

    /**
     * 处理单条消息：推送WebSocket、写入Redis List
     */
    private void handleMessage(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> value = record.getValue();
            String messageJson = "";
            if (value != null && !value.isEmpty()) {
                if (value.containsKey("payload")) {
                    messageJson = value.get("payload").toString();
                } else if (value.containsKey("message")) {
                    messageJson = value.get("message").toString();
                } else {
                    messageJson = JSON.toJSONString(value);
                }
            }
            log.info("收到Stream消息: streamId={}, payload={}", record.getId(), messageJson);
            JSONObject messageData = new JSONObject(messageJson);
            String requestId = messageData.getStr("request_id");
            String stepType = messageData.getStr("step_type");
            String eventType = messageData.getStr("event_type");
            String message = messageData.getStr("message");
            String redisKey = requestId + "_msg";
            // 写入Redis（仅think类型消息）
            if ("think".equals(stepType)) {
                stringRedisTemplate.opsForList().rightPush(redisKey, message);
                log.debug("消息已写入Redis: streamId={}, requestId={}, step_type={} -> {}", record.getId(), requestId, stepType, message);
            } else {
                log.debug("消息未写入Redis(非think): streamId={}, requestId={}, step_type={}, message={}", record.getId(), requestId, stepType, message);
            }
            // 发送WebSocket（所有消息都推送）
            try {
                webSocketServer.sendMessage(requestId, messageJson);
            } catch (Exception wsEx) {
                log.error("WebSocket发送失败: {} -> {}, 错误: {}", requestId, message, wsEx.getMessage());
            }
            if ("close".equals(stepType)) {
                webSocketServer.closeConnection(requestId);
                stringRedisTemplate.delete(redisKey);
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 确认消息
     */
    private void acknowledgeMessage(RecordId recordId) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, recordId);
        } catch (Exception e) {
            log.error("确认消息失败: {}, 错误: {}", recordId.getValue(), e.getMessage(), e);
        }
    }


    /**
     * 获取当前Pod的Stream名称
     * @return 当前Pod的Stream名称
     */
    public static String getStreamKey() {
        return STREAM_KEY;
    }
    
    /**
     * 获取当前Pod的ID
     * @return 当前Pod的ID
     */
    public static String getPodId() {
        return POD_ID;
    }
    
    /**
     * 获取Stream键前缀
     * @return Stream键前缀
     */
    public static String getStreamKeyPrefix() {
        return STREAM_KEY_PREFIX;
    }
    
    /**
     * 关闭消费者线程
     */
    public void shutdown() {
        this.running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(5000);
            } catch (InterruptedException e) {
                log.warn("等待消费者线程关闭时被中断: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}