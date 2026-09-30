package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.Application;
import com.senses.permission.entity.Permission;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.ApplicationPageParam;
import com.senses.permission.model.param.ApplicationParam;
import com.senses.permission.model.vo.UserVO;

import java.util.List;

/**
 * 应用表;(application)表服务接口
 * @author : liaojinlei
 * @date : 2022-12-7
 */
public interface ApplicationService extends IService<Application>{

    ResultData saveOrUpdateApplication(ApplicationParam applicationParam, String userName);

    ResultData logicDelete(Long appId, String userName);

    /**
     * 根据应用名称模糊查询集合
     * @param appName
     * @return
     */
    List<Application> listByAppName(String appName);

    Page<Application> listByPage(PageParam<ApplicationPageParam> pageParam);

    ResultData updateStatus(Long id, Integer status,String userName);

    List<TreeData<Permission>> installTreeList(List<Permission> permissionList);

    List<TreeData<Permission>> treeList(Long appId);

    Application getInfoByCode(String code);

    List<UserVO> getAppAdmin(String code);
}