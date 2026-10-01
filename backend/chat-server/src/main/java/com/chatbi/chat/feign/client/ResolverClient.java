package com.chatbi.chat.feign.client;

import com.chatbi.chat.models.LLMRequestParam;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "service-resolver",url = "${feign.resolver:}")
public interface ResolverClient {

    @Operation(summary = "拆解任务")
    @PostMapping("/api/v1/analyze")
    /** System B 直接返回 analyze 结果 JSON（非 code/data 包装），用 JSONObject 接收以保留全部字段供 trace 落库 */
    JSONObject analyze(@RequestBody LLMRequestParam requestParam);
}
