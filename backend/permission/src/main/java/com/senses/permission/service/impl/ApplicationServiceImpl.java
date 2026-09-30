package com.senses.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.senses.permission.constant.CommonStatusEnum;
import com.senses.permission.entity.*;
import com.senses.permission.mapper.*;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.ApplicationPageParam;
import com.senses.permission.model.param.ApplicationParam;
import com.senses.permission.model.vo.UserVO;
import com.senses.permission.service.ApplicationService;
import com.senses.permission.service.PermissionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用表;(application)表服务实现类
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Service
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, Application> implements ApplicationService{

    @Resource
    private PermissionService permissionService;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private DeptMapper deptMapper;


    @Override
    public ResultData saveOrUpdateApplication(ApplicationParam applicationParam, String userName) {
        Application application;
        if(applicationParam.getId()!=null){
            application = getById(applicationParam.getId());
            if (StringUtils.isNotBlank(applicationParam.getApplicationKey())
                    && !application.getApplicationKey().equals(applicationParam.getApplicationKey())){
                return ResultData.fail("Application Key 不能修改！");
            }
            if (StringUtils.isNotBlank(applicationParam.getCode())
                    && !application.getCode().equals(applicationParam.getCode())){
                return ResultData.fail("应用编码不能修改！");
            }
            if (StringUtils.isBlank(applicationParam.getName())){
                return ResultData.fail("应用名称不能为空！");
            }
            application.setName(applicationParam.getName());
        }else{
            if (StringUtils.isBlank(applicationParam.getApplicationKey())){
                return ResultData.fail("Application Key 不能为空！");
            }
            if (StringUtils.isBlank(applicationParam.getCode())){
                return ResultData.fail("应用编码不能为空！");
            }
            if (StringUtils.isBlank(applicationParam.getName())){
                return ResultData.fail("应用名称不能为空！");
            }
            QueryWrapper<Application> qwAppKey = new QueryWrapper<>();
            qwAppKey.eq("application_key",applicationParam.getApplicationKey());
            qwAppKey.ne("status", CommonStatusEnum.DELETE.getId());
            Application existAppKey = this.getOne(qwAppKey);
            if(existAppKey!=null){
                return ResultData.fail("Application Key 已经注册，请使用其他值！");
            }

            QueryWrapper<Application> qwCode = new QueryWrapper<>();
            qwCode.eq("code",applicationParam.getCode());
            qwCode.ne("status", CommonStatusEnum.DELETE.getId());
            Application existCode = this.getOne(qwCode);
            if(existCode!=null){
                return ResultData.fail("应用编码已被使用，请使用其他值！");
            }

            application = new Application();
            application.setCreatedTime(new Date());
            application.setCreatedUser(userName);
            application.setStatus(CommonStatusEnum.TRUE.getId());
            BeanUtils.copyProperties(applicationParam,application);
        }
        application.setModifyUser(userName);
        application.setModifyTime(new Date());
        if(this.saveOrUpdate(application)){
            return ResultData.success();
        }else {
            return ResultData.fail("保存失败");
        }

    }

    @Override
    public ResultData logicDelete(Long appId, String userName) {
        List<Role> roles  = roleMapper.noDeleteListByAppId(appId);
        if(!CollectionUtils.isEmpty(roles)){
            return ResultData.fail("删除应用失败，应用下存在未删除角色！");
        }
        Date deleteDate = new Date();
        UpdateWrapper<Application> uw = new UpdateWrapper<>();
        uw.set("status",CommonStatusEnum.DELETE.getId());
        uw.eq("id",appId);
        uw.set("modify_time",deleteDate);
        uw.set("modify_user",userName);
        baseMapper.update(null,uw);
        return ResultData.success();
    }

    /**
     * 根据应用名称模糊查询集合
     * @param appName
     * @return
     */
    @Override
    public List<Application> listByAppName(String appName) {
        QueryWrapper<Application> qw = new QueryWrapper<>();
        if(StringUtils.isNotBlank(appName)){
            qw.like("name",appName);
        }
        qw.eq("status",CommonStatusEnum.TRUE.getId());
        qw.orderByDesc("created_time");
        return this.list(qw);
    }

    @Override
    public Page<Application> listByPage(PageParam<ApplicationPageParam> pageParam) {
        // 参数校验、解析
        Page page = pageParam.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        ApplicationPageParam param = pageParam.getQueryParam();
        List<Application> list = baseMapper.listByPage(param.getFilterVal());
        page.setRecords(list);
        Long total = baseMapper.count(param.getFilterVal());
        page.setTotal(total);
        return page;
    }

    @Override
    public ResultData updateStatus(Long id, Integer status,String userName) {
        UpdateWrapper<Application> uw = new UpdateWrapper<>();
        uw.eq("id",id);
        uw.set("status",status);
        baseMapper.update(null,uw);
        return ResultData.success();
    }

    @Override
    public List<TreeData<Permission>> treeList(Long appId) {
        QueryWrapper<Permission> qw = new QueryWrapper<>();
        qw.eq("app_id", appId);
        qw.eq("status",CommonStatusEnum.TRUE.getId());
        qw.orderByAsc("pid");
        List<Permission> permissionList = permissionService.list(qw);
        return installTreeList(permissionList);

    }

    @Override
    public Application getInfoByCode(String code) {
        QueryWrapper<Application> wrapper = new QueryWrapper<>();
        wrapper.eq("code",code);
        wrapper.ne("status",CommonStatusEnum.DELETE.getId());
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public List<TreeData<Permission>> installTreeList(List<Permission> permissionList) {
        List<TreeData<Permission>> treeDataList = new LinkedList<>();
        if(CollectionUtils.isEmpty(permissionList)){
            return treeDataList;
        }
        Map<Long,List<Permission>> rootTreeMap = permissionList.stream().collect(Collectors.groupingBy(Permission::getPid));
        List<Permission> rootPermissions = rootTreeMap.get(0L);
        for(Permission rootPermission : rootPermissions){
            String pKey=null;
            TreeData<Permission> treeData = newTreeData(rootPermission, pKey);
            insertTree(treeData, rootTreeMap);
            treeDataList.add(treeData);
        }
        return treeDataList;
    }

    private void insertTree(TreeData<Permission> treeData, Map<Long, List<Permission>> rootTreeMap) {
        List<Permission> permissionList = rootTreeMap.get(treeData.getId());
        List<TreeData<Permission>> children = new LinkedList<>();
        if(!CollectionUtils.isEmpty(permissionList)){
            for(Permission permission : permissionList){
                TreeData<Permission> sonTreeData=newTreeData(permission,treeData.getKey());
                insertTree(sonTreeData,rootTreeMap);
                children.add(sonTreeData);
            }
        }
        treeData.setChildren(children);
    }


    private TreeData<Permission> newTreeData(Permission permission,String pKey){
        TreeData<Permission> treeData = new TreeData<>();
        StringBuilder sbKey = new StringBuilder();
        if(StringUtils.isNotBlank(pKey)){
            sbKey.append(pKey);
            sbKey.append("-");
        }
        sbKey.append(permission.getId());
        treeData.setKey(sbKey.toString());
        treeData.setLabel(permission.getName());
        treeData.setId(permission.getId());
        treeData.setShowType("Permission");
        treeData.setData(permission);
        return treeData;
    }

    @Override
    public List<UserVO> getAppAdmin(String code){
        //查询有管理员权限的部门
        List<Dept> deptList = deptMapper.selectAdminPermissionDeptByAppCode(code);
        Set<Long> deptIds = new HashSet<>();
        //查询有管理员权限的部门的下级部门
        if(!CollectionUtils.isEmpty(deptList)){
            deptList.stream().forEach(e->{
                deptIds.add(e.getId());
            });
            List<Dept> allDept = deptMapper.trueList();
            boolean isNoEnd = true;
            while(isNoEnd){
                boolean findContain = false;
                for(Dept dept:allDept){
                    if(deptIds.contains(dept.getPid())){
                        deptIds.add(dept.getId());
                        findContain = true;
                    }
                }
                if(!findContain){
                    isNoEnd = false;
                }
            }
        }
        //查询用户
        return baseMapper.selectAppAdmin(code,deptIds);
    }
}