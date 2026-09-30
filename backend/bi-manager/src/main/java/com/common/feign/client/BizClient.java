package com.common.feign.client;

import com.common.result.R;
import com.common.feign.config.FeignConfig;
import com.common.feign.dto.FeignResult;
import com.common.feign.factory.BizClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 示例 Feign Client
 * <p>
 * name = 服务名（Nacos 服务注册名）
 * path = 前缀路径（可选）
 * fallback = 服务降级实现
 */
@FeignClient(
        name = "biz-backend",
        url = "${feign.biz-backend.url:}",
        path = "/api",
        fallback = BizClientFallbackFactory.class
)
public interface BizClient {

    /**
     * 调用下游服务 - 根据ID查询
     */
    @GetMapping("/user/{id}")
    R<Object> getUserById(@PathVariable("id") Long id);

    /**
     * 调用下游服务 - 获取业务数据
     */
    @GetMapping("/biz/data")
    FeignResult<String> getBizData();
}
