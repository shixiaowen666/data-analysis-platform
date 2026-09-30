package com.senses.permission.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.constant.*;
import com.senses.permission.entity.*;
import com.senses.permission.mapper.*;
import com.senses.permission.model.*;
import com.senses.permission.model.param.ApproveWorksheet;
import com.senses.permission.model.param.DeptParam;
import com.senses.permission.model.vo.DeptRolesIdsVO;
import com.senses.permission.model.vo.DeptVO;
import com.senses.permission.model.vo.UserVO;
import com.senses.permission.service.*;
import com.senses.permission.service.client.SenseChatClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 组织部门表;(application)表服务实现类
 *
 * @author : shinan
 * @date : 2022-12-8
 */
@Service
@Slf4j
public class DeptServiceImpl extends ServiceImpl<DeptMapper, Dept> implements DeptService {

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private DataRoleMapper dataRoleMapper;

    @Resource
    private ApplicationMapper applicationMapper;

    @Resource
    private DeptsRolesMapper deptsRolesMapper;

    @Resource
    private DeptsRolesService deptsRolesService;

    @Resource
    private DeptsRolesDraftMapper deptsRolesDraftMapper;

    @Resource
    private DeptsRolesDraftService deptsRolesDraftService;

    @Resource
    private DeptsDataRolesMapper deptsDataRolesMapper;

    @Resource
    private DeptsDataRolesService deptsDataRolesService;

    @Resource
    private DeptsDataRolesDraftService deptsDataRolesDraftService;

    @Resource
    private DeptsDataRolesDraftMapper deptsDataRolesDraftMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UsersRolesMapper usersRolesMapper;

    @Resource
    private UsersDataRolesMapper usersDataRolesMapper;

    @Resource
    private UsersRolesDraftMapper usersRolesDraftMapper;

    @Resource
    private UsersDataRolesDraftMapper usersDataRolesDraftMapper;

    @Resource
    private ApproveRecordMapper approveRecordMapper;

    @Resource
    private RoleService roleService;

    @Resource
    private DataRoleService dataRoleService;

    @Resource
    private AsyncFeignService asyncFeignService;

    @Resource
    private FeignApproveDecoratorService feignApproveDecoratorService;

    @Resource
    private DeptMapper deptMapper;

    @Resource
    private SenseChatClient senseChatClient;

    @Override
    public ResultData saveOrUpdateDept(DeptParam deptParam, String userName) {
        if (StringUtils.isBlank(deptParam.getName())) {
            return ResultData.success("部门名称不能为空");
        }
        if (deptParam.getPid() != null && deptParam.getPid() != 0 && StringUtils.isBlank(deptParam.getCode())) {
            return ResultData.success("部门编码不能为空");
        } else {
            Dept existCode = baseMapper.selectByCode(deptParam.getCode());
            if (existCode != null && (deptParam.getId() == null || !deptParam.getId().equals(existCode.getId()))) {
                return ResultData.fail("部门编码已被使用");
            }
        }
        if (deptParam.getPid() != null && deptParam.getPid() != 0) {
            Dept parentDept = getById(deptParam.getPid());
            if (parentDept.getStatus() != CommonStatusEnum.TRUE.getId()) {
                return ResultData.success("部门“" + parentDept.getName() + "”已被禁用或删除，不能添加下级部门");
            }
        }
        Dept existName = baseMapper.selectByName(deptParam.getName());
        if (existName != null && (deptParam.getId() == null || !deptParam.getId().equals(existName.getId()))) {
            return ResultData.fail("部门名称已被使用");
        }

        Dept dept;
        if (deptParam.getId() != null) {
            dept = getById(deptParam.getId());
        } else {
            dept = new Dept();
            dept.setPid(deptParam.getPid() == null ? 0 : deptParam.getPid());
            dept.setStatus(CommonStatusEnum.TRUE.getId());
            dept.setCreatedTime(new Date());
            dept.setCreatedUser(userName);
        }
        dept.setCode(deptParam.getCode());
        dept.setName(deptParam.getName());
        dept.setModifyTime(new Date());
        dept.setModifyUser(userName);
        super.saveOrUpdate(dept);
        return ResultData.success();
    }

