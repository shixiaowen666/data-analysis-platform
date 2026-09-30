package com.senses.permission.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.senses.permission.constant.CommonStatusEnum;
import com.senses.permission.entity.ResourceManagement;
import com.senses.permission.mapper.ResourceManagementMapper;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.dataroleVo.ResourceManagementVo;
import com.senses.permission.model.param.ResourceManagementListQuery;
import com.senses.permission.model.param.ResourceManagementQuery;
import com.senses.permission.model.param.ResourcePermissionRequestDTO;
import com.senses.permission.service.ResourceManagementService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 资源管理表 服务实现类
 * </p>
 *
 * @author cxw
 * @since 2024-09-04
 */
@Service
public class ResourceManagementServiceImpl extends ServiceImpl<ResourceManagementMapper, ResourceManagement> implements ResourceManagementService {

    @Autowired
    private ResourceManagementMapper resourceManagementMapper;

    @Override
    public ResultData listAll(ResourceManagementListQuery query) {
        List<ResourceManagement> list = resourceManagementMapper.listAll(query);
        List<ResourceManagementVo> collect = list.stream()
                .map(resourceManagement -> {
                    Set<Integer> authLevel = new HashSet<>(Collections.singleton(0));
                    if (StringUtils.isNotEmpty(resourceManagement.getAuthLevel())) {
                        authLevel = Arrays.stream(resourceManagement.getAuthLevel().split(","))
                                .map(Integer::parseInt)
                                .collect(Collectors.toSet());
                    }
                    ResourceManagementVo vo = new ResourceManagementVo();
                    BeanUtils.copyProperties(resourceManagement, vo);
                    vo.setAuthLevel(authLevel);
                    return vo;
                })
                .filter(f -> CollectionUtils.isEmpty(query.getAuthLevelList()) ||
                        query.getAuthLevelList().contains(f.getAuthLevel().stream().max(Integer::compareTo).orElse(0))
                )
                .collect(Collectors.toList());
        return ResultData.success(collect);
    }

    @Override
    public ResultData listGroupByPage(PageParam<ResourceManagementQuery> query) {
        // 参数校验、解析
        Page page = query.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        ResourceManagementQuery queryParam = query.getQueryParam();
        List<ResourceManagement> list = resourceManagementMapper.listGroupByPage(queryParam);
        List<Integer> authLevelList = queryParam.getAuthLevelList();
        if (CollectionUtils.isNotEmpty(authLevelList)) {
            list = list.stream().filter(management -> {
                for (Integer level : authLevelList) {
                    if (level == 3 && management.getAuthLevel().contains("3")) {
                        management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                        return true;
                    } else if (level == 2 && (management.getAuthLevel().contains("3") || management.getAuthLevel().contains("2"))) {
                        management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                        return true;
                    } else if (level == 1 && !StringUtils.isEmpty(management.getAuthLevel())) {
                        management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                        return true;
                    } else {
                        return false;
                    }
                }
                return false;
            }).collect(Collectors.toList());
        } else {
            list.stream().forEach(management -> {
                if (!StringUtils.isEmpty(management.getAuthLevel())) {
                    management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                }
            });
        }
        page.setRecords(list);
        page.setTotal(Long.valueOf(list.size()));
        return ResultData.success(page);
    }

    @Override
    public ResultData listByPage(PageParam<ResourceManagementQuery> query) {
        // 参数校验、解析
        Page page = query.getPage();
        // 按条件分页查询
        PageHelper.startPage(Long.valueOf(page.getCurrent()).intValue(), Long.valueOf(page.getSize()).intValue());
        ResourceManagementQuery queryParam = query.getQueryParam();
        List<ResourceManagement> list = resourceManagementMapper.listByPage(queryParam);
        page.setRecords(list);
        List<Integer> authLevelList = queryParam.getAuthLevelList();
        if (CollectionUtils.isNotEmpty(authLevelList)) {
            list = list.stream().filter(management -> {
                for (Integer level : authLevelList) {
                    if (level == 3 && management.getAuthLevel().contains("3")) {
                        management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                        return true;
                    } else if (level == 2 && (management.getAuthLevel().contains("3") || management.getAuthLevel().contains("2"))) {
                        management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                        return true;
                    } else if (level == 1 && !StringUtils.isEmpty(management.getAuthLevel())) {
                        management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                        return true;
                    } else {
                        return false;
                    }
                }
                return false;
            }).collect(Collectors.toList());
        } else {
            list.stream().forEach(management -> {
                if (!StringUtils.isEmpty(management.getAuthLevel())) {
                    management.setAuthLevelVal(Arrays.asList(management.getAuthLevel().split(",")).stream().map(Integer::parseInt).max(Integer::compareTo).orElse(0));
                }
            });
        }
        page.setRecords(list);
        page.setTotal(Long.valueOf(list.size()));
        return ResultData.success(page);
    }

    @Override
    public ResultData deleteBatch(Integer resourceType, Long objectId, String applicationCode, Long userId) {
        UpdateWrapper<ResourceManagement> uw = new UpdateWrapper<>();
        uw.eq("resource_type", resourceType)
                .eq("object_id", objectId)
                .eq("application_code", applicationCode)
                .set("modify_user_id", userId)
                .set("online_status", 0);
        this.update(uw);
        return ResultData.success();
    }

