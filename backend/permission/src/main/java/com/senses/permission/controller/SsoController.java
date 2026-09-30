package com.senses.permission.controller;

import com.senses.permission.entity.User;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.SsoUserParam;
import com.senses.permission.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/upc/sso")
@Tag(name = "SSO 用户管理 API")
public class SsoController {

    @Resource
    private UserService userService;

    @Operation(summary = "根据SSO标识查询用户")
    @GetMapping(value = "/user")
    public ResultData<User> getUser(
            @Parameter(description = "SSO外部用户标识") @RequestParam String ssoId) {
        User user = userService.getBySsoId(ssoId);
        if (user == null) {
            return ResultData.fail("用户不存在");
        }
        return ResultData.success(user);
    }

    @Operation(summary = "新增SSO用户")
    @PostMapping(value = "/user")
    public ResultData<User> createUser(
            @Parameter(description = "SSO用户参数") @RequestBody SsoUserParam param) {
        User user = userService.createSsoUser(param);
        return ResultData.success(user);
    }

}
