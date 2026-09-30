package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.model.param.DataRolePermissionDetailParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DataRolesPermissionDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 数据角色权限草稿表;(data_roles_permission_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface DataRolesPermissionDraftMapper extends BaseMapper<DataRolesPermissionDraft>{
    @Select("select * from data_roles_permission_draft d " +
            "where d.data_role_id = #{dataRoleId}")
    List<DataRolesPermissionDraft> selectListByDataRoleId(@Param("dataRoleId") Long dataRoleId);

    List<DataRolesPermissionDraft> selectListByParam(@Param("param") DataRolePermissionDetailParam param);

}