    @Override
    public List<TreeData<Dept>> treeList(String deptName) {
        QueryWrapper<Dept> qw = new QueryWrapper<>();
        if (StringUtils.isNotBlank(deptName)) {
            qw.like("name", deptName);
        }
        qw.eq("status", CommonStatusEnum.TRUE.getId());
        qw.orderByAsc("pid");
        List<Dept> deptList = this.list(qw);
        List<TreeData<Dept>> treeDataList = new LinkedList<>();
        if (CollectionUtils.isEmpty(deptList)) {
            return treeDataList;
        }
        Map<Long, List<Dept>> rootTreeMap = null;
        if (StringUtils.isNotBlank(deptName)) {
            Set<Dept> allList = getAllParentDepts(deptList);
            rootTreeMap = allList.stream().collect(Collectors.groupingBy(Dept::getPid));
        }else{
            rootTreeMap = deptList.stream().collect(Collectors.groupingBy(Dept::getPid));
        }

        List<Dept> rootDepts = rootTreeMap.get(0L);
        for (Dept rootDept : rootDepts) {
            int level = 1;
            TreeData<Dept> treeData = newTreeData(rootDept, level);
            insertTree(treeData, level + 1, rootTreeMap);
            treeDataList.add(treeData);
        }
        return treeDataList;
    }

    @Override
    public Set<Dept> getAllParentDepts(List<Dept> deptList) {
        Set<Dept> allDept = new TreeSet<>();
        if(!CollectionUtils.isEmpty(deptList)){
            allDept.addAll(deptList);
            List<Long> pids = deptList.stream().map(e->e.getPid()).collect(Collectors.toList());
            while(true){
                List<Dept> depts = baseMapper.selectBatchIds(pids);
                if(CollectionUtils.isEmpty(depts)){
                    break;
                }else{
                    allDept.addAll(depts);
                    pids = depts.stream().map(e->e.getPid()).collect(Collectors.toList());
                }
            }
        }
        return allDept;
    }

    private void insertTree(TreeData<Dept> treeData, int level, Map<Long, List<Dept>> rootTreeMap) {
        List<Dept> depts = rootTreeMap.get(treeData.getId());
        List<TreeData<Dept>> children = new LinkedList<>();
        if (!CollectionUtils.isEmpty(depts)) {
            for (Dept dept : depts) {
                TreeData<Dept> sonTreeData = newTreeData(dept, level);
                insertTree(sonTreeData, level + 1, rootTreeMap);
                children.add(sonTreeData);
            }
        }
        treeData.setChildren(children);
    }

    @Override
    public List<Dept> getDeptsByIds(List<Long> deptIds){
        QueryWrapper<Dept> qw = new QueryWrapper<>();
        qw.in("id",deptIds).eq("status",1);
        return baseMapper.selectList(qw);
    }

    @Override
    public List<TreeData<User>> deptUserTreeListByTenantId() {
        //先根据userId 获取对应的 部门数路径
        Long tenantId = null;
        LoginUser loginUser = UserThreadLocal.get();
        if (loginUser != null) {
            tenantId = loginUser.getTenantId();
        }
        ResultData<List<Long>> userIdsByTenantId = senseChatClient.getUserIdsByTenantId(tenantId);
        if (ReturnCode.RC1.getCode() == userIdsByTenantId.getCode()){
            //获取当前用户id
            if (CollectionUtils.isEmpty(userIdsByTenantId.getData())) {
                return Collections.emptyList();
            }else {
                List<Long> deptIds = baseMapper.selectRelatedDeptIds(userIdsByTenantId.getData());
                List<Dept> depts = baseMapper.selectBatchIds(deptIds);
                List<TreeData<Dept>> treeDataList = new LinkedList<>();
                Set<Dept> allList = getAllParentDepts(depts);
                Map<Long, List<Dept>>   rootTreeMap = allList.stream().collect(Collectors.groupingBy(Dept::getPid));

                List<Dept> rootDepts = rootTreeMap.get(0L);
                for (Dept rootDept : rootDepts) {
                    int level = 1;
                    TreeData<Dept> treeData = newTreeData(rootDept, level);
                    insertTree(treeData, level + 1, rootTreeMap);
                    treeDataList.add(treeData);
                }

                List<User> users = userMapper.selectBatchIds(userIdsByTenantId.getData());
                Map<Long, List<User>> deptUserMap = users.stream().collect(Collectors.groupingBy(User::getDeptId));
                List<TreeData<User>> deptUsersTreeList = new LinkedList<>();
                installDeptUserTreeList(treeDataList, deptUserMap, deptUsersTreeList, 1);
                return deptUsersTreeList;
            }

        }else {
            return Collections.emptyList();
        }

    }

