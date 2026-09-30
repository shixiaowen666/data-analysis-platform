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
import com.senses.permission.model.param.*;
import com.senses.permission.model.vo.RoleInfoVO;
import com.senses.permission.model.vo.RoleVo;
import com.senses.permission.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色表;(role)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
@Slf4j
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService{
    @Resource
    private RoleMapper roleMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private RolesPermissionService rolesPermissionService;

    @Resource
    private UsersRolesMapper usersRolesMapper;

    @Resource
    private DeptsRolesMapper deptsRolesMapper;

    @Resource
    private GroupsRolesMapper groupsRolesMapper;

    @Resource
    private DeptsRolesDraftMapper deptsRolesDraftMapper;

    @Resource
    private GroupsRolesDraftMapper groupsRolesDraftMapper;

    @Resource
    private ApplicationMapper applicationMapper;

    @Resource
    private ApproveRecordMapper approveRecordMapper;

    @Resource
    private ApplicationService applicationService;

    @Resource
    private FeignApproveDecoratorService feignApproveDecoratorService;

    @Resource
    private RolesPermissionDraftMapper rolesPermissionDraftMapper;

    @Resource
    private RolesPermissionDraftService rolesPermissionDraftService;

    @Resource
    private AsyncFeignService asyncFeignService;

    @Override
    public ResultData deleteRole(Long roleId, String userName) {

        ResultData checkRelation = checkRelation(roleId);
        if(checkRelation.getCode()!= ReturnCode.RC1.getCode()){
            return checkRelation;
        }
        Role role = baseMapper.selectById(roleId);
        role.setStatus(CommonStatusEnum.DELETE.getId());
        role.setModifyTime(new Date());
        role.setModifyUser(userName);
        this.updateById(role);
        UpdateWrapper<RolesPermission> deleteRolesPermission = new UpdateWrapper<>();
        deleteRolesPermission.eq("role_id",roleId);
        rolesPermissionService.remove(deleteRolesPermission);

        deleteRolePermissionDraft(role);

        UpdateWrapper<DeptsRolesDraft>  deleteDeptRolesDraft= new UpdateWrapper<>();
        deleteDeptRolesDraft.eq("role_id",roleId);
        deptsRolesDraftMapper.delete(deleteDeptRolesDraft);

        UpdateWrapper<GroupsRolesDraft> deleteGroupRolesDraft = new UpdateWrapper<>();
        deleteGroupRolesDraft.eq("role_id",roleId);
        groupsRolesDraftMapper.delete(deleteGroupRolesDraft);
        return ResultData.success();
    }

    private ResultData checkRelation(Long roleId){
        List<DeptsRoles> deptsRoles = deptsRolesMapper.selectListByRoleId(roleId);
        if(!CollectionUtils.isEmpty(deptsRoles)){
            return ResultData.fail("该角色已被部门关联，不能删除");
        }
        List<GroupsRoles> groupsRoles = groupsRolesMapper.selectListByRoleId(roleId);
        if(!CollectionUtils.isEmpty(groupsRoles)){
            return ResultData.fail("该角色已被用户组关联，不能删除");
        }
        List<UsersRoles> usersRoles = usersRolesMapper.selectListByRoleId(roleId);
        if(!CollectionUtils.isEmpty(usersRoles)){
            return ResultData.fail("该角色已被用户关联，不能删除");
        }
        return ResultData.success();
    }
    @Override
    public Page<Role> listByPage(PageParam<RolePageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        RolePageParam param = pageParam.getQueryParam();
        Long orId = null;
        if(StringUtils.isNotBlank(param.getFilterVal())
                && StringUtils.isNumeric(param.getFilterVal())
                && !param.getFilterVal().startsWith("0")){
            orId = Long.valueOf(param.getFilterVal());
        }
        List<Role> list = baseMapper.listByPage(orId,param.getFilterVal());
        page.setRecords(list);
        Long total = baseMapper.count(orId,param.getFilterVal());
        page.setTotal(total);
        return page;
    }

    @Override
    public ResultData saveOrUpdateRole(RoleParam roleParam,String userName) {
        if(StringUtils.isBlank(roleParam.getName())){
            return ResultData.fail("角色名称不能为空");
        }
        if(StringUtils.isBlank(roleParam.getCnName())){
            return ResultData.fail("角色中文名称不能为空");
        }
        if(roleParam.getAppId()==null){
            return ResultData.fail("所属应用不能为空");
        }

        Role exist = baseMapper.selectByAppIdAndName(roleParam.getAppId(),roleParam.getName());
        if(exist != null && (roleParam.getId()==null || !roleParam.getId().equals(exist.getId()))){
            return ResultData.fail("当前应用下，已存在同名角色");
        }

        Date date = new Date();
        Role role;
        if(roleParam.getId()==null){
            role = new Role();
            role.setCreatedTime(date);
            role.setCreatedUser(userName);
            if(roleParam.getType()==null){
                return ResultData.fail("所属类型不能为空");
            }
            role.setType(roleParam.getType());
        }else {
            role = baseMapper.selectById(roleParam.getId());
            if(roleParam.getType()!=role.getType()){
                return ResultData.fail("角色类型不能修改");
            }
        }
        if(role.getType() == RoleTypeEnum.APP_ADMIN.getId() && !CollectionUtils.isEmpty(roleParam.getPermissionIds())){
            return ResultData.fail("应用管理员不应设置权限");
        }
        role.setModifyTime(date);
        role.setModifyUser(userName);
        role.setAppId(roleParam.getAppId());
        role.setName(roleParam.getName());
        role.setCnName(roleParam.getCnName());
        role.setStatus(roleParam.getStatus());
        this.saveOrUpdate(role);
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_ROLE.getVal(),role.getId());
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            return ResultData.fail("功能角色基本信息已修改，绑定功能权限保存失败，原因：存在审批中的功能权限绑定审批，请先联系审批中心，处理上次申请！");
        }
        List<Permission> pmsList = null;
        if(!CollectionUtils.isEmpty(roleParam.getPermissionIds())){
            pmsList = permissionMapper.appPermissionListByIds(roleParam.getPermissionIds());
            for(Permission permission:pmsList){
                if(!permission.getAppId().equals(role.getAppId())){
                    return ResultData.fail("所选功能中，存在不属于当前应用的功能");
                }
                if(permission.getStatus()==CommonStatusEnum.DELETE.getId()){
                    return ResultData.fail("所选功能中，存在已删除的功能");
                }
                if(permission.getStatus()==CommonStatusEnum.FALSE.getId()){
                    return ResultData.fail("所选功能中，存在已禁用的功能");
                }
            }
            List<Long> permissionIds = roleParam.getPermissionIds().stream().distinct().collect(Collectors.toList());
            roleParam.setPermissionIds(permissionIds);
        }
        String workName = role.getName()+"【功能角色授权】";
        ApproveRecord newApproveRecord = ApproveRecord.createApprove(role.getId(), ApproveFlowTagEnum.UPC_ROLE, userName,workName);
        approveRecordMapper.insert(newApproveRecord);
        addRolesPermissionDraft(role.getId(), roleParam.getPermissionIds());
        String worksheetData = ApproveWorksheet.createPermissionsWorksheetData(role.getType(),pmsList);
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(newApproveRecord.getId().toString(),workName,userName,worksheetData, BindMarkEnum.UPC_BINDPERMISSION_ROLE.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,"功能角色"+role.getName()+"授权发送申请单返回结果：");
        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteRolesPermissionsDrafts(role.getId(),roleParam.getPermissionIds());
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getData());
        }
        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        newApproveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(newApproveRecord);
        return ResultData.success();
    }

    private void deleteRolesPermissionsDrafts(Long roleId, List<Long> permissionIds) {
        if(!CollectionUtils.isEmpty(permissionIds)){
            UpdateWrapper<RolesPermissionDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("role_id",roleId);
            updateWrapper.in("permission_id",permissionIds);
            rolesPermissionDraftMapper.delete(updateWrapper);
        }
    }

    private void addRolesPermissionDraft(Long roleId, List<Long> permissionIds) {
        if(!CollectionUtils.isEmpty(permissionIds)){
            List<RolesPermissionDraft> list = new ArrayList<>(permissionIds.size());
            for(Long permissionId:permissionIds){
                RolesPermissionDraft rolesPermissionDraft = new RolesPermissionDraft();
                rolesPermissionDraft.setPermissionId(permissionId);
                rolesPermissionDraft.setRoleId(roleId);
                list.add(rolesPermissionDraft);
            }
            rolesPermissionDraftService.saveBatch(list);
        }

    }


    private void deleteBatchRolesPermission(Long roleId){
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq("role_id", roleId);
        rolesPermissionService.remove(wrapper);
    }

    private void saveBatchRolesPermission(Role role){
        //删除角色id和权限id的关联关系
        deleteBatchRolesPermission(role.getId());
        List<RolesPermissionDraft> rolesPermissionDrafts = rolesPermissionDraftMapper.selectListByRoleId(role.getId());
        List<Long> permissionIds;
        if(CollectionUtils.isEmpty(rolesPermissionDrafts) || role.getType() == RoleTypeEnum.APP_ADMIN.getId()){
            return;
        }
        permissionIds = rolesPermissionDrafts.stream().map(e->e.getPermissionId()).collect(Collectors.toList());
        List<RolesPermission> rolesPermissions = new ArrayList<>(permissionIds.size());
        for(Long pmsId : permissionIds){
            RolesPermission rolesPermission = new RolesPermission();
            rolesPermission.setPermissionId(pmsId);
            rolesPermission.setRoleId(role.getId());
            rolesPermissions.add(rolesPermission);
        }
        rolesPermissionService.saveBatch(rolesPermissions);
    }

    @Override
    public Page<User> getUserListByPage(PageParam<RoleBindUserPageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        RoleBindUserPageParam param = pageParam.getQueryParam();
        List<User> list = baseMapper.getUserListByPage(param.getRoleId(),param.getFilterVal());
        page.setRecords(list);
        Long total = baseMapper.countUserList(param.getRoleId(),param.getFilterVal());
        page.setTotal(total);
        return page;
    }

    @Override
    public Page<Permission> getPermissionListByPage(PageParam<RolePermissionPageParam> pageParam) {
        RolePermissionPageParam param = pageParam.getQueryParam();
        Role role = super.getById(param.getRoleId());
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());

        List<Permission> list;
        Long total;
        if(role.getType() == RoleTypeEnum.APP_ADMIN.getId()){
            list = permissionMapper.listByPage(role.getAppId(),CommonStatusEnum.TRUE.getId(),null,param.getFilterVal());
            total = permissionMapper.count(role.getAppId(),CommonStatusEnum.TRUE.getId(), null,param.getFilterVal());
        }else{
            list = baseMapper.getPermissionListByPage(param.getRoleId(),param.getFilterVal());
            total = baseMapper.countPermissionList(param.getRoleId(),param.getFilterVal());
        }
        page.setRecords(list);
        page.setTotal(total);
        return page;
    }

    @Override
    public RoleVo getRoleDetail(Long roleId) {
        RoleVo roleVo = new RoleVo();
        Role role = baseMapper.selectById(roleId);
        BeanUtils.copyProperties(role, roleVo);
        if(role.getType() == RoleTypeEnum.APP_ADMIN.getId()){
            Long appId = role.getAppId();
            roleVo.setPermissions(permissionMapper.listByPage(appId,CommonStatusEnum.TRUE.getId(), null,null));
        }else{
            roleVo.setPermissions(baseMapper.getPermissionListByPage(roleId, null));
        }
        return roleVo;
    }

    @Override
    public ResultData updateStatus(Long roleId, Integer status, String userName) {
        Role role = baseMapper.selectById(roleId);
        role.setStatus(status);
        role.setModifyUser(userName);
        role.setModifyTime(new Date());
        this.updateById(role);
        return ResultData.success();
    }

    @Override
    public List<TreeData<Role>> treeList(Long appId, String filterVal, Integer status) {
        QueryWrapper<Role> roleWrapper = new QueryWrapper<>();
        if(status != null){
            roleWrapper.eq("status", status);
        }
        if(appId != null && appId > 0){
            roleWrapper.eq("app_id", appId);
        }

        if(StringUtils.isNotBlank(filterVal)){
            roleWrapper.and(wrapper ->
                    wrapper.like("name", filterVal).or().
                            like("cn_name", filterVal)
            );
        }
        List<Role> roles = baseMapper.selectList(roleWrapper);

        List<Application> applications;
        if(appId != null && appId > 0){
            applications = new ArrayList<>();
            applications.add(applicationMapper.selectById(appId));
        }else{
            applications = applicationMapper.trueList();
        }

        List<TreeData<Role>> rootList = installAppRoleTree(applications,roles);
        return rootList;

    }

    @Override
    public List<TreeData<Role>> installAppRoleTree(List<Application> applications,List<Role> roles) {
        List<TreeData<Role>> rootList = new LinkedList<>();
        Map<Long,List<Role>> appRoleMap;
        if(!CollectionUtils.isEmpty(roles)){
            appRoleMap = roles.stream().collect(Collectors.groupingBy(Role::getAppId));
        }else{
            appRoleMap = new HashMap<>();
        }
        if(!CollectionUtils.isEmpty(applications)){
            for(Application application:applications){
                TreeData<Role> rootTree = new TreeData<>();
                rootTree.setKey("1_"+application.getId());
                rootTree.setLabel(application.getName());
                rootTree.setId(application.getId());
                rootTree.setShowType("Application");
                List<Role> appRoles = appRoleMap.get(application.getId());
                List<TreeData<Role>> roleTreeDatas = new LinkedList<>();
                if(!CollectionUtils.isEmpty(appRoles)){
                    for(Role role:appRoles){
                        TreeData<Role> roleTreeData = new TreeData<>();
                        roleTreeData.setKey("2_"+role.getId());
                        roleTreeData.setLabel(role.getName());
                        roleTreeData.setShowType("Role");
                        roleTreeData.setId(role.getId());
                        roleTreeData.setData(role);
                        roleTreeDatas.add(roleTreeData);
                    }
                }
                rootTree.setChildren(roleTreeDatas);
                rootList.add(rootTree);
            }
        }
        return rootList;
    }

    @Override
    public List<TreeData<Permission>> permissionTreeList(Long roleId, String filterVal,Integer selectApprove) {
        Role role = super.getById(roleId);
        List<Permission> list;
        if(role.getType() == RoleTypeEnum.APP_ADMIN.getId()){
            list = permissionMapper.trueList(role.getAppId(),null,filterVal);
        }else{
            list = baseMapper.getAllPermissionList(roleId,filterVal,selectApprove);
        }
        return applicationService.installTreeList(list);
    }

    @Override
    public List<Long> getPermissionIds(Long roleId) {
        List<Permission> list = baseMapper.getAllPermissionList(roleId,null,null);
        if(CollectionUtils.isEmpty(list)){
            return new LinkedList<>();
        }
        return list.stream().map(e->e.getId()).collect(Collectors.toList());
    }

    @Override
    public Role getInfoById(Long roleId) {
        Role role = super.getById(roleId);
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_ROLE.getVal(),role.getId());
        if(approveRecord!=null){
            role.setApproveStatus(approveRecord.getApproveStatus());
        }
        return role;
    }

    @Override
    public void confirmApprove(ApproveRecord approveRecord) {
        Long roleId = approveRecord.getApproveTargetId();
        Role role = super.getById(roleId);
        if(approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()){
            //保存角色id和权限id的关联关系
            saveBatchRolesPermission(role);
            deleteRolePermissionDraft(role);
        }else{
            deleteRolePermissionDraft(role);
        }
    }

    private void deleteRolePermissionDraft(Role role) {
        UpdateWrapper<RolesPermissionDraft> deleteRolesPermissionDraft = new UpdateWrapper<>();
        deleteRolesPermissionDraft.eq("role_id",role.getId());
        rolesPermissionDraftMapper.delete(deleteRolesPermissionDraft);
    }

    @Override
    public List<RoleInfoVO> getAllUserRoleInfos(Long userId) {

        return roleMapper.getAllUserRoleInfos(userId);
    }
}