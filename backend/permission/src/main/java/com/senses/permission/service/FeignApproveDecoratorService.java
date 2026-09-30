package com.senses.permission.service;

import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.ApproveWorksheet;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

public interface FeignApproveDecoratorService {
    public ResultData<Map<String,Object>> saveWorksheet(@RequestBody ApproveWorksheet approveWorksheet, String logInfoPre);
}