    private TreeData<Dept> newTreeData(Dept dept, Integer level) {
        TreeData<Dept> treeData = new TreeData<>();
        treeData.setKey(level + "_" + dept.getId());
        treeData.setLabel(dept.getName());
        treeData.setId(dept.getId());
        treeData.setShowType("DEPT");
        treeData.setData(dept);
        return treeData;
    }


    @Override
    public ResultData deleteDept(Long deptId, String userName) {
        List<Long> allTreeDeptIds = new LinkedList<>();
        allTreeDeptIds.add(deptId);
        List<Long> pids = new LinkedList<>();
        pids.add(deptId);
        while (true) {
            QueryWrapper<Dept> qw = new QueryWrapper<>();
            qw.ne("status", CommonStatusEnum.DELETE.getId());
            qw.in("pid", pids);
            List<Dept> lowLevelDepts = list(qw);
            if (!CollectionUtils.isEmpty(lowLevelDepts)) {
                List<Long> lowLevelDeptIds = lowLevelDepts.stream().map(e -> e.getId()).collect(Collectors.toList());
                allTreeDeptIds.addAll(lowLevelDeptIds);
                pids = lowLevelDeptIds;
            } else {
                break;
            }
        }
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.ne("status", CommonStatusEnum.DELETE.getId());
        qw.in("dept_id", allTreeDeptIds);
        List<User> deptsUsers = userMapper.selectList(qw);
        if (!CollectionUtils.isEmpty(deptsUsers)) {
            return ResultData.fail("删除失败：部门或其下子部门，有挂靠用户");
        }

        UpdateWrapper<Dept> duw = new UpdateWrapper<>();
        duw.set("status", CommonStatusEnum.DELETE.getId());
        duw.set("modify_time", new Date());
        duw.set("modify_user", userName);
        duw.in("id", allTreeDeptIds);
        this.update(duw);

        UpdateWrapper<DeptsRoles> deleteDeptRoles = new UpdateWrapper<>();
        deleteDeptRoles.in("dept_id", allTreeDeptIds);
        deptsRolesMapper.delete(deleteDeptRoles);

        UpdateWrapper<DeptsDataRoles> deleteDeptDataRoles = new UpdateWrapper<>();
        deleteDeptDataRoles.in("dept_id", allTreeDeptIds);
        deptsDataRolesMapper.delete(deleteDeptDataRoles);
        deleteDeptRoleAndDataRoleDraft(allTreeDeptIds,null,null);
        return ResultData.success();
    }

