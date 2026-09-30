package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.model.param.DataRolePermissionDetailParam;
import com.senses.permission.model.param.UserPersonDataRoleParam;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DataRolesPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据角色与权限关系表;(data_roles_permission)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface DataRolesPermissionMapper extends BaseMapper<DataRolesPermission>{
    @Select("select * from data_roles_permission where data_role_id=#{dataRoleId} and (database_name like '%#{keyWords}%' or table_name like '%#{keyWords}%' )")
     List<DataRolesPermission> listByPage(@Param("dataRoleId") Long dataRoleId, @Param("keyWords") String keyWords);

    @Select("select count(1) from data_roles_permission where data_role_id=#{dataRoleId} and (database_name like '%#{keyWords}%' or table_name like '%#{keyWords}%' )")
     Long count(@Param("dataRoleId") Long dataRoleId, @Param("keyWords") String keyWords);

    List<DataRolesPermission> selectListByParam(@Param("param") DataRolePermissionDetailParam dataRolePermissionDetailParam);

    List<DataRolesPermission> getUserDatarolePermission(@Param("userId") Long userId, @Param("param") UserPersonDataRoleParam userPersonDataRoleParam,@Param("deptIds") List<Long> deptIds);
}