    @Override
    public List<Map<String,Object>> getResourcePermissionByUserId(String applicationCode, Integer resourceType, Long userId) {
        return getResourcePermissions(applicationCode, resourceType, userId, null);
    }

    @Override
    public List<Map<String,Object>> getResourcePermissionByParam(ResourcePermissionRequestDTO resourceDTO) {
        return getResourcePermissions(resourceDTO.getApplicationCode(), resourceDTO.getResourceType(),
                resourceDTO.getUserId(), resourceDTO.getObjectIds());
    }

    private List<Map<String,Object>> getResourcePermissions(String applicationCode, Integer resourceType, Long userId, List<Long> objectIds) {
        List<ResourceManagement> list = resourceManagementMapper.getResourcePermissionByUserId(applicationCode,resourceType,userId,objectIds);
        return list.stream()
                .map(resourceManagement -> {
                    Integer maxAuthLevel = Optional.ofNullable(resourceManagement.getAuthLevel())
                            .map(authLevel -> Arrays.stream(authLevel.split(","))
                                    .map(Integer::valueOf)
                                    .max(Integer::compareTo)
                                    .orElse(0))
                            .orElse(0);
                    Map<String,Object> map = new HashMap<>();
                    map.put("id",resourceManagement.getObjectId());
                    map.put("applicationCode",resourceManagement.getApplicationCode());
                    map.put("resourceType",resourceManagement.getResourceType());
                    map.put("maxAuthLevel",maxAuthLevel);
                    map.put("authDownload",resourceManagement.getAuthDownload());
                    return map;
                })
                .filter(resourceManagement -> (Integer)resourceManagement.get("maxAuthLevel") > 0)
                .collect(Collectors.toMap(
                        map -> (Long) map.get("id"),
                        Function.identity(),
                        (map1, map2) -> {
                            Integer maxAuthLevel1 = (Integer) map1.get("maxAuthLevel");
                            Integer maxAuthLevel2 = (Integer) map2.get("maxAuthLevel");
                            map1.put("maxAuthLevel", Math.max(maxAuthLevel1, maxAuthLevel2));
                            return map1;
                        }
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    // @Override
    // public ResultData edit(Long userId, Long id, Integer authLevel, Integer authDownload) {
    //     ResourceManagement resourceManagement = resourceManagementMapper.selectById(id);
    //     if(null==resourceManagement|| resourceManagement.getOnlineStatus()==CommonStatusEnum.DELETE.getId()){
    //         return ResultData.fail("资源不存在");
    //     }
    //     resourceManagement.setModifyTime(LocalDateTime.now());
    //     resourceManagement.setModifyUserId(userId);
    //     resourceManagement.setAuthLevel(authLevel);
    //     resourceManagement.setAuthDownload(authDownload);
    //     int i = resourceManagementMapper.updateById(resourceManagement);
    //     if (i == 0) {
    //         return ResultData.fail("更新失败");
    //     }
    //     return ResultData.success("更新成功");
    // }

    @Override
    public ResultData delete(Long userId, Long id) {
        ResourceManagement resourceManagement = resourceManagementMapper.selectById(id);
        if (null == resourceManagement) {
            return ResultData.fail("资源不存在");
        }
        resourceManagement.setModifyTime(LocalDateTime.now());
        resourceManagement.setModifyUserId(userId);
        resourceManagement.setOnlineStatus(CommonStatusEnum.DELETE.getId());
        int i = resourceManagementMapper.updateById(resourceManagement);
        if (i == 0) {
            return ResultData.fail("删除失败");
        }
        return ResultData.success("删除成功");
    }

    @Override
    public List<ResourceManagement> getPrivilegesByUserId(Long userId) {
        QueryWrapper<ResourceManagement> wrapper = new QueryWrapper<>();
        wrapper.eq(ResourceManagement.AUTHORIZE_USER_ID, userId);
        wrapper.eq(ResourceManagement.ONLINE_STATUS, CommonStatusEnum.TRUE.getId());
        return resourceManagementMapper.selectList(wrapper);
    }

    @Override
    public ResultData saveOrUpdateBatch(List<ResourceManagementVo> resourceManagements) {
        List<ResourceManagement> collect = resourceManagements.stream().map(vo -> {
            ResourceManagement resourceManagement = new ResourceManagement();
            BeanUtils.copyProperties(vo, resourceManagement);
            String authLevel = JSON.toJSONString(vo.getAuthLevel()).replace("[", "").replace("]", "");
            resourceManagement.setAuthLevel(StringUtils.isEmpty(authLevel) ? "0" : authLevel);
            if (resourceManagement.getId() != null && resourceManagement.getId() != 0) {
                resourceManagement.setModifyUserId(resourceManagement.getAuthorizeUserId());
            }
            return resourceManagement;
        }).collect(Collectors.toList());
        if (saveOrUpdateBatch(collect)) {
            return ResultData.success();
        }
        return ResultData.fail("新增失败");
    }


}
