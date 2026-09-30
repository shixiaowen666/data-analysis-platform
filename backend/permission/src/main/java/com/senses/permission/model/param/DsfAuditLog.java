package com.senses.permission.model.param;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.util.Date;

import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.constant.ApproveStatusEnum;
import com.senses.permission.constant.AuthRoleTypeEnum;
import com.senses.permission.constant.BindTypeEnum;
import com.senses.permission.entity.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * <p>
 * 安全审计日志记录
 * </p>
 *
 * @author kxa
 * @since 2022-12-29
 */
@Data
public class DsfAuditLog implements Cloneable{

private static final long serialVersionUID=1L;

    /**
     * 自增主键
     */
    private Integer id;

    /**
     * 功能性ID，保存不同日志类型共通性数据ID
     */
    private Integer featureId;

    /**
     * 功能性名称，保存不同日志类型共通性名称数据
     */
    private String featureName;
    /**
     * 功能性英文名称，保存不同日志类型共通性名称数据
     */
    private String featureEnName;

    /**
     * 所属应用ID
     */
    private Integer applicationId;

    /**
     * 所属应用名称
     */
    private String applicationName;

    /**
     * 角色类型
     */
    private Integer roleType;

    /**
     * 授权对象
     */
    private String authObj;

    /**
     * 授权对象名称
     */
    private String authObjName;

    /**
     * 所属部门ID
     */
    private Integer deptId;

    /**
     * 所属部门名称
     */
    private String deptName;

    /**
     * 数据源类型ID
     */
    private Integer engineId;

    /**
     * 数据源类型名称
     */
    private String engineName;

    /**
     * 所属系统ID
     */
    private Integer systemId;

    /**
     * 所属系统名称
     */
    private String systemName;

    /**
     * 数据源ID
     */
    private Integer datasourceId;

    /**
     * 数据源名称
     */
    private String datasourceName;

    /**
     * 表ID
     */
    private Integer tableId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段中文名
     */
    private String tableComment;

    /**
     * 字段id
     */
    private Integer columnId;

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 字段中文名
     */
    private String columnComment;

    /**
     * 安全分级ID
     */
    private Integer levelId;

    /**
     * 安全分级名称
     */
    private String levelName;

    /**
     * 安全分类ID
     */
    private Integer classId;

    /**
     * 安全分类名称
     */
    private String className;

    /**
     * 是否敏感字段0-否1-是
     */
    private Integer isMask;

    /**
     * 读写权限
     */
    private Integer permission;

    /**
     * 权限有效期
     */
    private String permissionExpiryDate;

    /**
     * 提交时间
     */
    private String commitTime;

    /**
     * 用户IP
     */
    private String ip;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 鉴权状态
     */
    private String permissionStatus;

    /**
     * 是否返回数据0-否1-是
     */
    private Integer isReturn;

    /**
     * 查询表
     */
    private String tables;

    /**
     * 涉及敏感字段
     */
    private String maskColumn;

    /**
     * 执行sql
     */
    private String querySql;

    /**
     * 申请人
     */
    private String applyUser;

    /**
     * 申请时间
     */
    private String applyTime;

    /**
     * 授权人
     */
    private String authUser;

    /**
     * 授权时间
     */
    private String authTime;

    /**
     *
     */
    private String executeStatus;

    /**
     *日志类型:0-功能权限,1-数据权限,2-个人权限,3-查询日志
     */
    private Integer logType;

    /**
     * 个人权限授权级别 0-字段级 1-表级 2-库级 3-数据源类型级
     */
    private Integer selfType;

    /**
     * 审批状态 0审批中，1审批通过，2已驳回  3已撤回
     */
    private Integer approveStatus;

    /**
     * 授权类型 0个人申请1管理员授权
     */
    private Integer authRoleType;

    /**
     * 工单号
     */
    private String workOrderNo;

