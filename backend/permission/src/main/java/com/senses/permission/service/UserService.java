package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.entity.*;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.dataroleVo.DataPermissionVo;
import com.senses.permission.model.param.*;
import com.senses.permission.model.vo.ImportUserResultVo;
import com.senses.permission.model.vo.UserRolesIdsVO;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户表;(user)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface UserService extends IService<User>{

    List<User> listLikeUsername(String username);

    Page<User> listByPage(PageParam<UserPageParam> pageParam);

    ResultData deleteUser(Long userId, String userName);

    ResultData saveOrUpdateUser(UserParam userParam,String loginUser);

    User getInfo(Long userId);

    ResultData sendResetPasswordUrl(Long userId,String userName);

    ResultData checkResetUrl(String resetToken);

    ResultData resetPassword(UserResetPasswordParam userResetPasswordParam);

    ResultData updateStatus(Long id, Integer status,String userName);

    UserRolesIdsVO getRoles(User user);

    ResultData bind(Long userId, List<Long> roleIds, List<Long> dataRoleIds,String username);

    List<TreeData<Role>> roleTreeList(User user);

    List<TreeData<DataRole>> dataRoleTreeList(User user);

    User login(UserParam userParam);

    User loginByPlainPassword(UserParam userParam);

    User loginByPlainPasswordSimple(UserParam userParam);

    User getByUsername(String username);

    User getInfoAndPermissionTreeByUsername(String username);

    List<User> listByDeptsOrGroupsOrName(DeptGroupNameUserParam deptGroupNameUserParam);

    List<User> getInfoByIds(List<Long> userIds);

    List<User> getInfoByUsernames(List<String> userNames);

    void confirmApprove(ApproveRecord approveRecord);

    List<TreeData<DataRole>> getMyDeptDataRoleTreeList(User user);

    List<TreeData<DataRole>> getMyGroupDataRoleTreeList(User user);

    List<DataRolesPermission> myListByPageDataRolePermission(User user, UserPersonDataRoleParam userPersonDataRoleParam);

    ResultData applyDataPermission(User user, Set<DataPermissionVo> dataPermissions, ApproveFlowTagEnum approveFlowTagEnum);

    UserRolesIdsVO getMyDataRoleIds(User user);

    ResultData initUserDataRole();

    ResultData applyRoles(User user, List<Long> roleIds);

    void confirmApproveRole(ApproveRecord approveRecord);

    List<String> getUserList(Long deptId);

    List<User> listByDeptsOrStatusOrName(Map<String,Object> map);

    UserRolesIdsVO getMyRoleIds(User user);

    UserRolesIdsVO getAllRoles(User user);

    List<Long> getAllUserDept(User user);

    ResultData updatePassword(String operator, UpdatePasswordParam updatePasswordParam);
    Map<Long,String> getUserIdName();

    ResultData loginPreUpdatePassword(LoginPreUpdatePasswordParam loginPreUpdatePasswordParam);

    ResultData<ImportUserResultVo> upload(String username, MultipartFile file);

    ResultData resetPwd(Long userId);

    List<User> listByTenantId();

    User getBySsoId(String ssoId);

    User createSsoUser(SsoUserParam param);
}