    @Override
    public Dept getInfo(Long deptId) {
        Dept dept = super.getById(deptId);
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_DEPT.getVal(), deptId);
        if (approveRecord != null) {
            dept.setApproveStatus(approveRecord.getApproveStatus());
        }
        return dept;
    }

    @Override
    public Dept getInfoByCode(String code) {
        Dept dept = deptMapper.selectByCode(code);
        return dept;
    }

    @Override
    public ResultData bind(Long deptId, List<Long> roleIds, List<Long> dataRoleIds, String username) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_DEPT.getVal(), deptId);
        if (approveRecord != null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()) {
            return ResultData.fail("存在审批中的部门绑定权限申请，请先联系审批中心，处理上次申请！");
        }
        Dept dept = baseMapper.selectById(deptId);
        if (dept.getStatus() == CommonStatusEnum.DELETE.getId()) {
            return ResultData.fail("组织部门名称已被删除，不能再授权");
        }
        List<Role> roles = null;
        List<DataRole> dataRoles = null;
        StringBuilder errorRoles = new StringBuilder();
        if (!CollectionUtils.isEmpty(roleIds)) {
            roles = roleMapper.appRoleListByIds(roleIds);
            List<String> deleteRoleName = roles.stream().filter(e -> e.getStatus() == CommonStatusEnum.DELETE.getId()).map(e -> e.getName()).collect(Collectors.toList());
            List<String> falseRoleName = roles.stream().filter(e -> e.getStatus() == CommonStatusEnum.FALSE.getId()).map(e -> e.getName()).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(deleteRoleName)) {
                errorRoles.append("以下角色已删除，无法授权：" + String.join("，", deleteRoleName));
            }
            if (!CollectionUtils.isEmpty(falseRoleName)) {
                if (errorRoles.length() != 0) {
                    errorRoles.append("；");
                }
                errorRoles.append("以下角色已禁用，无法授权：" + String.join("，", falseRoleName) + "。");
            }
            if (errorRoles.length() != 0) {
                return ResultData.fail("授权错误：" + errorRoles);
            }
        }
        if (!CollectionUtils.isEmpty(dataRoleIds)) {
            dataRoles = dataRoleMapper.deptDataRoleListByIds(dataRoleIds);
            List<String> deleteDataRoleName = dataRoles.stream().filter(e -> e.getStatus() == CommonStatusEnum.DELETE.getId()).map(e -> e.getName()).collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(deleteDataRoleName)) {
                errorRoles.append("以下数据角色已删除，无法授权：" + String.join("，", deleteDataRoleName));
            }
            List<String> falseDataRoleName = dataRoles.stream().filter(e -> e.getStatus() == CommonStatusEnum.FALSE.getId()).map(e -> e.getName()).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(falseDataRoleName)) {
                if (errorRoles.length() != 0) {
                    errorRoles.append("；");
                }
                errorRoles.append("以下数据角色已禁用，无法授权：" + String.join("，", falseDataRoleName) + "。");
            }
            if (errorRoles.length() != 0) {
                return ResultData.fail("授权错误：" + errorRoles);
            }
        }
        String workName = dept.getName() + "【部门绑定角色申请】";
        ApproveRecord newApproveRecord = ApproveRecord.createApprove(deptId, ApproveFlowTagEnum.UPC_DEPT, username, workName);
        approveRecordMapper.insert(newApproveRecord);
        addDeptsRolesDraft(deptId, roleIds);
        addDeptsDataRolesDraft(deptId,dataRoleIds,ApplicantTypeEnum.CONTROL.getId());
        String worksheetData = ApproveWorksheet.createRolesAndDataRoleWorksheetData(roles,dataRoles);
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(newApproveRecord.getId().toString(),workName,username,worksheetData, BindMarkEnum.UPC_BINDROLE_DEPT.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,"部门" + dept.getName() + "授权发送申请单返回结果：");
        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteDeptRoleDrafts(deptId,roleIds);
            deleteDeptDataRoleDrafts(deptId,dataRoleIds);
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getMessage());
        }

        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        newApproveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(newApproveRecord);
        //向审计中心发送日志记录
        asyncFeignService.addDeptBindDataRoleAuditLog(dept,dataRoleIds,newApproveRecord);
        asyncFeignService.addDeptBindRoleAuditLog(dept,roleIds,newApproveRecord);

        return ResultData.success();
    }

    private void deleteDeptRoleDrafts(Long deptId,List<Long> roleIds) {
        if(!CollectionUtils.isEmpty(roleIds)){
            UpdateWrapper<DeptsRolesDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("dept_id",deptId);
            updateWrapper.in("role_id",roleIds);
            deptsRolesDraftMapper.delete(updateWrapper);
        }

    }
    private void deleteDeptDataRoleDrafts(Long deptId,List<Long> dataRoleIds) {
        if(!CollectionUtils.isEmpty(dataRoleIds)){
            UpdateWrapper<DeptsDataRolesDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("dept_id",deptId);
            updateWrapper.in("data_role_id",dataRoleIds);
            deptsDataRolesDraftMapper.delete(updateWrapper);
        }
    }

    @Override
    public ResultData bindDataRole(Long deptId, List<Long> dataRoleIds,String username) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_PER_DEPT_DATA_ROLE.getVal(),deptId);
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            if(approveRecord.getApplicant().equals(username)){
                return ResultData.fail("存在审批中的部门绑定数据角色申请，请先联系审批中心，处理上次申请！");
            }else{
                return ResultData.fail("存在审批中的其他用户申请的部门绑定数据角色，请先联系审批中心，处理相关申请！");
            }
        }
        Dept dept = baseMapper.selectById(deptId);
        if (dept.getStatus() == CommonStatusEnum.DELETE.getId()) {
            return ResultData.fail("组织部门名称已被删除，不能再授权");
        }
        List<DataRole> dataRoles = null;
        StringBuilder errorRoles = new StringBuilder();
        if (!CollectionUtils.isEmpty(dataRoleIds)) {
            dataRoles = dataRoleMapper.deptDataRoleListByIds(dataRoleIds);
            List<String> deleteDataRoleName = dataRoles.stream().filter(e -> e.getStatus() == CommonStatusEnum.DELETE.getId()).map(e -> e.getName()).collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(deleteDataRoleName)) {
                errorRoles.append("以下数据角色已删除，无法授权：" + String.join("，", deleteDataRoleName));
            }
            List<String> falseDataRoleName = dataRoles.stream().filter(e -> e.getStatus() == CommonStatusEnum.FALSE.getId()).map(e -> e.getName()).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(falseDataRoleName)) {
                if (errorRoles.length() != 0) {
                    errorRoles.append("；");
                }
                errorRoles.append("以下数据角色已禁用，无法授权：" + String.join("，", falseDataRoleName) + "。");
            }
            if (errorRoles.length() != 0) {
                return ResultData.fail("授权错误：" + errorRoles);
            }
        }
        String workName =dept.getName()+"【部门授权】";
        ApproveRecord newApproveRecord = ApproveRecord.createApprove(deptId,ApproveFlowTagEnum.UPC_PER_DEPT_DATA_ROLE,username,workName);
        approveRecordMapper.insert(newApproveRecord);

        addDeptsDataRolesDraft(deptId,dataRoleIds, ApplicantTypeEnum.PERSON.getId());
        String worksheetData = ApproveWorksheet.createRolesAndDataRoleWorksheetData(null,dataRoles);
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(newApproveRecord.getId().toString(),workName,username,worksheetData,BindMarkEnum.UPC_BINDROLE_DEPT.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,"部门" + dept.getName() + "授权发送申请单返回结果：");
        log.info("部门" + dept.getName() + "授权发送申请单返回结果：" + JSON.toJSONString(sendApproveResult));
        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteDeptDataRoleDrafts(deptId,dataRoleIds);
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getMessage());
        }
        //向审计中心发送日志记录
        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        newApproveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(newApproveRecord);
        asyncFeignService.addDeptBindDataRoleAuditLog(dept,dataRoleIds,newApproveRecord);
        return ResultData.success();
    }


    private void addDeptsDataRolesDraft(Long deptId, List<Long> dataRoleIds,Integer applicantType) {
        if (!CollectionUtils.isEmpty(dataRoleIds)) {
            List<DeptsDataRolesDraft> deptsDataRolesList = new ArrayList<>(dataRoleIds.size());
            for (Long addId : dataRoleIds) {
                DeptsDataRolesDraft deptsDataRoles = new DeptsDataRolesDraft();
                deptsDataRoles.setDeptId(deptId);
                deptsDataRoles.setDataRoleId(addId);
                deptsDataRoles.setApplicantType(applicantType);
                deptsDataRolesList.add(deptsDataRoles);
            }
            deptsDataRolesDraftService.saveBatch(deptsDataRolesList);
        }
    }

    private void addDeptsRolesDraft(Long deptId, List<Long> roleIds) {
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<DeptsRolesDraft> deptsRolesList = new ArrayList<>(roleIds.size());
            for (Long addId : roleIds) {
                DeptsRolesDraft deptsRoles = new DeptsRolesDraft();
                deptsRoles.setDeptId(deptId);
                deptsRoles.setRoleId(addId);
                deptsRolesList.add(deptsRoles);
            }
            deptsRolesDraftService.saveBatch(deptsRolesList);
        }
    }

    @Override
    public List<TreeData<Role>> roleTreeList(Long deptId) {
        List<Application> deptApplication = applicationMapper.selectByDeptId(deptId);
        List<Role> roles = roleMapper.selectByDeptId(deptId);
        List<TreeData<Role>> treeDatas = roleService.installAppRoleTree(deptApplication, roles);
        return treeDatas;
    }

    @Override
    public List<TreeData<DataRole>> dataRoleTreeList(Long deptId) {
        Dept dept = this.getById(deptId);
        List<Dept> depts = new ArrayList<>(1);
        depts.add(dept);
        Long pid = dept.getPid();
        while (true) {
            if (pid == 0) {
                break;
            }
            Dept parentDept = baseMapper.selectById(pid);
            depts.add(parentDept);
            pid = parentDept.getPid();
        }
        List<DataRole> dataRoles = dataRoleMapper.selectByDeptId(deptId);
        List<TreeData<DataRole>> treeDatas = dataRoleService.installRoleTree(depts, dataRoles);
        return treeDatas;
    }

    @Override
    public DeptRolesIdsVO getRoles(Long deptId) {
        List<Role> roles = roleMapper.selectByDeptId(deptId);
        List<DataRole> dataRoles = dataRoleMapper.selectByDeptId(deptId);
        List<Long> roleIds;
        List<Long> dataRoleIds;
        if (roles == null) {
            roles = new LinkedList<>();
        }
        if (dataRoles == null) {
            dataRoles = new LinkedList<>();
        }
        roleIds = roles.stream().map(e -> e.getId()).collect(Collectors.toList());
        dataRoleIds = dataRoles.stream().map(e -> e.getId()).collect(Collectors.toList());
        DeptRolesIdsVO deptRolesIdsVO = new DeptRolesIdsVO();
        deptRolesIdsVO.setRoleIds(roleIds);
        deptRolesIdsVO.setDataRoleIds(dataRoleIds);
        return deptRolesIdsVO;
    }


    private void addOrUpdateDeptsRoles(Dept dept, List<Long> userIds, ApproveRecord approveRecord) {
        UpdateWrapper<DeptsRoles> deleteDeptsRoles = new UpdateWrapper<>();
        deleteDeptsRoles.eq("dept_id", dept.getId());
        deptsRolesMapper.delete(deleteDeptsRoles);

        List<DeptsRolesDraft> deptsRolesDrafts = deptsRolesDraftMapper.selectByDeptId(dept.getId());
        if (CollectionUtils.isEmpty(deptsRolesDrafts)) {
            return;
        }
        List<Long> roleIds = deptsRolesDrafts.stream().map(e -> e.getRoleId()).collect(Collectors.toList());
        List<DeptsRoles> deptsRolesList = new ArrayList<>(roleIds.size());
        for (Long addId : roleIds) {
            DeptsRoles deptsRoles = new DeptsRoles();
            deptsRoles.setDeptId(dept.getId());
            deptsRoles.setRoleId(addId);
            deptsRolesList.add(deptsRoles);
        }
        deptsRolesService.saveBatch(deptsRolesList);
        if (!CollectionUtils.isEmpty(userIds)) {
            UpdateWrapper<UsersRoles> deleteUsersRoles = new UpdateWrapper<>();
            deleteUsersRoles.in("role_id", roleIds);
            deleteUsersRoles.in("user_id", userIds);
            usersRolesMapper.delete(deleteUsersRoles);

            UpdateWrapper<UsersRolesDraft> deleteUsersRolesDraft = new UpdateWrapper<>();
            deleteUsersRolesDraft.in("role_id", roleIds);
            deleteUsersRolesDraft.in("user_id", userIds);
            usersRolesDraftMapper.delete(deleteUsersRolesDraft);
        }
        asyncFeignService.addDeptBindRoleAuditLog(dept, roleIds, approveRecord);
    }
    private void addOrUpdateDeptsDataRoles(Dept dept,List<Long> userIds,ApproveRecord approveRecord,Integer applicantType) {
        if(ApplicantTypeEnum.CONTROL.getId() == applicantType){
            UpdateWrapper<DeptsDataRoles> deleteDeptsDataRoles = new UpdateWrapper<>();
            deleteDeptsDataRoles.eq("dept_id", dept.getId());
            deptsDataRolesMapper.delete(deleteDeptsDataRoles);
        }

        List<DeptsDataRolesDraft> deptsRolesDrafts = deptsDataRolesDraftMapper.selectByDeptIdAndApplicantType(dept.getId(),applicantType);
        if (CollectionUtils.isEmpty(deptsRolesDrafts)) {
            return;
        }
        List<Long> dataRoleIds = deptsRolesDrafts.stream().map(e -> e.getDataRoleId()).collect(Collectors.toList());

        List<DeptsDataRoles> deptsDataRolesList = new ArrayList<>(dataRoleIds.size());
        for (Long addId : dataRoleIds) {
            DeptsDataRoles deptsDataRoles = new DeptsDataRoles();
            deptsDataRoles.setDeptId(dept.getId());
            deptsDataRoles.setDataRoleId(addId);
            deptsDataRolesList.add(deptsDataRoles);
        }
        deptsDataRolesService.saveBatch(deptsDataRolesList);
        if (!CollectionUtils.isEmpty(userIds)) {
            UpdateWrapper<UsersDataRoles> deleteUsersDataRoles = new UpdateWrapper<>();
            deleteUsersDataRoles.in("data_role_id", dataRoleIds);
            deleteUsersDataRoles.in("user_id", userIds);
            usersDataRolesMapper.delete(deleteUsersDataRoles);

            UpdateWrapper<UsersDataRolesDraft> deleteUsersDataRolesDraft = new UpdateWrapper<>();
            deleteUsersDataRolesDraft.in("data_role_id", dataRoleIds);
            deleteUsersDataRolesDraft.in("user_id", userIds);
            usersDataRolesDraftMapper.delete(deleteUsersDataRolesDraft);
        }
        asyncFeignService.addDeptBindDataRoleAuditLog(dept, dataRoleIds, approveRecord);
    }

    @Override
    public List<TreeData<User>> deptUserTreeList(String deptName) {
        List<TreeData<Dept>> deptTreeList = treeList(deptName);
        List<User> users = userMapper.trueAllUserList();
        Map<Long, List<User>> deptUserMap = users.stream().collect(Collectors.groupingBy(User::getDeptId));
        List<TreeData<User>> deptUsersTreeList = new LinkedList<>();
        installDeptUserTreeList(deptTreeList, deptUserMap, deptUsersTreeList, 1);
        return deptUsersTreeList;
    }

    private void installDeptUserTreeList(List<TreeData<Dept>> deptTreeList, Map<Long, List<User>> deptUserMap, List<TreeData<User>> deptUsersTreeList, int level) {
        if (!CollectionUtils.isEmpty(deptTreeList)) {
            for (TreeData<Dept> deptTreeData : deptTreeList) {
                TreeData<User> userTreeData = TreeData.copyBaseInfo(deptTreeData);
                List<TreeData<User>> userTreeList = new LinkedList<>();
                List<User> users = deptUserMap.get(deptTreeData.getId());
                if (!CollectionUtils.isEmpty(users)) {
                    for (User user : users) {
                        TreeData<User> userData = newUserTreeData(user, level + 1, deptTreeData.getData().getId());
                        userTreeList.add(userData);
                    }
                }
                List<TreeData<Dept>> deptSonTreeList = deptTreeData.getChildren();
                if (!CollectionUtils.isEmpty(deptSonTreeList)) {
                    installDeptUserTreeList(deptSonTreeList, deptUserMap, userTreeList, level + 1);
                }
                userTreeData.setChildren(userTreeList);
                deptUsersTreeList.add(userTreeData);
            }
        }
    }

    private TreeData<User> newUserTreeData(User user, int level, Long deptId) {
        TreeData<User> treeData = new TreeData<>();
        treeData.setId(user.getId());
        treeData.setLabel(user.getUsername());
        treeData.setKey(level + "_" + deptId + "_" + user.getId());
        treeData.setShowType("User");
        treeData.setData(user);
        return treeData;
    }

    @Override
    public void confirmApprove(ApproveRecord approveRecord) {
        Long deptId = approveRecord.getApproveTargetId();
        Dept dept = super.getById(deptId);
        List<Long> deptIds = new LinkedList<>();
        deptIds.add(deptId);
        if (approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()) {
            List<User> users = userMapper.nodeleteListByDeptId(deptId);
            List<Long> userIds = null;
            if (!CollectionUtils.isEmpty(users)) {
                userIds = users.stream().map(e -> e.getId()).collect(Collectors.toList());
            }
            addOrUpdateDeptsRoles(dept,userIds,approveRecord);
            addOrUpdateDeptsDataRoles(dept,userIds,approveRecord,ApplicantTypeEnum.CONTROL.getId());
            deleteDeptRoleAndDataRoleDraft(deptIds,approveRecord,ApplicantTypeEnum.CONTROL.getId());
        }else{
            deleteDeptRoleAndDataRoleDraft(deptIds,approveRecord,ApplicantTypeEnum.CONTROL.getId());
        }

    }

    @Override
    public void confirmApproveDataRole(ApproveRecord approveRecord) {
        Long deptId = approveRecord.getApproveTargetId();
        Dept dept = super.getById(deptId);
        List<Long> deptIds = new LinkedList<>();
        deptIds.add(deptId);
        if(approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()){
            List<User> users = userMapper.nodeleteListByDeptId(deptId);
            List<Long> userIds = null;
            if(!CollectionUtils.isEmpty(users)){
                userIds = users.stream().map(e->e.getId()).collect(Collectors.toList());
            }
            addOrUpdateDeptsDataRoles(dept,userIds,approveRecord,ApplicantTypeEnum.PERSON.getId());
            deleteDeptDataRoleDraft(deptIds,approveRecord,ApplicantTypeEnum.PERSON.getId());
        }else{
            deleteDeptDataRoleDraft(deptIds,approveRecord,ApplicantTypeEnum.PERSON.getId());
        }

    }

    @Override
    public Map<String, Object> getDeptsAndUsers(Long deptId) {
        Map<String, Object> map = new HashMap<>();
        List<DeptVO> depts = baseMapper.selectByPid(deptId);
        List<UserVO> users = userMapper.selectByDeptId(deptId);
        map.put("depts", null == depts ? new ArrayList() : depts);
        map.put("users", null == users ? new ArrayList() : users);
        return map;
    }

    @Override
    public List<UserVO> getDeptUsers(Long deptId) {
        List<UserVO> users = userMapper.selectByDeptId(deptId);
        return null==users?new ArrayList<>():users;
    }

    private void deleteDeptRoleAndDataRoleDraft(List<Long> deptIds,ApproveRecord approveRecord,Integer applicantType) {
        deleteDeptRoleDraft(deptIds,approveRecord);
        deleteDeptDataRoleDraft(deptIds,approveRecord,applicantType);
    }

    private void deleteDeptRoleDraft(List<Long> deptIds, ApproveRecord approveRecord) {
        if(!CollectionUtils.isEmpty(deptIds) && approveRecord!= null && approveRecord.getApproveStatus()==ApproveStatusEnum.NO_APPROVED.getId()){
            for(Long deptId:deptIds){
                Dept dept = baseMapper.selectById(deptId);
                List<DeptsRolesDraft> deptsRolesDrafts = deptsRolesDraftMapper.selectByDeptId(deptId);
                if (!CollectionUtils.isEmpty(deptsRolesDrafts)) {
                    List<Long> roleIds = deptsRolesDrafts.stream().map(e -> e.getRoleId()).collect(Collectors.toList());
                    asyncFeignService.addDeptBindRoleAuditLog(dept, roleIds, approveRecord);
                }
            }
        }
        UpdateWrapper<DeptsRolesDraft> deleteDeptRolesDraft = new UpdateWrapper<>();
        deleteDeptRolesDraft.in("dept_id",deptIds);
        deptsRolesDraftMapper.delete(deleteDeptRolesDraft);

    }
    private void deleteDeptDataRoleDraft(List<Long> deptIds, ApproveRecord approveRecord,Integer applicantType) {
        if(!CollectionUtils.isEmpty(deptIds) && approveRecord!= null && approveRecord.getApproveStatus()==ApproveStatusEnum.NO_APPROVED.getId()){
            for(Long deptId:deptIds){
                Dept dept = baseMapper.selectById(deptId);
                List<DeptsDataRolesDraft> deptsDateRolesDrafts = deptsDataRolesDraftMapper.selectByDeptId(deptId);
                if (!CollectionUtils.isEmpty(deptsDateRolesDrafts)) {
                    List<Long> dataRoleIds = deptsDateRolesDrafts.stream().map(e -> e.getDataRoleId()).collect(Collectors.toList());
                    asyncFeignService.addDeptBindDataRoleAuditLog(dept, dataRoleIds, approveRecord);
                }
            }
        }
        UpdateWrapper<DeptsDataRolesDraft> deleteDeptDataRolesDraft = new UpdateWrapper<>();
        deleteDeptDataRolesDraft.in("dept_id", deptIds);
        if(applicantType!=null){
            deleteDeptDataRolesDraft.eq("applicant_type",applicantType);
        }
        deptsDataRolesDraftMapper.delete(deleteDeptDataRolesDraft);

    }

}