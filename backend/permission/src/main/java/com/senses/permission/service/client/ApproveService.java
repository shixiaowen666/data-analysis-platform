package com.senses.permission.service.client;

import io.swagger.v3.oas.annotations.Operation;
import com.senses.permission.model.Response;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.ApproveWorksheet;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 审批中心
 */
@FeignClient(name = "service-approve")
public interface ApproveService {

    @PostMapping("/approve/worksheet/saveWorksheet")
    @Operation(summary = "提交审批工单")
    public ResultData<Map<String,Object>> saveWorksheet(@RequestBody ApproveWorksheet approveWorksheet);
}
