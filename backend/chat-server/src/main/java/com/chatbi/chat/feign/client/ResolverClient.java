package com.chatbi.chat.feign.client;

import com.chatbi.chat.models.LLMRequestParam;
import com.chatbi.chat.response.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "service-resolver",url = "${feign.resolver:}")
public interface ResolverClient {

    @Operation(summary = "拆解任务")
    @PostMapping("/api/v1/analyze")
    ResultData analyze(@RequestBody LLMRequestParam requestParam);
}
