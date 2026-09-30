package com.senses.permission.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.senses.permission.constant.*;
import com.senses.permission.entity.*;
import com.senses.permission.listener.ExcelHeadOneListener;
import com.senses.permission.mapper.*;
import com.senses.permission.model.*;
import com.senses.permission.model.dataroleVo.DataPermissionVo;
import com.senses.permission.model.param.*;
import com.senses.permission.model.vo.*;
import com.senses.permission.service.*;
import com.senses.permission.service.client.ApproveService;
import com.senses.permission.service.client.FileHubService;
import com.senses.permission.service.client.SenseChatClient;
import com.senses.permission.util.CryptoUtils;
import com.senses.permission.util.EmailUtils;
import com.senses.permission.util.RegExUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户表;(user)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{

    @Value("password-key")
    private String passwordKey;

    @Value("reset-password-url")
    private String resetPasswordUrl;

    @Resource
    private DeptsRolesMapper deptsRolesMapper;

    @Resource
    private GroupsRolesMapper groupsRolesMapper;

    @Resource
    private GroupsUsersMapper groupsUsersMapper;

    @Resource
    private DeptMapper deptMapper;

    @Resource
    private DeptsDataRolesMapper deptsDataRolesMapper;

    @Resource
    private GroupsDataRolesMapper groupsDataRolesMapper;

    @Resource
    private ResetPasswordHistoryMapper resetPasswordHistoryMapper;

    @Resource
    private UsersRolesMapper usersRolesMapper;

    @Resource
    private UsersRolesService usersRolesService;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private DataRoleMapper dataRoleMapper;

    @Resource
    private UsersDataRolesMapper usersDataRolesMapper;

    @Resource
    private UsersDataRolesService usersDataRolesService;

    @Resource
    private DataRoleService dataRoleService;

    @Resource
    private RoleService roleService;

    @Resource
    private CryptoUtils cryptoUtils;

    @Resource
    private GroupMapper groupMapper;

    @Resource
    private AsyncFeignService asyncFeignService;

    @Resource
    private FeignApproveDecoratorService feignApproveDecoratorService;

    @Resource
    private ApproveRecordMapper approveRecordMapper;

    @Resource
    private UsersRolesDraftMapper usersRolesDraftMapper;

    @Resource
    private UsersRolesDraftService usersRolesDraftService;

    @Resource
    private UsersDataRolesDraftMapper usersDataRolesDraftMapper;

    @Resource
    private UsersDataRolesDraftService usersDataRolesDraftService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private ApplicationMapper applicationMapper;

    @Resource
    private SenseChatClient senseChatClient;
    @Override
    public List<User> listLikeUsername(String username) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("status",CommonStatusEnum.TRUE.getId());
        if(StringUtils.isNotBlank(username)){
            qw.like("username",username);
        }
        return super.list(qw);
    }

    @Override
    public Page<User> listByPage(PageParam<UserPageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        UserPageParam param = pageParam.getQueryParam();
        Long orId = null;
        if(StringUtils.isNotBlank(param.getFilterVal())
                && StringUtils.isNumeric(param.getFilterVal())
                && !param.getFilterVal().startsWith("0")){
            orId = Long.valueOf(param.getFilterVal());
        }
        Set<Integer> deptIds = new HashSet<>();
        List<Integer> pids = new LinkedList<>();
        if(param.getDeptId()!=null){
            pids.add(param.getDeptId());
            deptIds.add(param.getDeptId());
            while (true){
                QueryWrapper<Dept> deptQw = new QueryWrapper<>();
                deptQw.in("pid",pids);
                List<Dept> depts = deptMapper.selectList(deptQw);
                if(CollectionUtils.isEmpty(depts)){
                    break;
                }
                List<Integer> deptIdList = depts.stream().map(e->e.getId().intValue()).collect(Collectors.toList());
                pids = deptIdList;
                deptIds.addAll(deptIdList);
            }
        }
        List<User> list = baseMapper.listByPage(deptIds,param.getStatus(),param.getFilterVal(),orId);
        page.setRecords(list);
        Long total = baseMapper.count(deptIds,param.getStatus(),param.getFilterVal(),orId);
        page.setTotal(total);
        return page;
    }
    @Override
    public ResultData deleteUser(Long id, String userName) {
        User user = baseMapper.selectById(id);

        if (user.getStatus() == CommonStatusEnum.TRUE.getId()) {
            return ResultData.fail("当前用户使用中，不能删除");
        }

        user.setStatus(CommonStatusEnum.DELETE.getId());
        user.setModifyTime(new Date());
        user.setModifyUser(userName);
        super.updateById(user);

        UpdateWrapper<UsersRoles> delete = new UpdateWrapper<>();
        delete.eq("user_id",id);
        usersRolesMapper.delete(delete);

        UpdateWrapper<UsersDataRoles> deleteDataRoles = new UpdateWrapper<>();
        deleteDataRoles.eq("user_id",id);
        usersDataRolesMapper.delete(deleteDataRoles);

        UpdateWrapper<GroupsUsers> groupsUsersUpdateWrapper = new UpdateWrapper<>();
        groupsUsersUpdateWrapper.eq("user_id",id);
        groupsUsersMapper.delete(groupsUsersUpdateWrapper);

        deleteUserRoleAndDataRoles(user,null);
        return ResultData.success();
    }

    @Override
    public ResultData saveOrUpdateUser(UserParam userParam,String loginUser) {
        if(StringUtils.isBlank(userParam.getUsername())){
            return ResultData.fail("用户名不能为空");
        }
        if(!RegExUtils.isAlphaNumeric(userParam.getUsername())){
            return ResultData.fail("用户名只能由字母和数字组成");
        }
        if(StringUtils.isBlank(userParam.getName())){
            return ResultData.fail("用户姓名不能为空");
        }
        if(StringUtils.isBlank(userParam.getEmail())){
            return ResultData.fail("用户邮箱不能为空");
        }
        if(userParam.getDeptId()==null){
            return ResultData.fail("用户所属部门不能为空");
        }
        if(userParam.getIsAdmin()==null){
            return ResultData.fail("未设置用户是否为管理员角色");
        }
        User existUserName = this.getByUsername(userParam.getUsername());
        if (existUserName!=null && (userParam.getId() == null || !userParam.getId().equals(existUserName.getId())) && existUserName.getStatus()!=CommonStatusEnum.DELETE.getId()){
            return ResultData.fail("用户名已被使用");
        }
        if(existUserName!=null && existUserName.getStatus() == CommonStatusEnum.DELETE.getId()){
            userParam.setId(existUserName.getId());
        }

        User existEmail = baseMapper.selectExistEmail(userParam.getEmail());
        if (existEmail!=null && (userParam.getId() == null || !userParam.getId().equals(existEmail.getId()))){
            return ResultData.fail("用户邮箱已被使用");
        }
        User user;
        Date date = new Date();
        boolean add = false;
        if(userParam.getId() == null){
            user = new User();
            user.setCreatedTime(date);
            user.setSource(UserSourceEnum.IN_CREATE.getId());
            add = true;
        }else{
            user = baseMapper.selectById(userParam.getId());
        }
        if(userParam.getId() == null ||(existUserName!=null && existUserName.getStatus()==CommonStatusEnum.DELETE.getId())){
            if(StringUtils.isBlank(userParam.getPassword())){
                return ResultData.fail("密码不能为空");
            }
            user.setCreatedUser(loginUser);
            user.setStatus(CommonStatusEnum.TRUE.getId());
            user.setPassword(DigestUtils.md5Hex(userParam.getPassword()+passwordKey));
        }
        user.setModifyTime(date);
        user.setModifyUser(loginUser);
        user.setUsername(userParam.getUsername());
        user.setName(userParam.getName());
        user.setPhone(userParam.getPhone());
        user.setEmail(userParam.getEmail());
        user.setDeptId(userParam.getDeptId());
        user.setIsAdmin(userParam.getIsAdmin());
        super.saveOrUpdate(user);

        //新增用户异步初始化分享目录
        if(add){
            asyncFeignService.saveShareDirectory(userParam.getUsername());
        }
        DataRole dataRole = dataRoleMapper.selectPersonDataRole(user.getId());
        if(dataRole == null){
            dataRole = DataRole.newPersonDataRole(user);
            dataRoleMapper.insert(dataRole);
            UsersDataRoles usersDataRoles = new UsersDataRoles();
            usersDataRoles.setUserId(user.getId());
            usersDataRoles.setDataRoleId(dataRole.getId());
            usersDataRolesMapper.insert(usersDataRoles);
        }else{
            if(!dataRole.getDeptId().equals(userParam.getDeptId())){
                dataRole.setDeptId(userParam.getDeptId());
                dataRoleMapper.updateById(dataRole);
            }
        }
        return ResultData.success();
    }

    @Override
    public User getInfo(Long userId) {
        User user = baseMapper.selectInfoByUserId(userId);
        return user;
    }

    @Override
    public ResultData sendResetPasswordUrl(Long userId, String userName) {
        User user = baseMapper.selectById(userId);
        Date date = new Date();
        return ResultData.success();
    }

    @Override
    public ResultData checkResetUrl(String resetToken) {
        ResultData<ResetPasswordHistory> checkToken = checkToken(resetToken);
        if(checkToken.getCode()==ReturnCode.RC0.getCode()){
            return checkToken;
        }
        ResetPasswordHistory resetPasswordHistory = checkToken.getData();
        ResultData<User> checkUser = checkUserStatus(resetPasswordHistory.getUserId());
        if(checkUser.getCode()==ReturnCode.RC0.getCode()){
            return checkUser;
        }
        return ResultData.success();
    }

    private ResultData<User> checkUserStatus(Long userId) {
        User user = baseMapper.selectById(userId);
        if(user.getStatus()== CommonStatusEnum.DELETE.getId()){
            return ResultData.fail("用户已删除");
        }
        return ResultData.success(user);
    }

    private ResultData<ResetPasswordHistory> checkToken(String token){
        QueryWrapper<ResetPasswordHistory> qw = new QueryWrapper<>();
        qw.eq("reset_password_url",token);
        ResetPasswordHistory resetPasswordHistory = resetPasswordHistoryMapper.selectOne(qw);
        if(resetPasswordHistory == null){
            return ResultData.fail("链接不存在");
        }
        Date date = new Date();
        if(resetPasswordHistory.getExpireTime().before(date)){
            return ResultData.fail("链接已过期");
        }
        return ResultData.success(resetPasswordHistory);
    }
    @Override
    public ResultData resetPassword(UserResetPasswordParam userResetPasswordParam) {

        ResultData<ResetPasswordHistory> checkToken = checkToken(userResetPasswordParam.getResetToken());
        if(checkToken.getCode()==ReturnCode.RC0.getCode()){
            return checkToken;
        }
        ResetPasswordHistory resetPasswordHistory = checkToken.getData();
        ResultData<User> checkUser = checkUserStatus(resetPasswordHistory.getUserId());
        if(checkUser.getCode()==ReturnCode.RC0.getCode()){
            return checkUser;
        }
        if(StringUtils.isBlank(userResetPasswordParam.getPassword())){
            return ResultData.fail("登录密码不能为空");
        }
        User user = checkUser.getData();
        user.setLastPasswordResetTime(new Date());
        user.setPassword(DigestUtils.md5Hex(userResetPasswordParam.getPassword()+passwordKey));
        baseMapper.updateById(user);
        return ResultData.success();
    }

    @Override
    public ResultData updateStatus(Long id, Integer status,String userName) {
        User user = baseMapper.selectById(id);
        if(user.getStatus()==CommonStatusEnum.DELETE.getId()){
            return ResultData.fail("用户已删除，不支持变更状态");
        }
        user.setStatus(status);
        user.setModifyTime(new Date());
        user.setModifyUser(userName);
        super.updateById(user);
        return ResultData.success();
    }

    @Override
    public UserRolesIdsVO getRoles(User user) {
        UserRolesIdsVO userAllRolesListVO = new UserRolesIdsVO();
        List<Long> deptIds = getAllUserDept(user);
        setRoleIds(userAllRolesListVO,user,deptIds);
        setDataRoleIds(userAllRolesListVO,user,deptIds,false);
        return userAllRolesListVO;
    }

    @Override
    public UserRolesIdsVO getAllRoles(User user) {
        UserRolesIdsVO userAllRolesListVO = new UserRolesIdsVO();
        List<Long> deptIds = getAllUserDept(user);
        setRoleIds(userAllRolesListVO,user,deptIds);
        setDataRoleIds(userAllRolesListVO,user,deptIds,true);
        return userAllRolesListVO;
    }

    private void setRoleIds(UserRolesIdsVO userAllRolesListVO,User user,List<Long> deptIds){
        List<UsersRoles> usersRoles = usersRolesMapper.selectListByUserId(user.getId());
        List<Long> roleIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(usersRoles)){
            roleIds = usersRoles.stream().map(e->e.getRoleId()).collect(Collectors.toList());
        }
        Set<Long> cannotRoleIds = new LinkedHashSet<>();
        List<DeptsRoles> deptsRoles = deptsRolesMapper.selectListByDeptIds(deptIds);
        if(!CollectionUtils.isEmpty(deptsRoles)){
            List<Long> deptRoleIds = deptsRoles.stream().map(e->e.getRoleId()).collect(Collectors.toList());
            cannotRoleIds.addAll(deptRoleIds);
        }
        List<GroupsRoles> groupsRoles = groupsRolesMapper.selectTrueListByUserId(user.getId());
        if(!CollectionUtils.isEmpty(groupsRoles)){
            List<Long> groupRoleIds = groupsRoles.stream().map(e->e.getRoleId()).collect(Collectors.toList());
            cannotRoleIds.addAll(groupRoleIds);
        }

        userAllRolesListVO.setRoleIds(roleIds);
        userAllRolesListVO.setCannotRoleIds(cannotRoleIds);
    }

    private void setDataRoleIds(UserRolesIdsVO userAllRolesListVO,User user,List<Long> deptIds,boolean allDataRoles){

        List<UsersDataRoles> usersDataRoles;
        if(allDataRoles){
            usersDataRoles = usersDataRolesMapper.selectAllListByUserId(user.getId());
        }else{
            usersDataRoles = usersDataRolesMapper.selectListByUserId(user.getId());
        }

        List<Long> dataRoleIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(usersDataRoles)){
            dataRoleIds = usersDataRoles.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());
        }

        Set<Long> cannotDataRoleIds = new LinkedHashSet<>();
        List<DeptsDataRoles> deptsDataRoles = deptsDataRolesMapper.selectListBydeptIds(deptIds);
        if(!CollectionUtils.isEmpty(deptsDataRoles)){
            List<Long> deptDataRoleIds = deptsDataRoles.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());
            cannotDataRoleIds.addAll(deptDataRoleIds);
        }
        List<GroupsDataRoles> groupsDataRoles = groupsDataRolesMapper.selectTrueListByUserId(user.getId());
        if(!CollectionUtils.isEmpty(groupsDataRoles)){
            List<Long> groupDataRoleIds = groupsDataRoles.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());
            cannotDataRoleIds.addAll(groupDataRoleIds);
        }

        userAllRolesListVO.setDataRoleIds(dataRoleIds);
        userAllRolesListVO.setCannotDataRoleIds(cannotDataRoleIds);
    }

    @Override
    public ResultData bind(Long userId, List<Long> roleIds, List<Long> dataRoleIds,String username) {
        User user = baseMapper.selectById(userId);
        if(user.getStatus() == CommonStatusEnum.DELETE.getId()){
            return ResultData.fail("用户已被删除，不能再授权");
        }
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_USER.getVal(),userId);
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            return ResultData.fail("存在审批中的用户绑定权限申请，请先联系审批中心，处理上次申请！");
        }

        String workName = user.getUsername()+"【用户授权】";
        approveRecord = ApproveRecord.createApprove(userId, ApproveFlowTagEnum.UPC_USER, username,workName);
        approveRecordMapper.insert(approveRecord);
        ResultData<List<Role>> bindRoleResult = bindRole(user,roleIds,ApplicantTypeEnum.CONTROL.getId());
        if(bindRoleResult.getCode()!=ReturnCode.RC1.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            return bindRoleResult;
        }

        ResultData<List<DataRole>> bindDataRoleResult = bindDataRole(user,dataRoleIds);
        if(bindDataRoleResult.getCode()!=ReturnCode.RC1.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteUsersRolesDraft(userId,roleIds);
            return bindDataRoleResult;
        }

        String worksheetData = ApproveWorksheet.createRolesAndDataRoleWorksheetData(bindRoleResult.getData(),bindDataRoleResult.getData());
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(approveRecord.getId().toString(),workName,username,worksheetData,BindMarkEnum.UPC_BINDROLE_USER.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,"用户"+user.getUsername()+"授权发送申请单返回结果：");

        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteUsersRolesDraft(userId,roleIds);
            deleteUsersDataRolesDraft(userId,dataRoleIds);
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getMessage());
        }
        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        approveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(approveRecord);
        //发送用户绑定权限审计
        asyncFeignService.addPersonBindRoleAuditLog(user,roleIds,approveRecord);
        asyncFeignService.addPersonBindDataRoleAuditLog(user,dataRoleIds,approveRecord);

        return ResultData.success();
    }

    private void deleteUsersRolesDraft(Long userId, List<Long> roleIds) {
        if(!CollectionUtils.isEmpty(roleIds)){
            UpdateWrapper<UsersRolesDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("user_id",userId);
            updateWrapper.in("role_id",roleIds);
            usersRolesDraftMapper.delete(updateWrapper);
        }
    }
    private void deleteUsersDataRolesDraft(Long userId, List<Long> dataRoleIds) {
        if(!CollectionUtils.isEmpty(dataRoleIds)){
            UpdateWrapper<UsersDataRolesDraft> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("user_id",userId);
            updateWrapper.in("data_role_id",dataRoleIds);
            usersDataRolesDraftMapper.delete(updateWrapper);
        }
    }
    private ResultData<List<Role>> bindRole(User user,List<Long> roleIds,Integer applicantType){
        List<Role> roles = null;
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
        addUserRolesDraft(user.getId(), roleIds,applicantType);
        return ResultData.success(roles);
    }

    private ResultData<List<DataRole>> bindDataRole(User user,List<Long> dataRoleIds){
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
        addUserDataRolesDraft(user.getId(),dataRoleIds);
        return ResultData.success(dataRoles);
    }
    private void addUserDataRolesDraft(Long userId, List<Long> dataRoleIds) {
        List<UsersDataRolesDraft> list = new ArrayList<>(dataRoleIds.size());
        for(Long dataRoleId:dataRoleIds){
            UsersDataRolesDraft usersDataRolesDraft = new UsersDataRolesDraft();
            usersDataRolesDraft.setUserId(userId);
            usersDataRolesDraft.setDataRoleId(dataRoleId);
            list.add(usersDataRolesDraft);
        }
        usersDataRolesDraftService.saveBatch(list);
    }

    private void addUserRolesDraft(Long userId, List<Long> roleIds,Integer applicantType) {
        List<UsersRolesDraft> list = new ArrayList<>(roleIds.size());
        for(Long roleId:roleIds){
            UsersRolesDraft usersRolesDraft = new UsersRolesDraft();
            usersRolesDraft.setUserId(userId);
            usersRolesDraft.setRoleId(roleId);
            usersRolesDraft.setApplicantType(applicantType);
            list.add(usersRolesDraft);
        }
        usersRolesDraftService.saveBatch(list);
    }


    private void addOrUpdateUsersRoles(User user,ApproveRecord approveRecord,Integer applicantType) {
        List<UsersRolesDraft> usersRolesDraftsList = usersRolesDraftMapper.selectListByUserIdAndApplicantType(user.getId(),applicantType);
        if(CollectionUtils.isEmpty(usersRolesDraftsList)){
            usersRolesDraftsList = new ArrayList<>();
        }
        List<Long> addIds = usersRolesDraftsList.stream().map(e->e.getRoleId()).collect(Collectors.toList());
        UpdateWrapper<UsersRoles> delete = new UpdateWrapper<>();
        delete.eq("user_id",user.getId());
        usersRolesMapper.delete(delete);
        if(!CollectionUtils.isEmpty(addIds)){
            List<UsersRoles> usersRolesList = new ArrayList<>(addIds.size());
            for(Long addId:addIds){
                UsersRoles usersRoles = new UsersRoles();
                usersRoles.setUserId(user.getId());
                usersRoles.setRoleId(addId);
                usersRolesList.add(usersRoles);
            }
            usersRolesService.saveBatch(usersRolesList);
            asyncFeignService.addPersonBindRoleAuditLog(user,addIds,approveRecord);
        }
    }

    private void addOrUpdateUsersDataRoles(User user,ApproveRecord approveRecord) {
        List<UsersDataRolesDraft> usersDataRoleDraftList = usersDataRolesDraftMapper.selectListByUserId(user.getId());
        if(CollectionUtils.isEmpty(usersDataRoleDraftList)){
            usersDataRoleDraftList = new ArrayList<>();
        }
        List<Long> addIds = usersDataRoleDraftList.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());

        DataRole personDataRole = dataRoleMapper.selectPersonDataRole(user.getId());
        UpdateWrapper<UsersDataRoles> deleteDataRoles = new UpdateWrapper<>();
        deleteDataRoles.eq("user_id",user.getId());
        deleteDataRoles.ne("data_role_id",personDataRole.getId());
        usersDataRolesMapper.delete(deleteDataRoles);
        if(!CollectionUtils.isEmpty(addIds)){
            List<UsersDataRoles> usersDataRolesList = new ArrayList<>(addIds.size());
            for(Long addId:addIds){
                UsersDataRoles usersDataRoles = new UsersDataRoles();
                usersDataRoles.setUserId(user.getId());
                usersDataRoles.setDataRoleId(addId);
                usersDataRolesList.add(usersDataRoles);
            }
            usersDataRolesService.saveBatch(usersDataRolesList);
            asyncFeignService.addPersonBindDataRoleAuditLog(user,addIds,approveRecord);
        }
    }

    @Override
    public List<TreeData<Role>> roleTreeList(User user) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_USER.getVal(),user.getId());
        Integer approveStatus = null;
        if(approveRecord!=null){
            approveStatus = approveRecord.getApproveStatus();
        }
        List<Long> deptIds = getAllUserDept(user);
        List<Role> roles = baseMapper.roleTreeList(user.getId(),deptIds,approveStatus);
        List<Application> applications = baseMapper.getUserApplication(user.getId(),deptIds,approveStatus);
        return roleService.installAppRoleTree(applications,roles);
    }

    @Override
    public List<TreeData<DataRole>> dataRoleTreeList(User user) {
        List<Long> deptIds = getAllUserDept(user);
        List<DataRole> roles = baseMapper.dataRoleTreeList(user.getId(),deptIds);
        List<Dept> depts = new ArrayList<>();
        if(!CollectionUtils.isEmpty(roles)){
            Set<Long> dataRoleDeptIds = roles.stream().map(DataRole::getDeptId).collect(Collectors.toSet());
            depts = getAllParentDept(dataRoleDeptIds);
        }
        return dataRoleService.installRoleTree(depts, roles);
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
    private List<Dept> getAllParentDept(Set<Long> deptIds){
        Set<Dept> depts = new HashSet<>();
        List<Dept> deptList = deptMapper.selectBatchIds(deptIds);
        depts.addAll(deptList);
        Set<Long> pids = deptList.stream().map(e->e.getPid()).collect(Collectors.toSet());
        while(true){
            deptList = deptMapper.selectBatchIds(pids);
            if(!CollectionUtils.isEmpty(deptList)){
                depts.addAll(deptList);
                pids = deptList.stream().map(e->e.getPid()).collect(Collectors.toSet());
            }else{
                break;
            }
        }
        return depts.stream().collect(Collectors.toList());
    }
    private List<Long> getAllUserDept(Long userId){
        User user = baseMapper.selectById(userId);
        return getAllUserDept(user);
    }

    @Override
    public List<Long> getAllUserDept(User user){
        List<Long> deptIds = new LinkedList<>();
        Long deptId = user.getDeptId();
        while(true){
            deptIds.add(deptId);
            Dept dept = deptMapper.selectById(deptId);
            if(dept!=null && dept.getPid()!=0){
                deptId = dept.getPid();
            }else{
                break;
            }
        }
        return deptIds;
    }

    @Override
    public User login(UserParam userParam) {
        String plainPassword;
        try {
            plainPassword = cryptoUtils.decryptPassword(userParam.getPassword());
        } catch (Exception e) {
            log.warn("RSA 解密失败，拒绝登录: {}", e.getMessage());
            return null;
        }
        return doLogin(userParam, plainPassword);
    }

    @Override
    public User loginByPlainPassword(UserParam userParam) {
        return doLogin(userParam, userParam.getPassword());
    }

    @Override
    public User loginByPlainPasswordSimple(UserParam userParam) {
        User user = selectByUsernameAndPassword(userParam.getUsername(), userParam.getPassword());
        if(user != null){
            user.setPassword(null);
        }
        return user;
    }

    private User doLogin(UserParam userParam, String plainPassword) {
        User user = selectByUsernameAndPassword(userParam.getUsername(), plainPassword);
        if(user == null){
            return null;
        }
        List<TreeData<Permission>> permissionTree = getPermissionTree(user.getId());
        user.setPermissionTree(permissionTree);

        List<RoleInfoVO> roleInfos = getAllRoleInfos(user.getId());
        user.setRoleInfos(roleInfos);
        List<Long> deptIds = getAllUserDept(user);
        List<DataRole> roles = baseMapper.dataRoleTreeList(user.getId(),deptIds);
        List<Long> dataRoleIds = roles.stream().map(DataRole::getId).collect(Collectors.toList());
        user.setDataRoleIds(dataRoleIds);
        user.setPassword(null);
        return user;
    }

    private User selectByUsernameAndPassword(String username, String plainPassword) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq("username", username);
        wrapper.eq("password", DigestUtils.md5Hex(plainPassword + passwordKey));
        wrapper.ne("status", CommonStatusEnum.DELETE.getId());
        return baseMapper.selectOne(wrapper);
    }

    private List<RoleInfoVO> getAllRoleInfos(Long userId) {
        List<RoleInfoVO> roleInfoVOS = roleService.getAllUserRoleInfos(userId);
        //当有条件判断时，处理器会预处理为true，所以首先判断最有可能的结果，速度会更快
        if(roleInfoVOS != null){
            return roleInfoVOS;
        }
        roleInfoVOS = new LinkedList<>();
        return roleInfoVOS;
    }

    private List<TreeData<Permission>> getPermissionTree(Long userId){
        List<TreeData<Permission>> permissionTree = new LinkedList<>();
        List<Long> deptIds = getAllUserDept(userId);
        List<Application> applications = applicationMapper.selectByUserId(userId,deptIds);
        log.info("应用："+ JSON.toJSONString(applications));
        Map<Long,List<Permission>> appPermissions = null;
        if(!CollectionUtils.isEmpty(applications)){
            List<Permission> permissions = permissionMapper.getViewPermissionList(userId,deptIds);
            if(!CollectionUtils.isEmpty(permissions)){
                appPermissions = permissions.stream().collect(Collectors.groupingBy(Permission::getAppId));
//                Map<Long,Permission> permissionMap = new HashMap<>();
//                permissions.stream().forEach(e->permissionMap.put(e.getId(),e));
            }
        }else{
            return permissionTree;
        }
        for(Application application:applications){
            TreeData<Permission> treeRoot = new TreeData<>();
            treeRoot.setKey(application.getCode());
            treeRoot.setLabel(application.getName());
            treeRoot.setId(application.getId());
            treeRoot.setShowType("Application");
            if(appPermissions!=null){
                List<Permission> permissions = appPermissions.get(application.getId());
                if(!CollectionUtils.isEmpty(permissions)){
                    Map<Long,List<Permission>> pidPermissionMap = permissions.stream().collect(Collectors.groupingBy(Permission::getPid));
                    List<Permission> appRootMenus = pidPermissionMap.get(0l);
                    if(appRootMenus!=null){
                        for(Permission p1:appRootMenus){
                            insertPermissionTree(treeRoot,p1,pidPermissionMap);
                        }
                    }
                }
            }
            permissionTree.add(treeRoot);
        }
        return permissionTree;
    }

    private void insertPermissionTree(TreeData<Permission> parentTreeData, Permission permission, Map<Long, List<Permission>> pidPermissionMap) {
        TreeData<Permission> childrenTreeData = new TreeData<>();
        childrenTreeData.setKey(permission.getSign());
        childrenTreeData.setLabel(permission.getName());
        childrenTreeData.setId(permission.getId());
        childrenTreeData.setShowType(getPermissionShowType(permission.getType()));
        childrenTreeData.setData(permission);
        List<TreeData<Permission>> childrens = parentTreeData.getChildren();
        if(childrens == null){
            childrens = new LinkedList<>();
        }
        childrens.add(childrenTreeData);
        List<Permission> sonPermissions = pidPermissionMap.get(permission.getId());
        if(!CollectionUtils.isEmpty(sonPermissions)){
            for(Permission sonPermission:sonPermissions){
                insertPermissionTree(childrenTreeData,sonPermission,pidPermissionMap);
            }
        }
        parentTreeData.setChildren(childrens);
    }

    private String getPermissionShowType(Integer type) {
        String showType = null;
        switch (type){
            case 0:
                showType = "Menu";
                break;
            case 1:
                showType = "Button";
                break;
        }
        return showType;
    }

    @Override
    public User getByUsername(String username) {
        User user = baseMapper.selectByUsername(username);
        return user;
    }

    @Override
    public User getInfoAndPermissionTreeByUsername(String username) {
        User user = baseMapper.selectByUsername(username);
        if(user!=null){
            List<TreeData<Permission>> permissionTree = getPermissionTree(user.getId());
            user.setPermissionTree(permissionTree);
            List<RoleInfoVO> roleInfos = getAllRoleInfos(user.getId());
            user.setRoleInfos(roleInfos);
            List<Long> deptIds = getAllUserDept(user);
            List<DataRole> roles = baseMapper.dataRoleTreeList(user.getId(),deptIds);
            List<Long> dataRoleIds = roles.stream().map(DataRole::getId).collect(Collectors.toList());
            user.setDataRoleIds(dataRoleIds);
        }
        return user;
    }

    @Override
    public List<User> listByDeptsOrGroupsOrName(DeptGroupNameUserParam deptGroupNameUserParam) {
        List<User> users = baseMapper.listByDeptsOrGroupsOrName(deptGroupNameUserParam);
        return users;
    }

    @Override
    public List<User> getInfoByIds(List<Long> userIds) {
        if(CollectionUtils.isEmpty(userIds)){
            return new LinkedList<>();
        }
        List<User> userList = baseMapper.getInfoByIds(userIds);

        for(User user:userList){
            List<Dept> allParentDept = getAllParentDept(user.getDeptId());
            user.setDepts(allParentDept);
        }
        List<Group> groups = groupMapper.listByUserIds(userIds);
        Map<Long,List<Group>> userGroupMap = groups.stream().collect(Collectors.groupingBy(Group::getUserId));
        for(User user : userList){
            List<Group> userGroups = userGroupMap.get(user.getId());
            if(userGroups == null){
                userGroups = new LinkedList<>();
            }
            user.setGroups(userGroups);
        }
        return userList;
    }

    @Override
    public List<User> getInfoByUsernames(List<String> userNames) {
        if(CollectionUtils.isEmpty(userNames)){
            return new LinkedList<>();
        }
        List<User> userList = baseMapper.getInfoByUsernames(userNames);
        if(CollectionUtils.isEmpty(userList)){
            return new LinkedList<>();
        }

        for(User user:userList){
            List<Dept> allParentDept = getAllParentDept(user.getDeptId());
            user.setDepts(allParentDept);
        }

        List<Group> groups = groupMapper.listByUserIds(userList.stream().map(e->e.getId()).collect(Collectors.toList()));
        Map<Long,List<Group>> userGroupMap = groups.stream().collect(Collectors.groupingBy(Group::getUserId));
        for(User user : userList){
            List<Group> userGroups = userGroupMap.get(user.getId());
            if(userGroups == null){
                userGroups = new LinkedList<>();
            }
            user.setGroups(userGroups);
        }
        return userList;
    }

    @Override
    public void confirmApprove(ApproveRecord approveRecord) {
        Long userId = approveRecord.getApproveTargetId();
        User user = super.getById(userId);
        if(approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()){
            addOrUpdateUsersRoles(user,approveRecord, ApplicantTypeEnum.CONTROL.getId());
            addOrUpdateUsersDataRoles(user,approveRecord);
            deleteUserRoleAndDataRoles(user,approveRecord);
        }else{
            deleteUserRoleAndDataRoles(user,approveRecord);
        }
    }

    @Override
    public List<TreeData<DataRole>> getMyDeptDataRoleTreeList(User user) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_USER.getVal(),user.getId());
        Integer approveStatus = null;
        if(approveRecord!=null){
            approveStatus = approveRecord.getApproveStatus();
        }
        List<Dept> depts = getAllParentDept(user.getDeptId());
        List<DataRole> dataRoles = baseMapper.getMyDeptDataRoleTreeList(user.getId(),depts.stream().map(e->e.getId()).collect(Collectors.toList()),approveStatus);
        return dataRoleService.installRoleTree(depts,dataRoles);
    }


    @Override
    public List<TreeData<DataRole>> getMyGroupDataRoleTreeList(User user) {
        List<TreeData<DataRole>> rootList = new LinkedList<>();
        List<Group> groups = groupMapper.listByUserId(user.getId());
        if(!CollectionUtils.isEmpty(groups)){
            List<DataRole> dataRoles = baseMapper.getMyGroupDataRoleTreeList(user.getId());
            Map<Long,List<DataRole>> groupDataRoleMap = dataRoles.stream().collect(Collectors.groupingBy(DataRole::getGroupId));
            for(Group group:groups){
                TreeData<DataRole> treeData = createGroupRootTree(group);
                List<DataRole> dataRoleVals = groupDataRoleMap.get(group.getId());
                if(!CollectionUtils.isEmpty(dataRoleVals)){
                    List<Dept> depts = getAllParentDept(dataRoleVals.stream().map(e->e.getDeptId()).collect(Collectors.toSet()));
                    List<TreeData<DataRole>> deptDataRoleTrees = dataRoleService.installRoleTree(depts,dataRoleVals);
                    treeData.setChildren(deptDataRoleTrees);
                    rootList.add(treeData);

                }
            }


        }

        return rootList;
    }

    private TreeData<DataRole> createGroupRootTree(Group group) {
        TreeData<DataRole> treeData = new TreeData<>();
        treeData.setKey(0+"_group_"+group.getId());
        treeData.setId(group.getId());
        treeData.setLabel(group.getGroupName());
        treeData.setShowType("Group");
        return treeData;
    }

    @Override
    public List<DataRolesPermission> myListByPageDataRolePermission(User user,UserPersonDataRoleParam userPersonDataRoleParam) {
        List<DataRolesPermission> list = baseMapper.getMyDataRoleTreeList(user.getId(),userPersonDataRoleParam);
        return list;
    }

    @Override
    public ResultData applyDataPermission(User user, Set<DataPermissionVo> dataPermissions,ApproveFlowTagEnum approveFlowTagEnum) {
        DataRole personDataRole = dataRoleMapper.selectPersonDataRole(user.getId());
        if(ApproveFlowTagEnum.UPC_DATA_ROLE.equals(approveFlowTagEnum)){
            ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(approveFlowTagEnum.getVal(),personDataRole.getId());
            if(approveRecord!=null && approveRecord.getApproveStatus()==ApproveStatusEnum.WAIT_APPROVE.getId()){
                return ResultData.fail("当前角色存在审批中的数据权限绑定申请，请先处理上次申请！");
            }
        }
        return dataRoleService.sendApproveInfo(personDataRole,dataPermissions,user.getUsername(),AuthRoleTypeEnum.PERSON.getId(),approveFlowTagEnum);
    }

    @Override
    public UserRolesIdsVO getMyDataRoleIds(User user) {
        UserRolesIdsVO userAllRolesListVO = new UserRolesIdsVO();
        List<Long> deptIds = getAllUserDept(user);
        setDataRoleIds(userAllRolesListVO,user,deptIds,false);
        return userAllRolesListVO;
    }
    @Override
    public UserRolesIdsVO getMyRoleIds(User user) {
        UserRolesIdsVO userAllRolesListVO = new UserRolesIdsVO();
        List<Long> deptIds = getAllUserDept(user);
        setRoleIds(userAllRolesListVO,user,deptIds);
        return userAllRolesListVO;
    }

    @Override
    public ResultData initUserDataRole() {
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.ne("status",CommonStatusEnum.DELETE.getId());
        List<User> users = baseMapper.selectList(userQueryWrapper);
        for(User user:users){
            DataRole dataRole = dataRoleMapper.selectPersonDataRole(user.getId());
            if(dataRole == null){
                dataRole = DataRole.newPersonDataRole(user);
                dataRoleMapper.insert(dataRole);
                UsersDataRoles usersDataRoles = new UsersDataRoles();
                usersDataRoles.setUserId(user.getId());
                usersDataRoles.setDataRoleId(dataRole.getId());
                usersDataRolesMapper.insert(usersDataRoles);
            }

        }
        return ResultData.success();
    }

    @Override
    public ResultData applyRoles(User user, List<Long> roleIds) {
        ApproveRecord approveRecord = approveRecordMapper.lastByFlagAndTargetId(ApproveFlowTagEnum.UPC_PER_USER_ROLE.getVal(),user.getId());
        if(approveRecord!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.WAIT_APPROVE.getId()){
            return ResultData.fail("存在审批中的用户绑定功能角色申请，请先联系审批中心，处理上次申请！");
        }

        String workName = user.getUsername()+"【个人功能角色申请】";
        approveRecord = ApproveRecord.createApprove(user.getId(), ApproveFlowTagEnum.UPC_PER_USER_ROLE, user.getUsername(),workName);
        approveRecordMapper.insert(approveRecord);
        ResultData<List<Role>> bindRoleResult = bindRole(user,roleIds,ApplicantTypeEnum.PERSON.getId());
        if(bindRoleResult.getCode()!=ReturnCode.RC1.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            return bindRoleResult;
        }



        String worksheetData = ApproveWorksheet.createRolesAndDataRoleWorksheetData(bindRoleResult.getData(),null);
        ApproveWorksheet workSheetDTO = new ApproveWorksheet(approveRecord.getId().toString(),workName,user.getUsername(),worksheetData,BindMarkEnum.UPC_BINDROLE_PERMISSION.getCode());

        ResultData<Map<String,Object>> sendApproveResult = feignApproveDecoratorService.saveWorksheet(workSheetDTO,"用户"+user.getUsername()+"授权发送申请绑定功能角色返回结果：");
        if(sendApproveResult.getCode() == ReturnCode.RC0.getCode()){
            approveRecordMapper.deleteById(approveRecord.getId());
            deleteUsersRolesDraft(user.getId(),roleIds);
            return ResultData.fail("创建审批单失败，失败原因："+sendApproveResult.getMessage());
        }
        Map<String,Object> approveResultMap = sendApproveResult.getData();
        String workOrderNo = String.valueOf(approveResultMap.get("workOrderNumber"));
        approveRecord.setWorkOrderNo(workOrderNo);
        approveRecordMapper.updateById(approveRecord);
        //发送用户绑定权限审计
        asyncFeignService.addPersonBindRoleAuditLog(user,roleIds,approveRecord);
        return ResultData.success();
    }

    @Override
    public void confirmApproveRole(ApproveRecord approveRecord) {
        Long userId = approveRecord.getApproveTargetId();
        User user = super.getById(userId);
        if(approveRecord.getApproveStatus() == ApproveStatusEnum.APPROVED.getId()){
            addOrUpdateUsersRoles(user,approveRecord,ApplicantTypeEnum.PERSON.getId());
            deleteUserRole(user,approveRecord,ApplicantTypeEnum.PERSON.getId());
        }else{
            deleteUserRole(user,approveRecord,ApplicantTypeEnum.PERSON.getId());
        }

    }

    private void deleteUserRole(User user, ApproveRecord approveRecord,Integer applicantType) {
        if(approveRecord!=null && approveRecord.getApproveStatus()!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.NO_APPROVED.getId()){
            List<UsersRolesDraft> usersRolesDrafts = usersRolesDraftMapper.selectListByUserIdAndApplicantType(user.getId(),applicantType);
            if(!CollectionUtils.isEmpty(usersRolesDrafts)){
                List<Long> roleIds = usersRolesDrafts.stream().map(e->e.getRoleId()).collect(Collectors.toList());
                asyncFeignService.addPersonBindRoleAuditLog(user,roleIds,approveRecord);
            }
        }
        UpdateWrapper<UsersRolesDraft> deleteUsersRolesDraft = new UpdateWrapper<>();
        deleteUsersRolesDraft.eq("user_id",user.getId());
        deleteUsersRolesDraft.eq("applicant_type",applicantType);
        usersRolesDraftMapper.delete(deleteUsersRolesDraft);
    }

    private void deleteUserDataRole(User user, ApproveRecord approveRecord) {
        if(approveRecord!=null && approveRecord.getApproveStatus()!=null && approveRecord.getApproveStatus() == ApproveStatusEnum.NO_APPROVED.getId()){
            List<UsersDataRolesDraft> usersDataRolesDrafts = usersDataRolesDraftMapper.selectListByUserId(user.getId());
            if(!CollectionUtils.isEmpty(usersDataRolesDrafts)){
                List<Long> dataRoleIds = usersDataRolesDrafts.stream().map(e->e.getDataRoleId()).collect(Collectors.toList());
                asyncFeignService.addPersonBindDataRoleAuditLog(user,dataRoleIds,approveRecord);
            }

        }

        UpdateWrapper<UsersDataRolesDraft> deleteDataRolesDraft = new UpdateWrapper<>();
        deleteDataRolesDraft.eq("user_id",user.getId());
        usersDataRolesDraftMapper.delete(deleteDataRolesDraft);
    }


    private void deleteUserRoleAndDataRoles(User user,ApproveRecord approveRecord){
        deleteUserRole(user,approveRecord, ApplicantTypeEnum.CONTROL.getId());
        deleteUserDataRole(user,approveRecord);
    }

    public List<String> getUserList(Long deptId){
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        userWrapper.eq("dept_id",deptId);
        List<User> userList = userMapper.selectList(userWrapper);
        List<String> usernameList = new ArrayList<>();
        for(User user : userList){
            usernameList.add(user.getUsername());
        }
        return usernameList;
    }



    public List<User> listByDeptsOrStatusOrName(Map<String,Object> map) {
        List deptList = (List) map.get("deptId");
        List statusList = (List) map.get("status");
        String keywords1 = (String) map.get("keywords1");
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        userWrapper.notIn("status",2);
        if (null != keywords1){
            userWrapper.and(Wrapper -> Wrapper.like("username",keywords1).or().like("name",keywords1));
        }
        if (null != deptList && !deptList.isEmpty()){
            userWrapper.in("dept_id",deptList);
        }
        if (null != statusList && !statusList.isEmpty()){
            userWrapper.in("status",statusList);
        }
        List<User> users = userMapper.selectList(userWrapper);
        return users;
    }

    @Override
    public ResultData updatePassword(String username, UpdatePasswordParam updatePasswordParam) {
        return updatePassword(username,null,updatePasswordParam.getNewPassword(),updatePasswordParam.getRePassword());
    }

    public Map<Long,String> getUserIdName(){
        Map<Long,String> map =  new HashMap<>();
        List<User> list = this.list();
        list.stream().forEach(user->{
            map.put(user.getId(),user.getUsername());
        });
        return map;
    }

    @Override
    public ResultData loginPreUpdatePassword(LoginPreUpdatePasswordParam loginPreUpdatePasswordParam) {
        if(StringUtils.isBlank(loginPreUpdatePasswordParam.getUsername())){
            return ResultData.fail("用户名不能为空");
        }
        if(StringUtils.isBlank(loginPreUpdatePasswordParam.getOldPassword())){
            return ResultData.fail("旧密码不能为空");
        }
        return updatePassword(loginPreUpdatePasswordParam.getUsername(),loginPreUpdatePasswordParam.getOldPassword(),
                loginPreUpdatePasswordParam.getNewPassword(),loginPreUpdatePasswordParam.getRePassword());
    }

    @Override
    public ResultData<ImportUserResultVo> upload(String username, MultipartFile file) {
        String uploadRootDir = "/tmp/importUser/";
        ResultData<File> saveFile = null;
        try{
            saveFile = saveFile(username,file,uploadRootDir);
            if(saveFile.getCode() == ReturnCode.RC0.getCode()){
                return ResultData.fail(saveFile.getMessage());
            }
        }catch (Exception e){
            log.error("",e);
            return ResultData.fail("导入文件异常");
        }
        File tmpFile = saveFile.getData();
        ResultData<ImportUserResultVo> importResult = null;
        try {
            importResult = addUserFromFile(username,tmpFile);
        }catch (Exception e){
            log.error("",e);
            importResult = ResultData.fail("导入用户失败，失败原因："+e.getMessage());
        }
        tmpFile.deleteOnExit();
        return importResult;
    }

    @Override
    public ResultData resetPwd(Long userId) {
        User user = userMapper.selectById(userId);
        if(user == null){
            return ResultData.fail("用户不存在");
        }
        user.setPassword(DigestUtils.md5Hex("123456"+passwordKey));
        user.setModifyTime(new Date());
        user.setModifyUser(user.getUsername());
        baseMapper.updateById(user);
        return ResultData.success();
    }

    @Override
    public List<User> listByTenantId() {
        Long tenantId = null;
        LoginUser loginUser = UserThreadLocal.get();
        if(loginUser != null){
            tenantId = loginUser.getTenantId();
        }
        ResultData<List<Long>> userIdsByTenantId = senseChatClient.getUserIdsByTenantId(tenantId);
        if(userIdsByTenantId.getCode() == ReturnCode.RC1.getCode()){
            List<Long> userIds = userIdsByTenantId.getData();
            QueryWrapper<User> qw = new QueryWrapper<>();
            qw.eq("status",CommonStatusEnum.TRUE.getId());
            if (userIds != null && userIds.size() > 0) {
                qw.in("id",userIds);
            }
            List<User> users = baseMapper.selectList(qw);
            return users;
        }else {
            return Collections.emptyList();
        }
    }

    private ResultData updatePassword(String username,String oldPassword,String newPassword,String rePassword){
        if(StringUtils.isBlank(newPassword)){
            return ResultData.fail("新密码不能为空");
        }
        if(StringUtils.isBlank(rePassword)){
            return ResultData.fail("重复密码不能为空");
        }
        if(!newPassword.equals(rePassword)){
            return ResultData.fail("密码输入不一致");
        }
        ResultData checkPassword = checkPassword(newPassword);
        if(checkPassword.getCode() == ReturnCode.RC0.getCode()){
            return checkPassword;
        }
        User user = baseMapper.selectByUsername(username);
        if(user == null){
            return ResultData.fail("用户不存在");
        }
        if(StringUtils.isNotBlank(oldPassword)){
            String oldPasswordMd5 = DigestUtils.md5Hex(oldPassword+passwordKey);
            if(!user.getPassword().equals(oldPasswordMd5)){
                return ResultData.fail("旧密码错误");
            }
        }
        user.setPassword(DigestUtils.md5Hex(newPassword+passwordKey));
        user.setModifyTime(new Date());
        user.setModifyUser(user.getUsername());
        baseMapper.updateById(user);
        return ResultData.success();
    }

    private ResultData checkPassword(String password){
        if(password.length()<8 || password.length()>20){
            return ResultData.fail("密码长度需要在8-20个字符之间");
        }
        if(!RegExUtils.checkPassword(password)){
            return ResultData.fail("密码需包含大写字母，小写字母，数字，特殊符号等任意三项");
        }
        return ResultData.success();
    }

    private ResultData<File> saveFile(String username, MultipartFile file, String uploadRootDir) throws IOException {
        String fileName = file.getOriginalFilename();
        String[] names = fileName.split("\\.");
        if (names.length == 1) {
            return ResultData.fail("文件格式不正确");
        }
        String fileFormat = names[names.length - 1];
        if (!"csv".equals(fileFormat.toLowerCase(Locale.SIMPLIFIED_CHINESE)) && !"xls".equals(fileFormat.toLowerCase(Locale.SIMPLIFIED_CHINESE))
                && !"xlsx".equals(fileFormat.toLowerCase(Locale.SIMPLIFIED_CHINESE))) {
            return ResultData.fail("文件格式不正确");
        }
        Long fileNamefix = System.currentTimeMillis();
        String rootDirPath = uploadRootDir + username + "/" + DateFormatUtils.format(new Date(),"yyyy-MM-dd") + "/import/";
        File rootDirFile = new File(rootDirPath);
        if (!rootDirFile.exists()) {
            rootDirFile.mkdirs();
        }
        StringBuilder sbPath = new StringBuilder(rootDirPath);
        for (int i = 0; i < names.length; i++) {
            sbPath.append(names[i]);
            if (i == names.length - 2) {
                sbPath.append("_");
                sbPath.append(fileNamefix);
            }
            if (i != names.length - 1) {
                sbPath.append(".");
            }

        }
        File saveFile = new File(sbPath.toString());
        file.transferTo(saveFile);
        return ResultData.success(saveFile);
    }
    private ResultData<ImportUserResultVo> addUserFromFile(String username, File file) throws IOException, ClassNotFoundException {
        List<ReadSheet> readSheetList = EasyExcel.read(file).build().excelExecutor().sheetList();
        List<ImportUserVo> varReadObj = new LinkedList<>();
        if(!CollectionUtils.isEmpty(readSheetList)){
            ReadSheet readSheet = readSheetList.get(0);
            Integer tempSheetNo = readSheet.getSheetNo();
            EasyExcel.read(file, ImportUserVo.class, new ExcelHeadOneListener())
                    .sheet(tempSheetNo)
                    .headRowNumber(1)
                    .doRead();
            // 反序列化对象[每读取一个sheet页，都需要清除上一次读取的数据，对象间有引用，避免汇总的数据被清除掉]
            ByteArrayOutputStream firstBos = new ByteArrayOutputStream();
            ObjectOutputStream firstOos = new ObjectOutputStream(firstBos);
            // 序列化-first-header-row对象
            firstOos.writeObject(ExcelHeadOneListener.getDataList());
            ByteArrayInputStream firstBis = new ByteArrayInputStream(firstBos.toByteArray());
            ObjectInputStream firstOis = new ObjectInputStream(firstBis);
            varReadObj = (List<ImportUserVo>) firstOis.readObject();
            ExcelHeadOneListener.clearDataList();
        }
        Set<String> deptNamesSet = varReadObj.stream().map(ImportUserVo::getDeptLink).collect(Collectors.toSet());
        Map<String,Long> deptNameParentIdMap = new HashMap<>();
        if(!CollectionUtils.isEmpty(deptNamesSet)){
            List<Dept> allDept = deptMapper.trueList();

            for(Dept dept:allDept){
                deptNameParentIdMap.put(dept.getName()+"_"+dept.getPid(),dept.getId());
            }
        }
        Map<String,Long> deptLinkIdMap = new HashMap<>();
        List<ImportUserFailedVo> addFailedVoList = new LinkedList<>();
        for (int i = 0; i < varReadObj.size(); i++) {
            ImportUserVo importVo = varReadObj.get(i);
            UserParam userParam = new UserParam();
            BeanUtils.copyProperties(importVo,userParam);
            userParam.setIsAdmin(CommonStatusEnum.FALSE.getId());
            Long deptId = getDeptIdInfo(importVo.getDeptLink(),deptLinkIdMap,deptNameParentIdMap);
            userParam.setDeptId(deptId);
            ResultData addUserResult = saveOrUpdateUser(userParam,username);
            if (addUserResult.getCode() == ReturnCode.RC0.getCode()){
                ImportUserFailedVo importUserFailedVo = new ImportUserFailedVo();
                importUserFailedVo.setUsername(importVo.getUsername());
                importUserFailedVo.setErrorMsg(addUserResult.getMessage());
                addFailedVoList.add(importUserFailedVo);
            }
        }
        int totalAddUserCount = varReadObj.size();
        int addFailedCount = addFailedVoList.size();
        int addSuccess = totalAddUserCount - addFailedCount;
        ImportUserResultVo importUserResultVo = new ImportUserResultVo();
        importUserResultVo.setTotalAddUserCount(totalAddUserCount);
        importUserResultVo.setAddFailedCount(addFailedCount);
        importUserResultVo.setAddSuccessCount(addSuccess);
        importUserResultVo.setImportUserFailedVos(addFailedVoList);
        if(totalAddUserCount!=0 && addSuccess==0){
            ResultData<ImportUserResultVo> failResult = ResultData.fail("导入失败");
            failResult.setData(importUserResultVo);
            return failResult;
        }
        return ResultData.success(importUserResultVo);
    }

    private Long getDeptIdInfo(String deptLink, Map<String, Long> deptLinkIdMap, Map<String, Long> deptNameParentIdMap) {
        if(StringUtils.isBlank(deptLink)){
            return null;
        }
        Long deptId = deptLinkIdMap.get(deptLink);
        if(deptId==null){
            String[] deptLinkNames = deptLink.split("_");
            String deptNamePidKey = deptLinkNames[0]+"_0";
            for(int i=0;i<deptLinkNames.length;i++){
                deptId = deptNameParentIdMap.get(deptNamePidKey);
                if(deptId == null){
                    break;
                }else if(i!=deptLinkNames.length-1){
                    deptNamePidKey = deptLinkNames[i+1]+"_"+deptId;
                }
            }
        }
        return deptId;
    }

    @Override
    public User getBySsoId(String ssoId) {
        User user = baseMapper.selectBySsoId(ssoId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public User createSsoUser(SsoUserParam param) {
        User existBySsoId = baseMapper.selectBySsoId(param.getSsoId());
        if (existBySsoId != null) {
            existBySsoId.setPassword(null);
            return existBySsoId;
        }

        User existByUsername = getByUsername(param.getUsername());
        if (existByUsername != null && existByUsername.getStatus() != CommonStatusEnum.DELETE.getId()) {
            existByUsername.setSsoId(param.getSsoId());
            baseMapper.updateById(existByUsername);
            existByUsername.setPassword(null);
            return existByUsername;
        }

        Date date = new Date();
        User user = new User();
        user.setUsername(param.getUsername());
        user.setName(param.getUsername());
        user.setSsoId(param.getSsoId());
        user.setSource(UserSourceEnum.OUT_SYNC.getId());
        user.setIsAdmin(CommonStatusEnum.FALSE.getId());
        user.setStatus(CommonStatusEnum.TRUE.getId());
        user.setCreatedTime(date);
        user.setCreatedUser("sso");
        user.setModifyTime(date);
        user.setModifyUser("sso");
        super.save(user);

        DataRole dataRole = DataRole.newPersonDataRole(user);
        dataRoleMapper.insert(dataRole);
        UsersDataRoles usersDataRoles = new UsersDataRoles();
        usersDataRoles.setUserId(user.getId());
        usersDataRoles.setDataRoleId(dataRole.getId());
        usersDataRolesMapper.insert(usersDataRoles);

        user.setPassword(null);
        return user;
    }

}