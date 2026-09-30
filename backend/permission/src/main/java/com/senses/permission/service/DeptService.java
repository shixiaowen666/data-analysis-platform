package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.*;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.DeptParam;
import com.senses.permission.model.vo.DeptRolesIdsVO;
import com.senses.permission.model.vo.UserVO;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 组织部门表;(application)表服务接口
 * @author : shinan
 * @date : 2022-12-8
 */
public interface DeptService extends IService<Dept> {

    ResultData saveOrUpdateDept(DeptParam dept, String userName);

    ResultData deleteDept(Long deptId, String userName);

    Dept getInfo(Long deptId);

    Dept getInfoByCode(String code);

    List<TreeData<Dept>> treeList(String deptName);

    ResultData bind(Long deptId, List<Long> roleIds, List<Long> dataRoleIds, String username);

    ResultData bindDataRole(Long deptId, List<Long> dataRoleIds,String username);

    List<TreeData<Role>> roleTreeList(Long detpId);

    List<TreeData<DataRole>> dataRoleTreeList(Long deptId);

    DeptRolesIdsVO getRoles(Long deptId);

    List<TreeData<User>> deptUserTreeList(String deptName);

    void confirmApprove(ApproveRecord approveRecord);

    Map<String,Object> getDeptsAndUsers(Long deptId);

    List<UserVO> getDeptUsers(Long deptId);

    void confirmApproveDataRole(ApproveRecord approveRecord);

    public Set<Dept> getAllParentDepts(List<Dept> deptList);

    List<Dept> getDeptsByIds(List<Long> deptIds);

    List<TreeData<User>> deptUserTreeListByTenantId();
}
