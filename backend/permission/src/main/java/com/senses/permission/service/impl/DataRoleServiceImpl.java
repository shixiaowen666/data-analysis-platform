package com.senses.permission.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.senses.permission.constant.*;
import com.senses.permission.entity.*;
import com.senses.permission.mapper.*;
import com.senses.permission.model.*;
import com.senses.permission.model.dataroleVo.*;
import com.senses.permission.model.param.ApproveWorksheet;
import com.senses.permission.model.param.DataRoleBindUserPageParam;
import com.senses.permission.model.param.DataRolePermissionDetailParam;
import com.senses.permission.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据角色表;(data_role)表服务实现类
 *
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
@Slf4j
public class DataRoleServiceImpl extends ServiceImpl<DataRoleMapper, DataRole> implements DataRoleService {
    @Resource
    private DataRoleMapper dataRoleMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private DataRolesPermissionService dataRolesPermissionService;
    @Resource
    private DataRolesPermissionMapper dataRolesPermissionMapper;
    @Resource
    private UsersDataRolesMapper usersDataRolesMapper;
    @Resource
    private GroupsDataRolesMapper groupsDataRolesMapper;
    @Resource
    private DeptsDataRolesMapper deptsDataRolesMapper;
    @Resource
    private ApproveRecordMapper approveRecordMapper;
    @Resource
    private UserMapper userMapper;

    @Resource
    private AsyncFeignService asyncFeignService;
    @Resource
    private FeignApproveDecoratorService feignApproveDecoratorService;
    @Resource
    private DataRolesPermissionDraftService dataRolesPermissionDraftService;
    @Resource
    private DataRolesPermissionDraftMapper dataRolesPermissionDraftMapper;

    @Override
    public ResultData create(DataRoleVo resources) {
        try {
            DataRole dataRole = new DataRole();
            Date date = new Date();
            BeanUtils.copyProperties(resources, dataRole);
            dataRole.setStatus(PermissionConstant.DATA_STATUS_ENABLE);
            dataRole.setCreatedTime(date);
            dataRole.setModifyTime(date);
            int insert = dataRoleMapper.insert(dataRole);
            return sendApproveInfo(dataRole,resources.getDataPermissions(),resources.getCreatedUser(),AuthRoleTypeEnum.CONTROL.getId(),ApproveFlowTagEnum.UPC_DATA_ROLE);
        } catch (BeansException e) {
            log.info("",e);
            return ResultData.fail(e.getMessage());
        }
    }

