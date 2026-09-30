package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.entity.ApproveRecord;
import com.senses.permission.entity.DataRole;
import com.senses.permission.entity.Dept;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.dataroleVo.*;
import com.senses.permission.model.param.DataRoleBindUserPageParam;
import com.senses.permission.model.param.DataRolePermissionDetailParam;
import com.senses.permission.model.param.RoleBindUserPageParam;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 数据角色表;(data_role)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface DataRoleService extends IService<DataRole>{

    ResultData create(DataRoleVo resources);

    ResultData getDataRoleDetail(DataRolePermissionDetailParam dataRolePermissionDetailParam);

    ResultData deleteDataRole(Long id);

    ResultData getDataRoles();

    ResultData getDataRoles(PageParam<DataRoleQueryCriteria> criteria);

    ResultData bind(DataRoleRelationVo resources);

    List<TreeData<DataRole>> treeList(String username,Long userId,Long deptId, String filterVal, Integer status);

    ResultData getDataPermissions(PageParam<DataPermissionQueryCriteria> criteria);

    List<TreeData<DataRole>> installRoleTree(Collection<Dept> depts, List<DataRole> dataRoles);

    ResultData dataRoleChangeStatus(Long id, Integer status, String modifyUser);


    ResultData getBindUserListByPage(PageParam<DataRoleBindUserPageParam> pageParam);
    ResultData getDataPermissionListByPage(PageParam<DataPermissionQueryCriteria> criteria);

    ResultData deleteDataRolePermissions(String modifyUser, List<Long> permisssionIds);

    ResultData overwriteDateRole(DataRoleVo resources);

    void confirmApprove(ApproveRecord approveRecord);

    ResultData sendApproveInfo(DataRole dataRole, Set<DataPermissionVo> dataPermissions, String username,Integer authRoleType,ApproveFlowTagEnum approveFlowTagEnum);
}