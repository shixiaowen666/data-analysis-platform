package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.ResourceManagement;
import com.senses.permission.model.dataroleVo.ResourceManagementVo;
import com.senses.permission.model.param.ResourceManagementListQuery;
import com.senses.permission.model.param.ResourceManagementQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 资源管理表 Mapper 接口
 * </p>
 *
 * @author cxw
 * @since 2024-09-04
 */
@Mapper
public interface ResourceManagementMapper extends BaseMapper<ResourceManagement> {

    List<ResourceManagement> listByPage(@Param("param") ResourceManagementQuery query);


    List<ResourceManagement> listAll(@Param("param")ResourceManagementListQuery query);

    List<ResourceManagement> listGroupByPage(@Param("param")ResourceManagementQuery queryParam);


    List<ResourceManagement> getResourcePermissionByUserId(@Param("applicationCode") String applicationCode,
                                                           @Param("resourceType") Integer resourceType,
                                                           @Param("userId") Long userId,
                                                           @Param("objectIds") List<Long> objectIds);
}