    public static DsfAuditLog newDeptRoleAuditLog(Dept dept, Role role,ApproveRecord approveRecord) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setFeatureId(role.getId().intValue());
        dsfAuditLog.setFeatureName(role.getCnName());
        dsfAuditLog.setFeatureEnName(role.getName());
        dsfAuditLog.setApplicationName(role.getAppName());
        dsfAuditLog.setApplicationId(role.getAppId().intValue());
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setAuthObj(BindTypeEnum.DEPT.getVal());
        dsfAuditLog.setAuthObjName(dept.getName());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setRoleType(role.getType());
        dsfAuditLog.setLogType(0);
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(getAuthroleTypeFromFlowTag(approveRecord.getFlowTag()));
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }

    public static DsfAuditLog newGroupRoleAuditLog(Group group, Role role,ApproveRecord approveRecord) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setFeatureId(role.getId().intValue());
        dsfAuditLog.setFeatureName(role.getCnName());
        dsfAuditLog.setFeatureEnName(role.getName());
        dsfAuditLog.setApplicationName(role.getAppName());
        dsfAuditLog.setApplicationId(role.getAppId().intValue());
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setAuthObj(BindTypeEnum.GROUP.getVal());
        dsfAuditLog.setAuthObjName(group.getGroupName());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setRoleType(role.getType());
        dsfAuditLog.setLogType(0);
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(getAuthroleTypeFromFlowTag(approveRecord.getFlowTag()));
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }

    public static DsfAuditLog newPersonRoleAuditLog(User user, Role role,ApproveRecord approveRecord) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setFeatureId(role.getId().intValue());
        dsfAuditLog.setFeatureName(role.getCnName());
        dsfAuditLog.setFeatureEnName(role.getName());
        dsfAuditLog.setApplicationName(role.getAppName());
        dsfAuditLog.setApplicationId(role.getAppId().intValue());
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setAuthObj(BindTypeEnum.PERSON.getVal());
        dsfAuditLog.setAuthObjName(user.getUsername());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setRoleType(role.getType());
        dsfAuditLog.setLogType(0);
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(getAuthroleTypeFromFlowTag(approveRecord.getFlowTag()));
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }
    public static DsfAuditLog newDeptDataRoleAuditLog(Dept dept, DataRole dataRole,ApproveRecord approveRecord) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setFeatureId(dataRole.getId().intValue());
        dsfAuditLog.setFeatureName(dataRole.getRemark());
        dsfAuditLog.setFeatureEnName(dataRole.getName());
        dsfAuditLog.setDeptId(dataRole.getDeptId().intValue());
        dsfAuditLog.setDeptName(dataRole.getDeptName());
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setAuthObj(BindTypeEnum.DEPT.getVal());
        dsfAuditLog.setAuthObjName(dept.getName());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setLogType(1);
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(getAuthroleTypeFromFlowTag(approveRecord.getFlowTag()));
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }

    public static DsfAuditLog newGroupDataRoleAuditLog(Group group, DataRole dataRole,ApproveRecord approveRecord) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setFeatureId(dataRole.getId().intValue());
        dsfAuditLog.setFeatureName(dataRole.getRemark());
        dsfAuditLog.setFeatureEnName(dataRole.getName());
        dsfAuditLog.setDeptId(dataRole.getDeptId().intValue());
        dsfAuditLog.setDeptName(dataRole.getDeptName());
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setAuthObj(BindTypeEnum.GROUP.getVal());
        dsfAuditLog.setAuthObjName(group.getGroupName());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setLogType(1);
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(getAuthroleTypeFromFlowTag(approveRecord.getFlowTag()));
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }

    public static DsfAuditLog newPersonDataRoleAuditLog(User user, DataRole dataRole,ApproveRecord approveRecord) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setFeatureId(dataRole.getId().intValue());
        dsfAuditLog.setFeatureName(dataRole.getRemark());
        dsfAuditLog.setFeatureEnName(dataRole.getName());
        dsfAuditLog.setDeptId(dataRole.getDeptId().intValue());
        dsfAuditLog.setDeptName(dataRole.getDeptName());
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setAuthObj(BindTypeEnum.PERSON.getVal());
        dsfAuditLog.setAuthObjName(user.getUsername());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setLogType(1);
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(getAuthroleTypeFromFlowTag(approveRecord.getFlowTag()));
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }

    public static DsfAuditLog newDataRolesPermissionAuditLog(DataRolesPermission dataRolesPermission,ApproveRecord approveRecord,Integer authRoleType) {
        DsfAuditLog dsfAuditLog = new DsfAuditLog();
        dsfAuditLog.setAuthUser(approveRecord.getApprover());
        dsfAuditLog.setApplyUser(approveRecord.getApplicant());
        dsfAuditLog.setLogType(2);
        dsfAuditLog.setDatasourceId(dataRolesPermission.getDatasourceId().intValue());
        dsfAuditLog.setDatasourceName(dataRolesPermission.getDatasourceName());
        dsfAuditLog.setTableName(dataRolesPermission.getTableName());
        dsfAuditLog.setEngineId(dataRolesPermission.getEngineId().intValue());
        dsfAuditLog.setEngineName(dataRolesPermission.getEngine());
        dsfAuditLog.setAuthTime(handleAuthTime(approveRecord));
        dsfAuditLog.setColumnName(dataRolesPermission.getColumnName());
        dsfAuditLog.setPermission(dataRolesPermission.getAction());
        if("*".equals(dataRolesPermission.getTableName().trim())){
            dsfAuditLog.setSelfType(2);
        }else if("*".equals(dataRolesPermission.getColumnName().trim())){
            dsfAuditLog.setSelfType(1);
        }else {
            dsfAuditLog.setSelfType(0);
        }
        dsfAuditLog.setApproveStatus(approveRecord.getApproveStatus());
        dsfAuditLog.setAuthRoleType(authRoleType);
        dsfAuditLog.setWorkOrderNo(approveRecord.getWorkOrderNo());
        return dsfAuditLog;
    }

    private static Integer getAuthroleTypeFromFlowTag(String flowTag) {
        if(ApproveFlowTagEnum.UPC_PER_DEPT_DATA_ROLE.getVal().equals(flowTag) || ApproveFlowTagEnum.UPC_PER_DEPT_DATA_ROLE.getVal().equals(flowTag)){
            return AuthRoleTypeEnum.PERSON.getId();
        }else{
            return AuthRoleTypeEnum.CONTROL.getId();
        }
    }

    private static String handleAuthTime(ApproveRecord approveRecord) {
        if(approveRecord.getApproveStatus() != ApproveStatusEnum.WAIT_APPROVE.getId()){
            return "";
        }
        return null;
    }
    @Override
    public DsfAuditLog clone() {
        try {
            DsfAuditLog clone = (DsfAuditLog) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

}
