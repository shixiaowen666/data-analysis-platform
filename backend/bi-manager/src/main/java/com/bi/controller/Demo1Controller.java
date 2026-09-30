package com.bi.controller;

import com.common.result.R;
import com.common.feign.client.BizClient;
import com.common.feign.dto.FeignResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feign 调用示例 Controller
 */
@Tag(name = "Demo示例")
@Slf4j
@RestController
@RequestMapping("/v1/demo")
@RequiredArgsConstructor
public class Demo1Controller {

    private final BizClient bizClient;

    @Operation(summary = "Feign 获取用户信息")
    @GetMapping("/user/{id}")
    public R<Object> getUserByFeign(@PathVariable Long id) {
        R<Object> result = bizClient.getUserById(id);
        if (result != null && result.isSuccess()) {
            return R.ok(result.getData());
        }
        return R.fail("获取用户信息失败: " + (result != null ? result.getMessage() : "服务不可用"));
    }

    @Operation(summary = "Feign 获取业务数据")
    @GetMapping("/biz/data")
    public R<String> getBizDataByFeign() {
        FeignResult<String> result = bizClient.getBizData();
        if (result != null && result.isSuccess()) {
            return R.ok(result.getData());
        }
        return R.fail("获取业务数据失败: 服务不可用");
    }
}
