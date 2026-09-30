package com.senses.permission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.Application;
import com.senses.permission.entity.Permission;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.ApplicationPageParam;
import com.senses.permission.model.param.ApplicationParam;
import com.senses.permission.model.param.UpdateStatusParam;
import com.senses.permission.service.ApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>
 *  应用管理
 * </p>
 *
 * @author liaojinlei
 * @since 2022-12-07
 */

@Slf4j
@RestController
@RequestMapping("/upc/application")
@Tag(name = "应用管理 API")
public class ApplicationController {

    @Resource
    private ApplicationService applicationService;


    /**
     * list
     * @param appName
     * @return
     */
    @Operation(summary = "应用列表")
    @GetMapping(value = "/list")
    public ResultData<List<Application>> list(
            @Parameter(description = "应用名称") @RequestParam(required = false)String appName){

        return ResultData.success(applicationService.listByAppName(appName));
    }


    /**
     * 添加或修改应用
     * @param application
     * @return
     */
    @Operation(summary = "添加或修改")
    @PostMapping(value = "/saveOrUpdate")
    public ResultData saveOrUpdate(HttpServletRequest request, @RequestBody ApplicationParam application){
        try {
            String userName = request.getHeader("username");
            return applicationService.saveOrUpdateApplication(application,userName);
        }catch (Exception e){
            log.error("",e);
            return ResultData.fail("保存错误："+e.getMessage());
        }

    }

    /**
     * 查看详细信息
     * @return
     */
    @Operation(summary = "查看应用信息")
    @GetMapping(value = "/getInfo")
    public ResultData<Application> getInfo(@Parameter(description = "应用id",example = "1212")@RequestParam Long appId){
        return ResultData.success(applicationService.getById(appId));
    }

    /**
     * 根据应用编码查询应用详细信息
     * @return
     */
    @Operation(summary = "根据应用编码查询应用详细信息")
    @GetMapping(value = "/getInfoByCode")
    public ResultData<Application> getInfoByCode(@Parameter(description = "应用编码",example = "1212")@RequestParam String code){
        return ResultData.success(applicationService.getInfoByCode(code));
    }

    /**
     * 删除应用
     * @param appId
     * @return
     */
    @Operation(summary = "删除应用")
    @GetMapping(value = "/delete")
    public ResultData delete(HttpServletRequest request,@Parameter(description = "应用id",example = "1212") @RequestParam Long appId){
        String userName = request.getHeader("username");
        return applicationService.logicDelete(appId,userName);
    }
    /**
     * 分页查询应用
     * @param pageParam
     * @return
     */
    @Operation(summary = "分页查询应用")
    @PostMapping(value = "/listByPage")
    public ResultData<Page<Application>> listByPage(@Parameter(description = "应用分页查询入参") @RequestBody PageParam<ApplicationPageParam> pageParam){
        return ResultData.success(applicationService.listByPage(pageParam));
    }

    /**
     * 修改状态
     * @return
     */
    @Operation(summary = "修改状态")
    @PostMapping(value = "/updateStatus")
    public ResultData updateStatus(HttpServletRequest request,@Parameter(description ="修改状态入参")@RequestBody UpdateStatusParam updateStatusParam){
        String userName = request.getHeader("username");
        return applicationService.updateStatus(updateStatusParam.getId(),updateStatusParam.getStatus(),userName);
    }

    /**
     * 应用菜单树
     * @return
     */
    @Operation(summary = "应用菜单树")
    @GetMapping(value = "/treeList")
    public ResultData<List<TreeData<Permission>>> treeList(
            @Parameter(description = "部门名称") @RequestParam(required = false) Long appId) {
        return ResultData.success(applicationService.treeList(appId));
    }

    /**
     * 获取应用管理员
     * @return
     */
    @Operation(summary = "获取应用管理员")
    @GetMapping(value = "/getAppAdmin")
    public ResultData getAppAdmin(@Parameter(description = "应用码") @RequestParam("code") String code) {
        return ResultData.success(applicationService.getAppAdmin(code));
    }

}

