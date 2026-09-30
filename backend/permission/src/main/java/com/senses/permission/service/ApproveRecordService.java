package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.ApproveRecord;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.vo.VsfApproveVo;

import java.util.List;

/**
 * 审批记录表;(approve_record)表服务接口
 * @author : liaojinlei
 * @date : 2023-1-11
 */
public interface ApproveRecordService extends IService<ApproveRecord>{
    ResultData confirm(ApproveRecord approveRecordParam);

    /**
     * 撤回审批
     * @param vsfApproveVo
     * @return
     */
    ResultData cancelWorksheet(VsfApproveVo vsfApproveVo);
}