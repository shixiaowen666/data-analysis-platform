package com.senses.permission.service.client;

import io.swagger.v3.oas.annotations.Operation;
import com.senses.permission.model.ResultData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @ClassName SenseChatClient
 * @Description TODO
 * @Author lhw
 * @Date 2025/8/7 09:08
 */
@FeignClient(url = "${feign.senses-chat:}",value = "senses-chat")
public interface SenseChatClient {
    @Operation(summary = "根据租户id获取userId列表")
    @GetMapping(value = "/senses-chat/space/getUserIdsByTenantId")
    ResultData<List<Long>> getUserIdsByTenantId(@RequestParam("tenantId") Long tenantId);
}
