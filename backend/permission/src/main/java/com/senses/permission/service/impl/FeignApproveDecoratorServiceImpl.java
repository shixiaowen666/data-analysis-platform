package com.senses.permission.service.impl;

import com.alibaba.fastjson.JSON;
import com.senses.permission.model.Response;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.ApproveWorksheet;
import com.senses.permission.service.FeignApproveDecoratorService;
import com.senses.permission.service.client.ApproveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Map;

@Service
@Slf4j
public class FeignApproveDecoratorServiceImpl implements FeignApproveDecoratorService {

    @Resource
    private ApproveService approveService;
    @Override
    public ResultData<Map<String,Object>> saveWorksheet(ApproveWorksheet approveWorksheet,String logInfoPre) {
        try{
            log.info("发起审批："+ JSON.toJSONString(approveWorksheet));
            ResultData<Map<String,Object>> resultData = approveService.saveWorksheet(approveWorksheet);
            log.info(logInfoPre+JSON.toJSONString(resultData));
            return resultData;
        }catch (Exception e){
            log.error(logInfoPre+"调用审批中心服务异常");
            log.error("",e);
            return ResultData.fail("调用审批中心服务异常："+e.getMessage());
        }
    }
}
