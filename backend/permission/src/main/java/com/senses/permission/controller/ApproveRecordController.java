package com.senses.permission.controller;


import io.swagger.v3.oas.annotations.tags.Tag;
import com.alibaba.fastjson.JSON;
import com.senses.permission.entity.ApproveRecord;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.vo.VsfApproveVo;
import com.senses.permission.service.ApproveRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/approve")
@Tag(name = "审批回调管理 API")
public class ApproveRecordController {

    @Resource
    private ApproveRecordService approveRecordService;

    @PostMapping("/callback")
    public ResultData confirm(@RequestBody ApproveRecord approveRecords){
        log.info("审批回调参数："+ JSON.toJSONString(approveRecords));
        return approveRecordService.confirm(approveRecords);
    }

    @PostMapping("/cancelWorksheet")
    public ResultData cancelWorksheet(@RequestBody VsfApproveVo vsfApproveVo){
        log.info("审批撤回回调参数："+ JSON.toJSONString(vsfApproveVo));
        return approveRecordService.cancelWorksheet(vsfApproveVo);
    }

}
