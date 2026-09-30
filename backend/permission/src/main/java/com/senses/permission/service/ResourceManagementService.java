package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.ResourceManagement;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.dataroleVo.ResourceManagementVo;
import com.senses.permission.model.param.ResourceManagementListQuery;
import com.senses.permission.model.param.ResourceManagementQuery;
import com.senses.permission.model.param.ResourcePermissionRequestDTO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 资源管理表 服务类
 * </p>
 *
 * @author cxw
 * @since 2024-09-04
 */
public interface ResourceManagementService extends IService<ResourceManagement> {

    ResultData listByPage(PageParam<ResourceManagementQuery> resourceManagementQuery);

    ResultData deleteBatch(Integer resourceType,Long objectId,String applicationCode,Long userId);

    // ResultData edit(Long userId, Long id, Integer authLevel, Integer authDownload);

    ResultData delete(Long userId, Long id);

    List<ResourceManagement> getPrivilegesByUserId(Long userId);

    ResultData saveOrUpdateBatch(List<ResourceManagementVo> resourceManagements);

    ResultData listAll(ResourceManagementListQuery query);

    ResultData listGroupByPage(PageParam<ResourceManagementQuery> query);


    List<Map<String,Object>> getResourcePermissionByUserId(String applicationCode, Integer resourceType, Long userId);

    List<Map<String,Object>> getResourcePermissionByParam(ResourcePermissionRequestDTO resourcePermissionRequestDTO);
}