    @Override
    public ResultData sendApproveInfo(DataRole dataRole,Set<DataPermissionVo> dataPermissions, String username,Integer authRoleType,ApproveFlowTagEnum approveFlowTagEnum) {
        String workName = null;
        String logInfoPre = null;

        BindMarkEnum bindMarkEnum = null;
        if(authRoleType.equals(AuthRoleTypeEnum.CONTROL.getId())){
            workName = dataRole.getName()+"【数据角色授权】";
            logInfoPre = "数据角色"+dataRole.getName()+"绑定数据权限授权发送申请单返回结果：";
            bindMarkEnum = BindMarkEnum.UPC_BINDPERMISSION_DATAROLE;
        }else{
            if(ApproveFlowTagEnum.UPC_APPEND_DATA_ROLE_FROM_META.equals(approveFlowTagEnum)){
                workName = username+"【数据权限申请】";
                logInfoPre = "用户"+username+"元数据获取单表数据权限授权发送申请单返回结果：";
//                bindMarkEnum = BindMarkEnum.UPC_META_DATAPERMISSION;
                bindMarkEnum = BindMarkEnum.UPC_VIEWMETA_TABLEPERMISSION;
            }else if(ApproveFlowTagEnum.UPC_APPEND_DATA_ROLE_FROM_IDE.equals(approveFlowTagEnum)){
                workName = username+"【表级数据权限申请】";
                logInfoPre = "用户"+username+"即席查询获取单表数据权限授权发送申请单返回结果：";
//                bindMarkEnum = BindMarkEnum.UPC_VIEWMETA_TABLEPERMISSION;
                bindMarkEnum = BindMarkEnum.UPC_META_DATAPERMISSION;
            }else {
                workName = username+"【个人数据权限申请】";
                logInfoPre = "用户"+username+"个人数据权限授权发送申请单返回结果：";
                bindMarkEnum = BindMarkEnum.UPC_BINDPERMISSION_PERSONDATAROLE;
            }
        }

        ApproveRecord approveRecord = ApproveRecord.createApprove(dataRole.getId(), approveFlowTagEnum, username,workName);
        approveRecordMapper.insert(approveRecord);
        List<DataRolesPermissionDraft> dataRolesPermissionDrafts = addDataRolesPermissionDraft(dataRole, dataPermissions,approveRecord.getCreatedTime());
        String worksheetData = ApproveWorksheet.createDataPermissionsWorksheetData(dataPermissions);
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(approveRecord.getId().toString(),workName,username,worksheetData, bindMarkEnum.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,logInfoPre);
        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            if(!CollectionUtils.isEmpty(dataRolesPermissionDrafts)){
                List<Long> draftsIds =  dataRolesPermissionDrafts.stream().map(e->e.getId()).collect(Collectors.toList());
                dataRolesPermissionDraftMapper.deleteBatchIds(draftsIds);
            }
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getMessage());
        }
        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        approveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(approveRecord);
        //向审计中心发送日志记录
        addDataRolePermissionAuditLog(dataRole,dataPermissions,approveRecord,authRoleType);
        return ResultData.success();
    }

    private void addDataRolePermissionAuditLog(DataRole dataRole,Set<DataPermissionVo> dataPermissions,ApproveRecord approveRecord,Integer authRoleType){
        List<DataRolesPermission> dataRolesPermissions = new ArrayList<>(dataPermissions.size());
        for(DataPermissionVo dataPermissionVo:dataPermissions){
            DataRolesPermission dataRolesPermission = new DataRolesPermission();
            BeanUtils.copyProperties(dataPermissionVo, dataRolesPermission);
            if(dataPermissionVo.getCreatedTime() == null){
                dataRolesPermission.setCreatedTime(approveRecord.getCreatedTime());
            }
            if(StringUtils.isBlank(dataPermissionVo.getCreatedUser())){
                dataRolesPermission.setCreatedUser(dataRole.getModifyUser());
            }
            if(StringUtils.isBlank(dataPermissionVo.getModifyUser())){
                dataRolesPermission.setModifyUser(dataRole.getModifyUser());
            }
            if(dataPermissionVo.getModifyTime() == null){
                dataRolesPermission.setModifyTime(approveRecord.getCreatedTime());
            }
            dataRolesPermission.setDataRoleId(dataRole.getId());
            dataRolesPermissions.add(dataRolesPermission);
        }
        asyncFeignService.addDataRolePermissionAuditLog(dataRole,dataRolesPermissions,approveRecord,authRoleType);
    }

    private List<DataRolesPermissionDraft> addDataRolesPermissionDraft(DataRole dataRole, Set<DataPermissionVo> dataPermissions,Date date) {
        List<DataRolesPermissionDraft> dataRolesPermissionDrafts = new ArrayList<>(dataPermissions.size());
        if(!CollectionUtils.isEmpty(dataPermissions)){

            for(DataPermissionVo dataPermissionVo:dataPermissions){
                DataRolesPermissionDraft dataRolesPermissionDraft = new DataRolesPermissionDraft();
                BeanUtils.copyProperties(dataPermissionVo, dataRolesPermissionDraft);
                dataRolesPermissionDraft.setId(null);
                if(dataPermissionVo.getCreatedTime() == null){
                    dataRolesPermissionDraft.setCreatedTime(date);
                }
                if(StringUtils.isBlank(dataPermissionVo.getCreatedUser())){
                    dataRolesPermissionDraft.setCreatedUser(dataRole.getModifyUser());
                }
                if(StringUtils.isBlank(dataPermissionVo.getModifyUser())){
                    dataRolesPermissionDraft.setModifyUser(dataRole.getModifyUser());
                }
                if(dataPermissionVo.getModifyTime() == null){
                    dataRolesPermissionDraft.setModifyTime(date);
                }
                dataRolesPermissionDraft.setDataRoleId(dataRole.getId());
                dataRolesPermissionDrafts.add(dataRolesPermissionDraft);
            }
            dataRolesPermissionDraftService.saveBatch(dataRolesPermissionDrafts);
        }
        return dataRolesPermissionDrafts;
    }

    @Override
    public ResultData<DataRole> getDataRoleDetail(DataRolePermissionDetailParam dataRolePermissionDetailParam) {
        DataRole dataRole = dataRoleMapper.getDataRoleById(dataRolePermissionDetailParam.getId());
        if (null == dataRole) {
            return ResultData.fail("查询数据为null");
        }
        List<DataRolesPermission> dataRolesPermissions = getDataRolePermissionsById(dataRolePermissionDetailParam);
        dataRole.setDataPermissions(dataRolesPermissions);
        return ResultData.success(dataRole);
    }

    private List<DataRolesPermission> getDataRolePermissionsById(DataRolePermissionDetailParam dataRolePermissionDetailParam) {
        List<DataRolesPermission> list = dataRolesPermissionMapper.selectListByParam(dataRolePermissionDetailParam);
        return list;
    }

    @Override
    public ResultData deleteDataRole(Long id) {
        try {
            ResultData checkRelation = checkRelation(id);
            if(checkRelation.getCode()!=ReturnCode.RC1.getCode()){
                return checkRelation;
            }
            DataRole dataRole = new DataRole();
            dataRole.setId(id);
            dataRole.setStatus(PermissionConstant.BIND_STATUS_DELETE);
            int i = dataRoleMapper.updateById(dataRole);
            if (i == 0) {
                return ResultData.fail("删除的id不存在");
            }
            UpdateWrapper<DataRolesPermission> deleteWrapper = new UpdateWrapper<>();
            deleteWrapper.eq("data_role_id", dataRole.getId());
            dataRolesPermissionMapper.delete(deleteWrapper);

            deleteDataRolesPermissionDraft(dataRole,null);
            return ResultData.success();
        } catch (Exception e) {
            return ResultData.fail("删除的id:,"+e.getMessage());
        }
    }

    private ResultData checkRelation(Long id) {
        QueryWrapper<UsersDataRoles> wrapper = new QueryWrapper();
        wrapper.eq("data_role_id",id);
        Long i1 = usersDataRolesMapper.selectCount(wrapper);
        if(i1>0){
            return ResultData.fail("该数据角色已被用户关联，不能删除");
        }
        QueryWrapper<DeptsDataRoles> wrapper1 = new QueryWrapper<>();
        wrapper1.eq("data_role_id",id);
        Long i2 = deptsDataRolesMapper.selectCount(wrapper1);
        if(i2>0){
            return ResultData.fail("该数据角色已被部门关联，不能删除");
        }
        QueryWrapper<GroupsDataRoles> wrapper2 = new QueryWrapper<>();
        wrapper2.eq("data_role_id",id);
        Long i3 = groupsDataRolesMapper.selectCount(wrapper2);
        if(i3>0){
            return ResultData.fail("该数据角色已被用户组关联，不能删除");
        }
        return ResultData.success();
    }

    @Override
    public ResultData getDataRoles() {
        QueryWrapper<DataRole> queryWrapper = new QueryWrapper<>();
//        queryWrapper.select("id", "name", "remark", "created_user_id");
        queryWrapper.eq("status", PermissionConstant.DATA_STATUS_ENABLE);
        queryWrapper.eq("data_role_type", DataRoleTypeEnum.NORMAL.getVal());
//        queryWrapper.orderByAsc( "id");
        List<DataRole> dataRoles = dataRoleMapper.selectList(queryWrapper);
        List<DataRoleListVo> DataRoleList = dataRoles.stream().map(dataRole -> {
            DataRoleListVo dataRoleListVo = new DataRoleListVo();
            BeanUtils.copyProperties(dataRole, dataRoleListVo);
            return dataRoleListVo;
        }).collect(Collectors.toList());
        return ResultData.success(DataRoleList);

    }

    @Override
    public ResultData getDataRoles(PageParam<DataRoleQueryCriteria> criteria) {
        // 参数校验、解析
        Page page = criteria.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        DataRoleQueryCriteria queryParam = criteria.getQueryParam();
        List<DataRole> list = dataRoleMapper.listByPage(queryParam.getKeyWords());
        page.setRecords(list);
        Long total = dataRoleMapper.count(queryParam.getKeyWords());
        page.setTotal(total);
        return ResultData.success(page);
    }
    @Override
    public ResultData getDataPermissions(PageParam<DataPermissionQueryCriteria> criteria) {
        DataPermissionQueryCriteria queryParam = criteria.getQueryParam();
        QueryWrapper<DataRolesPermission> dataRolesPermissionWrapper = new QueryWrapper<>();
        if(queryParam.getDataRoleId() != null){
            dataRolesPermissionWrapper.eq("data_role_id", queryParam.getDataRoleId());
        }

        if(StringUtils.isNotBlank(queryParam.getKeyWords())){
            dataRolesPermissionWrapper.and(wrapper ->
                    wrapper.like("database_name", queryParam.getKeyWords()).or().
                            like("table_name", queryParam.getKeyWords())
            );
        }

        return ResultData.success(dataRolesPermissionMapper.selectPage(criteria.getPage(), dataRolesPermissionWrapper));
    }

    @Override
    public ResultData dataRoleChangeStatus(Long id, Integer status, String modifyUser) {
        DataRole dataRole = dataRoleMapper.selectById(id);
        if(null==dataRole){
            return ResultData.fail("角色不存在");
        }
        DataRole dataRoleN = new DataRole();
        dataRoleN.setId(id);
        dataRoleN.setStatus(status);
        dataRoleN.setModifyUser(modifyUser);
        dataRoleN.setModifyTime(new Date());
        int i = dataRoleMapper.updateById(dataRoleN);
        if (i == 0) {
            return ResultData.fail("更新状态失败");
        }
        return ResultData.success();
    }


    @Override
    public ResultData getBindUserListByPage(PageParam<DataRoleBindUserPageParam> criteria) {
        // 参数校验、解析
        Page page = criteria.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        DataRoleBindUserPageParam queryParam = criteria.getQueryParam();
        List<DataRole> list = dataRoleMapper.getBindUserListByPage(queryParam.getFilterVal(),queryParam.getId());
        page.setRecords(list);
        Long total = dataRoleMapper.countBindUserListByPage(queryParam.getFilterVal(),queryParam.getId());
        page.setTotal(total);
        return ResultData.success(page);
    }

    @Override
    public ResultData getDataPermissionListByPage(PageParam<DataPermissionQueryCriteria> criteria) {
        // 参数校验、解析
        Page page = criteria.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        DataPermissionQueryCriteria queryParam = criteria.getQueryParam();
        List<DataRolesPermission> list = dataRoleMapper.getDataPermissionListByPage(queryParam.getDataRoleId(),queryParam.getKeyWords());
        page.setRecords(list);
        Long total = dataRoleMapper.countDataPermissionList(queryParam.getDataRoleId(),queryParam.getKeyWords());
        page.setTotal(total);
        return ResultData.success(page);

    }

    @Override
    public ResultData deleteDataRolePermissions(String modifyUser, List<Long> permisssionIds) {
        try {
            if(null==permisssionIds || permisssionIds.size()==0){
                return ResultData.fail("删除列表为空");
            }
            baseMapper.deleteBatchIds(permisssionIds);
            return ResultData.success();
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultData.fail(e.getMessage());
        }
    }

    @Override
    public ResultData overwriteDateRole(DataRoleVo resources) {
        try{
            DataRole dataRole = dataRoleMapper.selectById(resources.getId());
            if (null == dataRole) {
                return ResultData.fail("要更新的角色不存在");
            }

            Date date = new Date();
            DataRole dataRoleN = new DataRole();
            BeanUtils.copyProperties(resources, dataRoleN);
            dataRoleN.setModifyTime(date);
            if (dataRoleN.isNewerThan(dataRole)) {
                dataRole.copyValueBy(dataRoleN);
                dataRoleMapper.updateById(dataRole);
            }
            ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_DATA_ROLE.getVal(),resources.getId() );
            if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
                return ResultData.fail("数据角色基本信息已修改，数据权限保存失败，原因：存在审批中的数据权限绑定审批，请先联系审批中心，处理上次申请！");
            }
            return sendApproveInfo(dataRole,resources.getDataPermissions(),resources.getModifyUser(), AuthRoleTypeEnum.CONTROL.getId(),ApproveFlowTagEnum.UPC_DATA_ROLE);
        }catch(Exception e){
            log.info(e.getMessage());
            return ResultData.fail(e.getMessage());
        }

    }

    @Override
    public ResultData bind(DataRoleRelationVo resources) {
//        授权方式0个人授权1部门授权2用户组授权
        try {
            switch (resources.getBindType()) {
                case 0:
                    UsersDataRoles usersDataRoles = new UsersDataRoles();
                    usersDataRoles.setDataRoleId(resources.getDataRoleId());
                    usersDataRoles.setUserId(resources.getBindId());
//                授权方式0个人授权1部门授权2用户组授权
//                    usersDataRoles.setBindType(BIND_TYPE_USER);
                    int insert = usersDataRolesMapper.insert(usersDataRoles);
                    return insert > 0 ? ResultData.success() : ResultData.fail("用户角色已绑定");
                case 1:
                    GroupsDataRoles groupsDataRoles = new GroupsDataRoles();
                    groupsDataRoles.setDataRoleId(resources.getDataRoleId());
                    groupsDataRoles.setGroupId(resources.getBindId());
                    int insert1 = groupsDataRolesMapper.insert(groupsDataRoles);
                    return insert1 > 0 ? ResultData.success() : ResultData.fail("数据组角色已绑定");
                case 2:
                    DeptsDataRoles deptsDataRoles = new DeptsDataRoles();
                    deptsDataRoles.setDataRoleId(resources.getDataRoleId());
                    deptsDataRoles.setDeptId(resources.getBindId());
                    int insert2 = deptsDataRolesMapper.insert(deptsDataRoles);
                    return insert2 > 0 ? ResultData.success() : ResultData.fail("数据组角色已绑定");
                default:
                    throw new IllegalStateException("Unexpected value: " + resources.getBindType());
            }
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultData.fail(e.getMessage());
        }
    }

    @Override
    public List<TreeData<DataRole>> treeList(String username,Long userId,Long deptId, String filterVal, Integer status) {
        User user = null;
        if(userId!=null){
            user = userMapper.selectById(userId);
        }else if(StringUtils.isNotBlank(username)){
            user = userMapper.selectByUsername(username);
        }
        QueryWrapper<DataRole> dataRoleWrapper = new QueryWrapper<>();
        dataRoleWrapper.eq("data_role_type",DataRoleTypeEnum.NORMAL.getId());
        if(status != null){
            dataRoleWrapper.eq("status", status);
        }
        if(deptId != null && deptId > 0){
            dataRoleWrapper.eq("dept_id", deptId);
        }else if(user!=null){
            deptId = user.getDeptId();
            dataRoleWrapper.eq("dept_id", user.getDeptId());
        }
        if(StringUtils.isNotBlank(filterVal)){
            dataRoleWrapper.and(wrapper ->
                    wrapper.like("name", filterVal).or().
                            like("created_user", filterVal).or().
                            like("id", filterVal)
            );
        }
        List<DataRole> dataRoles = baseMapper.selectList(dataRoleWrapper);
        List<Dept> depts;
        if(deptId != null && deptId > 0){
            depts = getAllParentDept(deptId);
        }else{
            depts = deptMapper.trueList();
        }

        return installRoleTree(depts,dataRoles);
    }
    private List<Dept> getAllParentDept(Long deptId){
        List<Dept> depts = new LinkedList<>();
        while(true){
            Dept dept = deptMapper.selectById(deptId);
            if(dept!=null){
                depts.add(dept);
                deptId = dept.getPid();
            }else{
                break;
            }
        }
        return depts;
    }

    @Override
    public List<TreeData<DataRole>> installRoleTree(Collection<Dept> depts,List<DataRole> dataRoles) {
        List<TreeData<DataRole>> rootList = new LinkedList<>();
        Map<Long,List<DataRole>> deptRoleMap;
        if(!CollectionUtils.isEmpty(dataRoles)){
            deptRoleMap = dataRoles.stream().collect(Collectors.groupingBy(DataRole::getDeptId));
        }else{
            return rootList;
        }

        Map<Long,List<Dept>> rootTreeMap = depts.stream().collect(Collectors.groupingBy(Dept::getPid));
        List<Dept> rootDepts = rootTreeMap.get(0L);
        for(Dept rootDept:rootDepts){
            int level=1;
            TreeData<DataRole> treeData = newTreeData(rootDept,level);
            insertTree(treeData,level+1,rootTreeMap,deptRoleMap);
            rootList.add(treeData);
        }

        return rootList;
    }


    private void insertTree(TreeData<DataRole> treeData, int level, Map<Long, List<Dept>> rootTreeMap,Map<Long,List<DataRole>> deptRoleMap) {
        List<Dept> depts = rootTreeMap.get(treeData.getId());

        List<TreeData<DataRole>> children = new LinkedList<>();
        if(!CollectionUtils.isEmpty(depts)){
            for(Dept dept:depts){
                TreeData<DataRole> sonTreeData=newTreeData(dept,level);
                insertTree(sonTreeData,level+1,rootTreeMap,deptRoleMap);
                children.add(sonTreeData);
            }
        }
        List<DataRole> dataRoles = deptRoleMap.get(treeData.getId());
        if(!CollectionUtils.isEmpty(dataRoles)){
            for(DataRole dataRole:dataRoles){
                TreeData<DataRole> dataRoleData=newTreeData(dataRole,level);
                children.add(dataRoleData);
            }
        }
        treeData.setChildren(children);
    }
    private TreeData<DataRole> newTreeData(Dept dept,Integer level){
        TreeData<DataRole> treeData = new TreeData<>();
        treeData.setKey(level+"_"+dept.getId());
        treeData.setLabel(dept.getName());
        treeData.setId(dept.getId());
        treeData.setShowType("DEPT");
        return treeData;
    }

    private TreeData<DataRole> newTreeData(DataRole dataRole,Integer level){
        TreeData<DataRole> treeData = new TreeData<>();
        treeData.setKey(level+"_data_"+dataRole.getId());
        treeData.setLabel(dataRole.getName());
        treeData.setId(dataRole.getId());
        treeData.setShowType("DataRole");
        treeData.setData(dataRole);
        return treeData;
    }
    @Override
    public void confirmApprove(ApproveRecord approveRecord) {
        Long dataRoleId = approveRecord.getApproveTargetId();
        DataRole dataRole = super.getById(dataRoleId);
        if(approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()){
            addDataRolesPermission(dataRole,approveRecord);
            deleteDataRolesPermissionDraft(dataRole,approveRecord);
        }else{
            deleteDataRolesPermissionDraft(dataRole,approveRecord);
        }
    }

    private void deleteDataRolesPermissionDraft(DataRole dataRole,ApproveRecord approveRecord) {
        List<DataRolesPermissionDraft> dataRolesPermissionDrafts = dataRolesPermissionDraftMapper.selectListByDataRoleId(dataRole.getId());
        if(!CollectionUtils.isEmpty(dataRolesPermissionDrafts)){
            List<Long> deleteIds = dataRolesPermissionDrafts.stream().map(e->e.getId()).collect(Collectors.toList());
            dataRolesPermissionDraftMapper.deleteBatchIds(deleteIds);

            if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.NO_APPROVED.getId()){
                List<DataRolesPermission> dataRolesPermissions = new LinkedList<>();
                for(DataRolesPermissionDraft dataRolesPermissionDraft : dataRolesPermissionDrafts){
                    DataRolesPermission dataRolesPermission = new DataRolesPermission();
                    BeanUtils.copyProperties(dataRolesPermissionDraft,dataRolesPermission);
                }
                asyncFeignService.addDataRolePermissionAuditLog(dataRole,dataRolesPermissions,approveRecord,dataRole.getDataRoleType() == DataRoleTypeEnum.PERSON.getId()?AuthRoleTypeEnum.PERSON.getId() : AuthRoleTypeEnum.CONTROL.getId());
            }
        }
    }

    private void addDataRolesPermission(DataRole dataRole, ApproveRecord approveRecord) {
        //先删除
        if(ApproveFlowTagEnum.UPC_DATA_ROLE.getVal().equals(approveRecord.getFlowTag())){
            QueryWrapper wrapper = new QueryWrapper();
            wrapper.eq("data_role_id", dataRole.getId());
            dataRolesPermissionMapper.delete(wrapper);
        }

        //后新增
        List<DataRolesPermission> dataRolesPermissions = new LinkedList<>();
        List<DataRolesPermissionDraft> dataRolesPermissionDrafts = dataRolesPermissionDraftMapper.selectListByDataRoleId(dataRole.getId());
        dataRolesPermissionDrafts.stream().forEach(dataPermissionVo -> {
            DataRolesPermission dataRolesPermission = new DataRolesPermission();
            BeanUtils.copyProperties(dataPermissionVo, dataRolesPermission);
            dataRolesPermission.setId(null);
            dataRolesPermissions.add(dataRolesPermission);
        });
        dataRolesPermissionService.saveBatch(dataRolesPermissions);
        asyncFeignService.addDataRolePermissionAuditLog(dataRole,dataRolesPermissions,approveRecord,dataRole.getDataRoleType() == DataRoleTypeEnum.PERSON.getId()?AuthRoleTypeEnum.PERSON.getId() : AuthRoleTypeEnum.CONTROL.getId());
    }
}