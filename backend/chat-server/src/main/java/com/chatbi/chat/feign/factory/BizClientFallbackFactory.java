package com.chatbi.chat.feign.factory;

import com.chatbi.chat.feign.client.BizClient;
import com.chatbi.chat.feign.dto.FeignResult;
import com.chatbi.chat.response.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feign 降级实现
 * <p>
 * Spring Cloud 2021+ 默认使用 Resilience4j 作为熔断器，
 * Feign 降级直接实现服务接口即可，不需要 FallbackFactory
 */
@Slf4j
@Component
public class BizClientFallbackFactory implements BizClient {

    @Override
    public R<Object> getUserById(Long id) {
        log.error("BizClient fallback: getUserById, id: {}", id);
        return R.fail("用户服务不可用，请稍后重试");
    }

    @Override
    public FeignResult<String> getBizData() {
        log.error("BizClient fallback: getBizData");
        return null;
    }
}
