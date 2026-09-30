package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.constant.ApproveStatusEnum;
import com.senses.permission.entity.ApproveRecord;
import com.senses.permission.mapper.ApproveRecordMapper;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.vo.VsfApproveVo;
import com.senses.permission.service.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 审批记录表;(approve_record)表服务实现类
 * @author : liaojinlei
 * @date : 2023-1-11
 */
@Service
public class ApproveRecordServiceImpl extends ServiceImpl<ApproveRecordMapper, ApproveRecord> implements ApproveRecordService{
    @Resource
    private ApproveRecordMapper approveRecordMapper;

    @Resource
    private UserService userService;

    @Resource
    private GroupService groupService;

    @Resource
    private DeptService deptService;

    @Resource
    private RoleService roleService;

    @Resource
    private DataRoleService dataRoleService;

    @Override
    public ResultData confirm(ApproveRecord approveRecordParam) {
        if(approveRecordParam == null){
            return ResultData.success();
        }
        ApproveRecord approveRecord = approveRecordMapper.selectById(approveRecordParam.getId());
        if(approveRecord == null || approveRecordParam.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            return ResultData.fail("审批状态不正确");
        }
        ApproveFlowTagEnum approveFlowTagEnum = ApproveFlowTagEnum.valueFromVal(approveRecord.getFlowTag());
        approveRecord.setApproveStatus(approveRecordParam.getApproveStatus());
        approveRecord.setApprover(approveRecordParam.getApprover());
        return updateApproveRecord(approveFlowTagEnum,approveRecord);
    }

    @Override
    public ResultData cancelWorksheet(VsfApproveVo vsfApproveVo) {
        if(vsfApproveVo == null){
            return ResultData.success();
        }
        ApproveRecord approveRecord = approveRecordMapper.selectById(vsfApproveVo.getId());
        if(approveRecord == null){
            return ResultData.fail("审批状态不正确");
        }
        ApproveFlowTagEnum approveFlowTagEnum = ApproveFlowTagEnum.valueFromVal(approveRecord.getFlowTag());
        approveRecord.setApproveStatus(ApproveStatusEnum.ROLLBACK.getId());
        approveRecord.setApprover(approveRecord.getApplicant());
        return updateApproveRecord(approveFlowTagEnum,approveRecord);

    }

    private ResultData updateApproveRecord(ApproveFlowTagEnum approveFlowTagEnum,ApproveRecord approveRecord) {
        switch (approveFlowTagEnum){
            case UPC_USER:
                userService.confirmApprove(approveRecord);
                break;
            case UPC_PER_USER_ROLE:
                userService.confirmApproveRole(approveRecord);
                break;
            case UPC_USER_GROUP:
                groupService.confirmApprove(approveRecord);
                break;
            case UPC_DEPT:
                deptService.confirmApprove(approveRecord);
                break;
            case UPC_PER_DEPT_DATA_ROLE:
                deptService.confirmApproveDataRole(approveRecord);
                break;
            case UPC_ROLE:
                roleService.confirmApprove(approveRecord);
                break;
            case UPC_DATA_ROLE:
            case UPC_APPEND_DATA_ROLE_FROM_META:
            case UPC_APPEND_DATA_ROLE_FROM_IDE:
                dataRoleService.confirmApprove(approveRecord);
                break;
            default:
                return ResultData.fail("未找到审批标志");
        }
        baseMapper.updateById(approveRecord);
        return ResultData.success();
    }
}