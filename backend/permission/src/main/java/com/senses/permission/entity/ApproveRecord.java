package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.constant.ApproveStatusEnum;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审批记录表;
 * @author : liaojinlei
 * @date : 2023-1-11
 */
@Schema(description = "审批记录表")
@TableName("approve_record")
@Data
public class ApproveRecord{
    
    /** id */
    @Schema(description = "id")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 所属业务标志 upcUser，upcRole，upcDataRole，upcDept，upcUserGroup */
    @Schema(description = "所属业务标志 upcUser，upcRole，upcDataRole，upcDept，upcUserGroup")
    @TableField("flow_tag")
    private String flowTag;
    
    /** 事件id，功能，用户，用户组，组织等id */
    @Schema(description = "事件id，功能，用户，用户组，组织等id")
    @TableField("approve_target_id")
    private Long approveTargetId;
    
    /** 申请人 */
    @Schema(description = "申请人")
    @TableField("applicant")
    private String applicant;
    
    /** 审批人 */
    @Schema(description = "审批人")
    @TableField("approver")
    private String approver;

    /** 任务名 */
    @Schema(description = "任务名")
    @TableField("flow_name")
    private String flowName;
    
    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField("approve_status")
    private Integer approveStatus;
    
    /** 创建时间 */
    @Schema(description = "创建时间")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 修改时间 */
    @Schema(description = "修改时间")
    @TableField("modify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

    /** 工单号 */
    @Schema(description = "工单号")
    @TableField("work_order_no")
    private String workOrderNo;

    public static ApproveRecord createApprove(Long approveTargetId, ApproveFlowTagEnum approveFlowTagEnum,String username,String flowName) {
        ApproveRecord approveRecord = new ApproveRecord();
        approveRecord.setApproveStatus(ApproveStatusEnum.WAIT_APPROVE.getId());
        approveRecord.setFlowTag(approveFlowTagEnum.getVal());
        approveRecord.setApplicant(username);
        approveRecord.setApproveTargetId(approveTargetId);
        approveRecord.setFlowName(flowName);
        approveRecord.setCreatedTime(new Date());
        return approveRecord;
    }
}