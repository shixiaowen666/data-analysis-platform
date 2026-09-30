package com.senses.permission.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.constant.ApproveFlowTagEnum;
import com.senses.permission.constant.CommonStatusEnum;
import com.senses.permission.entity.*;
import com.senses.permission.model.*;
import com.senses.permission.model.dataroleVo.DataPermissionVo;
import com.senses.permission.model.param.*;
import com.senses.permission.model.vo.AuthenticationVO;
import com.senses.permission.model.vo.ImportUserResultVo;
import com.senses.permission.model.vo.UserRolesIdsVO;
import com.senses.permission.service.DeptService;
import com.senses.permission.service.PermissionService;
import com.senses.permission.service.UserService;
import com.senses.permission.util.FileUtils;
import com.senses.permission.util.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 *  用户管理
 * </p>
 *
 * @author liaojinlei
 * @since 2022-12-07
 */

@Slf4j
@RestController
@RequestMapping("/upc/user")
@Tag(name = "用户管理 API")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private DeptService deptService;

    @Resource
    private PermissionService permissionService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;


    /**
     * all
     * @return
     */
    @Operation(summary = "所有用户列表")
    @GetMapping(value = "/all")
    public ResultData<List<User>> all(){
        return ResultData.success(userService.listLikeUsername(null));
    }

    @Operation(summary = "查询当前空间下用户列表")
    @GetMapping(value = "/allByTenantId")
    public ResultData<List<User>> allByTenantId(){
        return ResultData.success(userService.listByTenantId());
    }

    /**
     * list
     * @param username
     * @return
     */
    @Operation(summary = "模糊搜索用户列表")
    @GetMapping(value = "/list")
    public ResultData<List<User>> list(
            @Parameter(description = "用户名称") @RequestParam(required = false)String username){
        return ResultData.success(userService.listLikeUsername(username));
    }

    /**
     * 按部门id集合，用户组id集合，用户名或姓名查询用户列表
     * @param deptGroupNameUserParam
     * @return
     */
    @Operation(summary = "按部门id集合，用户组id集合，用户名或姓名查询用户列表")
    @PostMapping(value = "/listByDeptsOrGroupsOrName")
    public ResultData<List<User>> listByDeptsOrGroupsOrName(
            @Parameter(description = "按部门id集合，用户组id集合，用户名或姓名查询用户列表") @RequestBody(required = false)DeptGroupNameUserParam deptGroupNameUserParam){
        return ResultData.success(userService.listByDeptsOrGroupsOrName(deptGroupNameUserParam));
    }

    /**
     * 分页查询用户列表
     * @param pageParam
     * @return
     */
    @Operation(summary = "分页查询用户列表")
    @PostMapping(value = "/listByPage")
    public ResultData<Page<User>> listByPage(@Parameter(description = "搜索值：用户id，姓名，用户名") @RequestBody PageParam<UserPageParam> pageParam){

        return ResultData.success(userService.listByPage(pageParam));
    }

    /**
     * 添加或修改用户
     * @param userParam
     * @return
     */
    @Operation(summary = "添加或修改用户")
    @PostMapping(value = "/saveOrUpdate")
    public ResultData saveOrUpdate(@RequestHeader("username")String username,@RequestBody UserParam userParam){
        return userService.saveOrUpdateUser(userParam,username);

    }

    @PostMapping(value = "/uploadUsers")
    @Operation(summary = "批量上传用户")
    public @ResponseBody ResultData<ImportUserResultVo> uploadUsers(@RequestBody MultipartFile file){
        String username = UserThreadLocal.get().getUsername();
        return userService.upload(username,file);
    }

    @GetMapping(value = "/downloadTemplate")
    @Operation(summary = "下载导入用户模板")
    public void downloadTemplate(HttpServletResponse response){
        FileUtils.downloadTemplate(response,"importUser.xlsx");
    }

    /**
     * 查看详细信息
     * @return
     */
    @Operation(summary = "查看用户信息")
    @GetMapping(value = "/getInfo")
    public ResultData<User> getInfo(@Parameter(description = "用户id",example = "1")@RequestParam Long userId){
        return ResultData.success(userService.getInfo(userId));
    }

    /**
     * 批量获取用户信息
     * @return
     */
    @Operation(summary = "根据用户id批量获取用户信息")
    @PostMapping(value = "/getInfoByIds")
    public ResultData<List<User>> getInfoByIds(@Parameter(description = "用户id集合")@RequestBody List<Long> userIds){
        return ResultData.success(userService.getInfoByIds(userIds));
    }

    /**
     * 批量获取用户信息
     * @return
     */
    @Operation(summary = "根据用户名批量获取用户信息")
    @PostMapping(value = "/getInfoByUsernames")
    public ResultData<List<User>> getInfoByUsernames(@Parameter(description = "用户名称集合")@RequestBody List<String> userNames){
        return ResultData.success(userService.getInfoByUsernames(userNames));
    }

    /**
     * 发送重置密码链接
     * @return
     */
    @Operation(summary = "发送重置密码链接")
    @GetMapping(value = "/sendResetPasswordUrl")
    @Deprecated
    public ResultData sendResetPasswordUrl(@RequestHeader("username")String username,@Parameter(description = "用户id")@RequestParam Long userId){
        return null;
    }

    /**
     * 验证链接
     * @return
     */
    @Operation(summary = "验证链接")
    @GetMapping(value = "/checkResetUrl")
    @Deprecated
    public ResultData checkResetUrl(@Parameter(description = "重置密码token")@RequestParam String resetToken){
        return userService.checkResetUrl(resetToken);
    }

    /**
     * 重置密码
     * @return
     */
    @Operation(summary = "重置密码")
    @PostMapping(value = "/resetPassword")
    @Deprecated
    public ResultData resetPassword(@Parameter(description = "重置密码参数") @RequestBody UserResetPasswordParam userResetPasswordParam){

        return userService.resetPassword(userResetPasswordParam);
    }

    /**
     * 重置密码
     * @return
     */
    @Operation(summary = "重置密码")
    @GetMapping(value = "/resetPwd")
    public ResultData resetPwd(@Parameter(description = "用户id")@RequestParam Long userId){

        return userService.resetPwd(userId);
    }

    /**
     * 登录
     * @return
     */
    @Operation(summary = "登录")
    @PostMapping(value = "/login")
    public ResultData login(@Parameter(description = "登录") @RequestBody UserParam userParam, HttpServletResponse response){
        return doLogin(userParam, response, true);
    }

    /**
     * 明文密码登录
     * @return
     */
    @Operation(summary = "明文密码登录获取token")
    @PostMapping(value = "/getToken")
    public ResultData getToken(@Parameter(description = "明文密码登录获取token") @RequestBody UserParam userParam, HttpServletResponse response){
        return doLogin(userParam, response, false);
    }

    /**
     * 明文密码登录获取长期token（2099年过期），不加载权限
     * @return
     */
    @Operation(summary = "明文密码登录获取长期token（2099年过期）")
    @PostMapping(value = "/getPermanentToken")
    public ResultData getPermanentToken(@Parameter(description = "明文密码登录获取长期token") @RequestBody UserParam userParam, HttpServletResponse response){
        User user = userService.loginByPlainPasswordSimple(userParam);
        if(user == null){
            return ResultData.fail("用户名或密码错误");
        }
        if(user.getStatus().equals(CommonStatusEnum.FALSE.getId())){
            return ResultData.fail("账户已禁用");
        }
        Date expiration = Date.from(LocalDateTime.of(2099, 12, 31, 23, 59, 59)
                .atZone(ZoneId.systemDefault()).toInstant());
        String token = jwtTokenUtil.generateToken(userParam.getUsername(), user.getId(), expiration);
        log.info("getPermanentToken token========{}", token);
        response.setHeader("accessToken", token);
        return ResultData.success(new AuthenticationVO(token, user));
    }

    private ResultData doLogin(UserParam userParam, HttpServletResponse response, boolean encrypted){
        User user = encrypted ? userService.login(userParam) : userService.loginByPlainPassword(userParam);
        if(user != null){
            if(user.getStatus().equals(CommonStatusEnum.FALSE.getId())){
                return ResultData.fail("账户已禁用");
            }
            final String token = jwtTokenUtil.generateToken(userParam.getUsername(),user.getId());
            log.info("login token========{}", token);
            if (StringUtils.isNotBlank(userParam.getUsername())) {
                LoginUser loginUser = new LoginUser();
                user.setUsername(userParam.getUsername());
                UserThreadLocal.set(loginUser);
            }
            AuthenticationVO authenticationVO = new AuthenticationVO(token, user);
            response.setHeader("accessToken", authenticationVO.getToken());
            return ResultData.success(authenticationVO);
        }else{
            return ResultData.fail("用户名或密码错误");
        }
    }
    @Operation(summary = "根据token获取登录返回信息")
    @GetMapping(value = "/loginByToken")
    public ResultData loginByToken(@Parameter(description = "accessToken")@RequestParam("accessToken") String accessToken) {
        User user=null;
        try {
            user = userService.getInfoAndPermissionTreeByUsername(jwtTokenUtil.getUsernameFromToken(accessToken));
        } catch (Exception e) {
            return ResultData.fail("token 校验失败");
        }
        if(null==user){
            return ResultData.fail("用户不存在");
        }
        user.setPassword(null);
        return ResultData.success(new AuthenticationVO(accessToken, user));
    }
    @Operation(summary = "根据token获取user信息")
    @GetMapping(value = "/validateToken")
    public ResultData validateToken(@Parameter(description = "accessToken")@RequestParam("accessToken") String accessToken) {
        User user=null;
        try {
             user = userService.getInfoAndPermissionTreeByUsername(jwtTokenUtil.getUsernameFromToken(accessToken));
        } catch (Exception e) {
            return ResultData.fail("token 校验失败");
        }
        if(null==user){
            return ResultData.fail("用户不存在");
        }
        user.setPassword(null);
        return ResultData.success(user);
    }
    /**
     * 查看详细信息
     * @return
     */
    @Operation(summary = "通过用户名查看用户信息")
    @GetMapping(value = "/getInfoByUsername")
    public ResultData<User> getInfo(@Parameter(description = "用户id",example = "admin")@RequestParam String username){
        log.info("query : "+username);
        return ResultData.success(userService.getByUsername(username));
    }


    /**
     * 查看详细信息
     * @return
     */
    @Operation(summary = "获取用户信息，用于前端刷新重置")
    @GetMapping(value = "/info")
    public ResultData<User> info(){
        if(UserThreadLocal.get() != null){
            return ResultData.success(userService.getInfoAndPermissionTreeByUsername(UserThreadLocal.get().getUsername()));
        }else{
            return ResultData.fail("获取用户信息失败");
        }


    }

    /**
     * 修改状态
     * @return
     */
    @Operation(summary = "修改状态")
    @PostMapping(value = "/updateStatus")
    public ResultData updateStatus(@RequestHeader("username")String username,@Parameter(description = "修改状态入参")@RequestBody UpdateStatusParam updateStatusParam){
        return userService.updateStatus(updateStatusParam.getId(),updateStatusParam.getStatus(),username);
    }

    /**
     * 删除用户
     * @param userId
     * @return
     */
    @Operation(summary = "删除用户")
    @GetMapping(value = "/delete")
    public ResultData delete(@RequestHeader("username")String username,@Parameter(description = "用户id",example = "1") @RequestParam Long userId){
        return userService.deleteUser(userId,username);
    }


    /**
     * 查询用户角色id
     * @return
     */
    @Operation(summary = "获取用户角色")
    @GetMapping(value = "/getRoles")
    public ResultData<UserRolesIdsVO> getRoles(@Parameter(description = "用户id",example = "1") @RequestParam(value="userId") Long userId){
        User user = userService.getById(userId);
        return ResultData.success(userService.getRoles(user));
    }

    /**
     * 授权
     * @param userBindParam
     * @return
     */
    @Operation(summary = "给用户授权")
    @PostMapping(value = "/bind")
    public ResultData bind(@RequestHeader("username")String username,@Parameter(description = "用户授权参数",example = "1") @RequestBody UserBindParam userBindParam){
        return userService.bind(userBindParam.getUserId(),userBindParam.getRoleIds(),userBindParam.getDataRoleIds(),username);
    }

    /**
     * 查询用户功能角色
     * @param userId
     * @return
     */
    @Operation(summary = "查询用户功能角色")
    @GetMapping(value = "/roleTreeList")
    public ResultData<List<TreeData<Role>>> roleTreeList(@Parameter(description = "用户id",example = "1") @RequestParam(value="userId") Long userId){
        new Date().getTime();
        User user = userService.getById(userId);
        return ResultData.success(userService.roleTreeList(user));
    }

    /**
     * 查询用户数据角色
     * @param userId
     * @return
     */
    @Operation(summary = "查询用户数据角色")
    @GetMapping(value = "/dataRoleTreeList")
    public ResultData<List<TreeData<DataRole>>> dataRoleTreeList(@Parameter(description = "用户id",example = "1") @RequestParam(value="userId") Long userId){
        User user = userService.getById(userId);
        return ResultData.success(userService.dataRoleTreeList(user));
    }


    /**
     * 查询我的功能权限
     * @param username
     * @return
     */
    @Operation(summary = "查询我的功能权限")
    @GetMapping(value = "/myRoleTreeList")
    public ResultData<List<TreeData<Role>>> myRoleTreeList(@RequestHeader("username")String username){
        User user = userService.getByUsername(username);
        return ResultData.success(userService.roleTreeList(user));
    }

    /**
     * 获取我的个人数据权限
     * @param username
     * @return
     */
    @Operation(summary = "获取我的个人数据权限")
    @PostMapping(value = "/myListByPageDataRolePermission")
    public ResultData<List<DataRolesPermission>> myListByPageDataRolePermission(@RequestHeader("username")String username, @RequestBody UserPersonDataRoleParam userPersonDataRoleParam){
        User user = userService.getByUsername(username);
        return ResultData.success(userService.myListByPageDataRolePermission(user,userPersonDataRoleParam));
    }

    /**
     * 获取我的数据权限-部门数据权限
     * @param username
     * @return
     */
    @Operation(summary = "获取我的数据权限-部门数据权限")
    @GetMapping(value = "/myDeptDataRoleTreeList")
    public ResultData<List<TreeData<DataRole>>> myDeptDataRoleTreeList(@RequestHeader("username")String username){
        User user = userService.getByUsername(username);
        return ResultData.success(userService.getMyDeptDataRoleTreeList(user));
    }
    /**
     * 获取我的数据权限-用户组数据权限
     * @param username
     * @return
     */
    @Operation(summary = "获取我的数据权限-用户组数据权限")
    @GetMapping(value = "/myGroupDataRoleTreeList")
    public ResultData<List<TreeData<DataRole>>> myGroupDataRoleTreeList(@RequestHeader("username")String username){
        User user = userService.getByUsername(username);
        return ResultData.success(userService.getMyGroupDataRoleTreeList(user));
    }

    /**
     * 个人申请功能权限
     * @param username
     * @return
     */
    @Operation(summary = "个人申请功能角色")
    @PostMapping(value = "/applyRoles")
    public ResultData applyRoles(@RequestHeader("username")String username,@RequestBody @Parameter(description = "数据权限列表") List<Long> roleIds){
        User user = userService.getByUsername(username);
        return userService.applyRoles(user,roleIds);
    }

    /**
     * 个人申请数据功能权限
     * @param username
     * @return
     */
    @Operation(summary = "个人申请数据功能权限")
    @PostMapping(value = "/applyDataPermission")
    public ResultData applyDataPermission(@RequestHeader("username")String username,@RequestBody @Parameter(description = "数据权限列表（更新时删除的权限可从列表移除，不必回传）") Set<DataPermissionVo> dataPermissions){
        User user = userService.getByUsername(username);
        return userService.applyDataPermission(user,dataPermissions, ApproveFlowTagEnum.UPC_DATA_ROLE);
    }

    /**
     * 个人申请新增单个表数据权限
     * @param username
     * @return
     */
    @Operation(summary = "个人申请新增单个表数据权限。提供给数据资产-元数据管理，申请单个表权限。")
    @PostMapping(value = "/appendApplyDataPermission1")
    public ResultData appApplyDataPermission1(@RequestHeader("username")String username,@RequestBody @Parameter(description = "追加的数据权限列表") Set<DataPermissionVo> dataPermissions){
        User user = userService.getByUsername(username);
        return userService.applyDataPermission(user,dataPermissions,ApproveFlowTagEnum.UPC_APPEND_DATA_ROLE_FROM_META);
    }

    /**
     * 个人申请新增单个表数据权限
     * @param username
     * @return
     */
    @Operation(summary = "个人申请新增单个表数据权限。提供给即席查询，申请单个表权限。")
    @PostMapping(value = "/appendApplyDataPermission2")
    public ResultData appApplyDataPermission2(@RequestHeader("username")String username,@RequestBody @Parameter(description = "追加的数据权限列表") Set<DataPermissionVo> dataPermissions){
        User user = userService.getByUsername(username);
        return userService.applyDataPermission(user,dataPermissions,ApproveFlowTagEnum.UPC_APPEND_DATA_ROLE_FROM_IDE);
    }

    /**
     * 个人申请部门绑定数据角色
     * @param username
     * @return
     */
    @Operation(summary = "个人申请部门绑定数据角色")
    @PostMapping(value = "/applyDeptDataRole")
    public ResultData applyDeptDataRole(@RequestHeader("username") String username,@Parameter(description = "部门授权参数") @RequestBody DeptBindParam deptBindParam){
        User user = userService.getByUsername(username);
        return deptService.bindDataRole(user.getDeptId(),deptBindParam.getDataRoleIds(),username);
    }

    /**
     * 获取我的数据角色id
     * @param username
     * @return
     */
    @Operation(summary = "获取我的数据角色id")
    @GetMapping(value = "/getMyDataRoleIds")
    public ResultData<UserRolesIdsVO> getMyDataRoleIds(@RequestHeader("username")String username){
        User user = userService.getByUsername(username);
        return ResultData.success(userService.getMyDataRoleIds(user));
    }

    /**
     * 获取我的功能角色id
     * @param username
     * @return
     */
    @Operation(summary = "获取我的功能角色id")
    @GetMapping(value = "/getMyRoleIds")
    public ResultData<UserRolesIdsVO> getMyRoleIds(@RequestHeader("username")String username){
        User user = userService.getByUsername(username);
        return ResultData.success(userService.getMyRoleIds(user));
    }

    /**
     * 初始化用户个人数据角色
     * @return
     */
    @Operation(summary = "初始化用户个人数据角色")
    @PostMapping(value = "/initUserDataRole")
    public ResultData initUserDataRole(){
        return userService.initUserDataRole();
    }

    /**
     * 通过部门id获取用户名列表
     * @param
     * @return
     */
    @Operation(summary = "通过部门id获取用户名列表")
    @PostMapping(value = "/getUserList")
    public ResultData getUserList(@Parameter(description = "部门id") @RequestParam Long deptId){
        return ResultData.success(userService.getUserList(deptId));
    }

    /**
     * 通过部门id列表、状态、姓名、用户名筛选用户
     * @param
     * @return
     */
    @Operation(summary = "通过部门id列表、状态、姓名、用户名筛选用户")
    @PostMapping(value = "/listByDeptsOrStatusOrName")
    public ResultData listByDeptsOrStatusOrName(@RequestBody Map<String,Object> map){
        return ResultData.success(userService.listByDeptsOrStatusOrName(map));
    }

    /**
     * 修改密码
     * @param
     * @return
     */
    @Operation(summary = "登录前修改密码")
    @PostMapping(value = "/loginPreUpdatePassword")
    public ResultData updatePassword(@RequestBody LoginPreUpdatePasswordParam loginPreUpdatePasswordParam){
        return userService.loginPreUpdatePassword(loginPreUpdatePasswordParam);
    }

    /**
     * 修改密码
     * @param
     * @return
     */
    @Operation(summary = "登录后修改密码")
    @PostMapping(value = "/updatePassword")
    public ResultData updatePassword(@RequestBody UpdatePasswordParam updatePasswordParam){
        String userName = UserThreadLocal.get().getUsername();
        return userService.updatePassword(userName,updatePasswordParam);
    }

//    /**
//     * 获取应用的一级菜单
//     * @param username
//     * @return
//     */
//    此方法有BUG，以后如需开启，需要修改查询sql
//    @Operation(summary = "权限-获取应用下的一级菜单")
//    @GetMapping(value = "/getRootMenus")
//    public ResultData<List<Permission>> getRootMenus(@RequestHeader("username")String username,@RequestParam(value = "appId") @Parameter(description = "应用id") Long appId){
//        User user = userService.getByUsername(username);
//        return ResultData.success(permissionService.getRootMenus(user.getId(),appId));
//    }

}

