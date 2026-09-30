package com.senses.permission.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.constant.ApproveStatusEnum;
import com.senses.permission.constant.BindMarkEnum;
import com.senses.permission.constant.CommonStatusEnum;
import com.senses.permission.entity.*;
import com.senses.permission.mapper.*;
import com.senses.permission.model.*;
import com.senses.permission.model.param.ApproveWorksheet;
import com.senses.permission.model.param.GroupAddUsersParam;
import com.senses.permission.model.param.GroupPageParam;
import com.senses.permission.model.param.GroupParam;
import com.senses.permission.model.vo.GroupRolesIdsVO;
import com.senses.permission.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * 用户组表;(application)表服务实现类
 *
 * @author : shinan
 * @date : 2022-12-8
 */
@Service
@Slf4j
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {

    @Resource
    private ApplicationMapper applicationMapper;

    @Resource
    private DeptMapper deptMapper;

    @Resource
    private GroupsUsersMapper groupsUsersMapper;

    @Resource
    private GroupsUsersService groupsUsersService;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private DataRoleMapper dataRoleMapper;

    @Resource
    private GroupsRolesMapper groupsRolesMapper;

    @Resource
    private ApproveRecordMapper approveRecordMapper;

    @Resource
    private GroupsRolesService groupsRolesService;

    @Resource
    private GroupsDataRolesMapper groupsDataRolesMapper;

    @Resource
    private GroupsDataRolesService groupsDataRolesService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private GroupsRolesDraftMapper groupsRolesDraftMapper;

    @Resource
    private GroupsRolesDraftService groupsRolesDraftService;

    @Resource
    private GroupsDataRolesDraftMapper groupsDataRolesDraftMapper;

    @Resource
    private GroupsDataRolesDraftService groupsDataRolesDraftService;

    @Resource
    private RoleService roleService;

    @Resource
    private DataRoleService dataRoleService;

    @Resource
    private UsersRolesMapper usersRolesMapper;

    @Resource
    private UsersDataRolesMapper usersDataRolesMapper;

    @Resource
    private UsersRolesDraftMapper usersRolesDraftMapper;

    @Resource
    private UsersDataRolesDraftMapper usersDataRolesDraftMapper;

    @Resource
    private AsyncFeignService asyncFeignService;

    @Resource
    private FeignApproveDecoratorService feignApproveDecoratorService;

    @Resource
    private DeptService deptService;
    @Override
    public ResultData saveOrUpdateGroup(GroupParam groupParam, String userName) {
        if (StringUtils.isNotBlank(groupParam.getGroupName())) {
            Group existGroupName = baseMapper.selectExist(groupParam.getGroupName());
            if (existGroupName != null && (groupParam.getId() == null || !groupParam.getId().equals(existGroupName.getId()))) {
                return ResultData.fail("组名称已被使用，请使用其他值！");
            }
        }
        Group group;
        if (groupParam.getId() != null) {
            group = getById(groupParam.getId());
            if (StringUtils.isNotBlank(groupParam.getGroupName())) {
                group.setGroupName(groupParam.getGroupName());
            }
            if(groupParam.getStatus() != null){
                group.setStatus(groupParam.getStatus());
            }
        } else {
            if (StringUtils.isBlank(groupParam.getGroupName())) {
                return ResultData.fail("组名称不能为空！");
            }
            group = new Group();
            group.setCreatedTime(new Date());
            group.setCreatedUser(userName);
            group.setGroupName(groupParam.getGroupName());
            group.setStatus(CommonStatusEnum.TRUE.getId());
        }
        group.setModifyUser(userName);
        group.setModifyTime(new Date());
        if (this.saveOrUpdate(group)) {
            return ResultData.success();
        } else {
            return ResultData.fail("保存失败");
        }
    }

    @Override
    public ResultData deleteGroup(Long id, String userName) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_USER_GROUP.getVal(),id);
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            return ResultData.fail("存在审批中的部门绑定权限申请，无法删除当前用户组！");
        }

        Group group = baseMapper.selectById(id);
        group.setStatus(CommonStatusEnum.DELETE.getId());
        group.setModifyTime(new Date());
        group.setModifyUser(userName);
        this.updateById(group);

        UpdateWrapper<GroupsUsers> deleteUw = new UpdateWrapper<>();
        deleteUw.eq("group_id", id);
        groupsUsersMapper.delete(deleteUw);

        UpdateWrapper<GroupsRoles> deleteGroupsRoles = new UpdateWrapper<>();
        deleteGroupsRoles.eq("group_id", id);
        groupsRolesMapper.delete(deleteGroupsRoles);

        UpdateWrapper<GroupsDataRoles> deleteGroupsDataRoles = new UpdateWrapper<>();
        deleteGroupsDataRoles.eq("group_id", id);
        groupsDataRolesMapper.delete(deleteGroupsDataRoles);

        deleteGroupRoleAndDataRoleDraft(group,null);

        return ResultData.success();
    }

    @Override
    public List<Group> listLikeGroupname(String groupName) {
        QueryWrapper<Group> qw = new QueryWrapper<>();
        qw.eq("status",CommonStatusEnum.TRUE.getId());
        if (StringUtils.isNotBlank(groupName)) {
            qw.like("groupName", groupName);
        }
        return this.list(qw);
    }

    @Override
    public Page<Group> listByPage(PageParam<GroupPageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        GroupPageParam param = pageParam.getQueryParam();
        List<Group> list = baseMapper.listByPage(param.getFilterVal());
        page.setRecords(list);
        Long total = baseMapper.count(param.getFilterVal());
        page.setTotal(total);
        return page;
    }

    @Override
    public Group getInfo(Integer groupId) {
        Group group = baseMapper.selectInfoByGroupId(groupId);
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_USER_GROUP.getVal(),Long.valueOf(groupId));
        if(approveRecord!=null){
            group.setApproveStatus(approveRecord.getApproveStatus());
        }
        return group;
    }

    @Override
    public ResultData bind(Long groupId, List<Long> roleIds, List<Long> dataRoleIds,String username) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_USER_GROUP.getVal(),groupId);
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            return ResultData.fail("存在审批中的部门绑定权限申请，请先联系审批中心，处理上次申请！");
        }
        Group group = baseMapper.selectById(groupId);
        if (group.getStatus() == CommonStatusEnum.DELETE.getId()) {
            return ResultData.fail("用户组已被删除，不能再授权");
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
            List<String> falseDataRoleName = roles.stream().filter(e -> e.getStatus() == CommonStatusEnum.FALSE.getId()).map(e -> e.getName()).collect(Collectors.toList());
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
        String workName = group.getGroupName()+"【用户组授权】";
        ApproveRecord newApproveRecord = ApproveRecord.createApprove(groupId,ApproveFlowTagEnum.UPC_USER_GROUP, username,workName);
        approveRecordMapper.insert(newApproveRecord);
        addGroupRolesDraft(groupId, roleIds);
        addGroupDataRolesDraft(groupId,dataRoleIds);
        String worksheetData = ApproveWorksheet.createRolesAndDataRoleWorksheetData(roles,dataRoles);
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(newApproveRecord.getId().toString(),workName,username,worksheetData, BindMarkEnum.UPC_BINDROLE_GROUP.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,"用户分组"+group.getGroupName()+"授权发送申请单返回结果：");
        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()) {
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteGroupRoleDrafts(groupId,roleIds);
            deleteGroupDataRoleDrafts(groupId,dataRoleIds);
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getMessage());
        }

        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        newApproveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(newApproveRecord);
        //向审计中心发送日志记录
        asyncFeignService.addGroupBindRoleAuditLog(group,roleIds,newApproveRecord);
        asyncFeignService.addGroupBindDataRoleAuditLog(group,dataRoleIds,newApproveRecord);
        return ResultData.success();
    }

    private void deleteGroupRoleDrafts(Long groupId, List<Long> roleIds) {
        if(!CollectionUtils.isEmpty(roleIds)){
            UpdateWrapper<GroupsRolesDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("group_id",groupId);
            updateWrapper.in("role_id",roleIds);
            groupsRolesDraftMapper.delete(updateWrapper);
        }
    }

    private void deleteGroupDataRoleDrafts(Long groupId, List<Long> dataRoleIds) {
        if(!CollectionUtils.isEmpty(dataRoleIds)){
            UpdateWrapper<GroupsDataRolesDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("group_id",groupId);
            updateWrapper.in("data_role_id",dataRoleIds);
            groupsDataRolesDraftMapper.delete(updateWrapper);
        }

    }

    private void addGroupDataRolesDraft(Long groupId, List<Long> dataRoleIds) {
        List<GroupsDataRolesDraft> list = new ArrayList<>(dataRoleIds.size());
        for(Long dataRoleId:dataRoleIds){
            GroupsDataRolesDraft groupsDataRolesDraft = new GroupsDataRolesDraft();
            groupsDataRolesDraft.setGroupId(groupId);
            groupsDataRolesDraft.setDataRoleId(dataRoleId);
            list.add(groupsDataRolesDraft);
        }
        groupsDataRolesDraftService.saveBatch(list);
    }

    private void addGroupRolesDraft(Long groupId, List<Long> roleIds) {
        List<GroupsRolesDraft> list = new ArrayList<>(roleIds.size());
        for(Long roleId:roleIds){
            GroupsRolesDraft groupsRolesDraft = new GroupsRolesDraft();
            groupsRolesDraft.setGroupId(groupId);
            groupsRolesDraft.setRoleId(roleId);
            list.add(groupsRolesDraft);
        }
        groupsRolesDraftService.saveBatch(list);
    }

    @Override
    public ResultData addUsers(GroupAddUsersParam groupAddUsersParam) {
        List<Long> userIds = groupAddUsersParam.getUserIds();
        if(CollectionUtils.isEmpty(userIds)){
            saveGroupsUsers(groupAddUsersParam.getGroupId(),userIds);
            return ResultData.success();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        List<User> deleteUsers = users.stream().filter(e->e.getStatus()==CommonStatusEnum.DELETE.getId()).collect(Collectors.toList());
        if(!CollectionUtils.isEmpty(deleteUsers)){
            List<String> userNames = deleteUsers.stream().map(e->e.getName()).collect(Collectors.toList());
            return ResultData.fail("以下用户已删除，不能添加到用户组，请重新选择："+String.join(",",userNames));
        }
        saveGroupsUsers(groupAddUsersParam.getGroupId(),userIds);
        return ResultData.success();
    }

    @Override
    public List<TreeData<Role>> roleTreeList(Long groupId) {
        List<Application> deptApplication = applicationMapper.selectByGroupId(groupId);
        List<Role> roles = roleMapper.selectByGroupId(groupId);
        List<TreeData<Role>> treeDatas = roleService.installAppRoleTree(deptApplication,roles);
        return treeDatas;
    }

    @Override
    public List<TreeData<DataRole>> dataRoleTreeList(Long groupId) {
        List<Dept> depts = deptMapper.selectByGroupId(groupId);
        Set<Dept> allDepts = deptService.getAllParentDepts(depts);
        List<DataRole> dataRoles = dataRoleMapper.selectByGroupId(groupId);
        List<TreeData<DataRole>> treeDatas = dataRoleService.installRoleTree(allDepts,dataRoles);
        return treeDatas;

    }


    private void saveGroupsUsers(Long groupId,List<Long> userIds) {
        UpdateWrapper<GroupsUsers> dw = new UpdateWrapper<>();
        dw.eq("group_id",groupId);
        groupsUsersMapper.delete(dw);


        if(!CollectionUtils.isEmpty(userIds)){
            List<GroupsUsers> addGroupsUsersList = new ArrayList<>(userIds.size());
            for(Long addId:userIds){
                GroupsUsers groupsUsers = new GroupsUsers();
                groupsUsers.setUserId(addId);
                groupsUsers.setGroupId(groupId);
                addGroupsUsersList.add(groupsUsers);
            }
            groupsUsersService.saveBatch(addGroupsUsersList);
        }

    }

    private void addOrUpdateGroupsRoles(Group group,List<Long> userIds,ApproveRecord approveRecord) {
        UpdateWrapper<GroupsRoles> delete = new UpdateWrapper<>();
        delete.eq("group_id", group.getId());
        groupsRolesMapper.delete(delete);
        List<GroupsRolesDraft> groupsRolesDraftList = groupsRolesDraftMapper.selectByGroupId(group.getId());
        if(CollectionUtils.isEmpty(groupsRolesDraftList)){
            return;
        }
        List<Long> addIds = groupsRolesDraftList.stream().map(e->e.getRoleId()).collect(Collectors.toList());
        List<GroupsRoles> groupsRolesList = new ArrayList<>(addIds.size());
        for (Long addId : addIds) {
            GroupsRoles groupsRoles = new GroupsRoles();
            groupsRoles.setGroupId(group.getId());
            groupsRoles.setRoleId(addId);
            groupsRolesList.add(groupsRoles);
        }
        groupsRolesService.saveBatch(groupsRolesList);

        if(!CollectionUtils.isEmpty(userIds)){
            UpdateWrapper<UsersRoles> uw = new UpdateWrapper<>();
            uw.in("role_id",addIds);
            uw.in("user_id",userIds);
            usersRolesMapper.delete(uw);

            UpdateWrapper<UsersRolesDraft> draftUw = new UpdateWrapper<>();
            draftUw.in("role_id",addIds);
            draftUw.in("user_id",userIds);
            usersRolesDraftMapper.delete(draftUw);
        }
        asyncFeignService.addGroupBindRoleAuditLog(group,addIds,approveRecord);
    }


    private void addOrUpdateGroupsDataRoles(Group group,List<Long> userIds,ApproveRecord approveRecord) {
        UpdateWrapper<GroupsDataRoles> delete = new UpdateWrapper<>();
        delete.eq("group_id", group.getId());
        groupsDataRolesMapper.delete(delete);
        List<GroupsDataRolesDraft> groupsDataRolesDraftList = groupsDataRolesDraftMapper.selectByGroupId(group.getId());
        if(CollectionUtils.isEmpty(groupsDataRolesDraftList)){
            return;
        }
        List<Long> addIds = groupsDataRolesDraftList.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());
        List<GroupsDataRoles> groupsDataRolesList = new ArrayList<>(addIds.size());
        for(Long addId:addIds){
            GroupsDataRoles groupsDataRoles = new GroupsDataRoles();
            groupsDataRoles.setGroupId(group.getId());
            groupsDataRoles.setDataRoleId(addId);
            groupsDataRolesList.add(groupsDataRoles);
        }
        groupsDataRolesService.saveBatch(groupsDataRolesList);
        if(!CollectionUtils.isEmpty(userIds)){
            UpdateWrapper<UsersDataRoles> uw = new UpdateWrapper<>();
            uw.in("data_role_id",addIds);
            uw.in("user_id",userIds);
            usersDataRolesMapper.delete(uw);

            UpdateWrapper<UsersDataRolesDraft> draftUw = new UpdateWrapper<>();
            draftUw.in("data_role_id",addIds);
            draftUw.in("user_id",userIds);
            usersDataRolesDraftMapper.delete(draftUw);
        }
        asyncFeignService.addGroupBindDataRoleAuditLog(group,addIds,approveRecord);
    }

    @Override
    public GroupRolesIdsVO getRoleAndDataRoleIds(Long groupId) {
        List<GroupsRoles> groupsRoles = groupsRolesMapper.selectListByGroupId(groupId);
        List<GroupsDataRoles> groupsDataRoles = groupsDataRolesMapper.listByGroupId(groupId);
        GroupRolesIdsVO groupRolesIdsVO = new GroupRolesIdsVO();
        List<Long> roleIds;
        if(!CollectionUtils.isEmpty(groupsRoles)){
            roleIds = groupsRoles.stream().map(e->e.getRoleId()).collect(Collectors.toList());
        }else{
            roleIds = new LinkedList<>();
        }
        List<Long> dataRoleIds;
        if(!CollectionUtils.isEmpty(groupsDataRoles)){
            dataRoleIds = groupsDataRoles.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());

        }else {
            dataRoleIds = new LinkedList<>();
        }
        groupRolesIdsVO.setRoleIds(roleIds);
        groupRolesIdsVO.setDataRoleIds(dataRoleIds);
        return groupRolesIdsVO;
    }

    @Override
    public void confirmApprove(ApproveRecord approveRecord) {
        Long groupId = approveRecord.getApproveTargetId();
        Group group = super.getById(groupId);
        List<User> users = userMapper.listByGroupId(groupId);
        List<Long> userIds = null;
        if(!CollectionUtils.isEmpty(users)){
            userIds = users.stream().map(e->e.getId()).collect(Collectors.toList());
        }
        if(approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()){
            addOrUpdateGroupsRoles(group,userIds,approveRecord);
            addOrUpdateGroupsDataRoles(group,userIds,approveRecord);
            deleteGroupRoleAndDataRoleDraft(group,approveRecord);
        }else{
            deleteGroupRoleAndDataRoleDraft(group,approveRecord);
        }

    }

    private void deleteGroupRoleAndDataRoleDraft(Group group,ApproveRecord approveRecord) {
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.NO_APPROVED.getId()){
            List<GroupsRolesDraft> groupsRolesDrafts = groupsRolesDraftMapper.selectByGroupId(group.getId());
            if(!CollectionUtils.isEmpty(groupsRolesDrafts)){
                List<Long> roleIds = groupsRolesDrafts.stream().map(e->e.getRoleId()).collect(Collectors.toList());
                asyncFeignService.addGroupBindRoleAuditLog(group,roleIds,approveRecord);
            }
            List<GroupsDataRolesDraft> groupsDataRolesDrafts = groupsDataRolesDraftMapper.selectByGroupId(group.getId());
            if(!CollectionUtils.isEmpty(groupsDataRolesDrafts)){
                List<Long> dataRoleIds = groupsDataRolesDrafts.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());
                asyncFeignService.addGroupBindDataRoleAuditLog(group,dataRoleIds,approveRecord);
            }
        }

        UpdateWrapper<GroupsRolesDraft> deleteGroupsRolesDraft = new UpdateWrapper<>();
        deleteGroupsRolesDraft.eq("group_id", group.getId());
        groupsRolesDraftMapper.delete(deleteGroupsRolesDraft);

        UpdateWrapper<GroupsDataRolesDraft> deleteGroupsDataRolesDraft = new UpdateWrapper<>();
        deleteGroupsDataRolesDraft.eq("group_id", group.getId());
        groupsDataRolesDraftMapper.delete(deleteGroupsDataRolesDraft);
    }

    @Override
    public List<Group> getGroupsByUsernames(List<String> usernames) {
        if(CollectionUtils.isEmpty(usernames)){
            return null;
        }
        return baseMapper.listByUsernames(usernames);
    }
    @Override
    public List<TreeData<Object>> getGroupUsersTree() {
        List<TreeData<Object>> treeData = new LinkedList<>();
        List<Group> groups = baseMapper.getGroupUsersTree();
        if(!CollectionUtils.isEmpty(groups)){
            Map<String,List<Group>> groupMap = groups.stream().collect(Collectors.groupingBy(e->e.getId()+"_"+e.getGroupName()));
            for(Map.Entry<String,List<Group>> entry:groupMap.entrySet()){
                String[] groupkeys = entry.getKey().split("_");
                List<Group> groupValues = entry.getValue();
                TreeData groupRoot = new TreeData();
                groupRoot.setKey("g_"+entry.getKey());
                groupRoot.setId(Long.valueOf(groupkeys[0]));
                groupRoot.setLabel(groupkeys[1]);
                groupRoot.setShowType("GROUP");
                List<TreeData> usersTree = new LinkedList<>();
                for(Group g:groupValues){
                    Long userId = g.getUserId();
                    if(userId!=null){
                        String username = g.getUsername();
                        TreeData userTree = new TreeData();
                        userTree.setKey("u_"+userId+"_"+username);
                        userTree.setId(userId);
                        userTree.setLabel(username);
                        userTree.setShowType("USER");
                        User user = new User();
                        user.setId(userId);
                        user.setName(g.getCnUsername());
                        user.setUsername(username);
                        userTree.setData(user);
                        usersTree.add(userTree);
                    }
                }
                groupRoot.setChildren(usersTree);
                treeData.add(groupRoot);
            }
        }
        return treeData;
    }
}