package com.senses.permission.service.client;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import com.senses.permission.model.Result;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.DsfAuditLog;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分享
 */
@FeignClient(name = "file-hub")
public interface FileHubService {
    /**
     * 提供外部接口
     * 添加初始化分享目录
     * @param fileName
     * @return
     */
    @Operation(summary = "添加初始化分享目录")
    @PostMapping(value = "/file_hub/save/shareDirectory")
    public Result saveShareDirectory(@Parameter(description = "fileName") @RequestParam(value = "fileName", required = true) String fileName);

}
