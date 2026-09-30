package com.common.feign.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenFeign 配置
 */
@Slf4j
@Configuration
public class FeignConfig {

    /**
     * Feign 日志级别
     * NONE(默认), BASIC(记录请求方法和URL), HEADERS(记录请求头), FULL(完整请求/响应)
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * 请求重试策略
     * 初始间隔 100ms，最大间隔 1000ms，最多重试 3 次
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 3);
    }

    /**
     * 全局请求拦截器（示例：透传 Token）
     * TODO: 从 ThreadLocal / Header 获取 Token 并透传到下游服务
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // String token = SecurityContextHolder.getToken();
            // if (token != null) {
            //     template.header("Authorization", token);
            // }
            log.debug("Feign Request: {} {}", template.method(), template.url());
        };
    }
}
