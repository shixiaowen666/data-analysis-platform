package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.UsersRoles;
import org.apache.ibatis.annotations.Select;

/**
 * 用户与角色表;(users_roles)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface UsersRolesMapper extends BaseMapper<UsersRoles>{
    @Select("select * from users_roles where role_id = #{roleId}")
    List<UsersRoles> selectListByRoleId(@Param("roleId") Long roleId);
    @Select("select * from users_roles where user_id = #{userId}")
    List<UsersRoles> selectListByUserId(@Param("userId")Long userId);
    @Select("select * from users_roles where role_id=#{role_id} and user_id = #{userId}")
    UsersRoles selectOnly(@Param("roleId") Long roleId, @Param("userId")Long userId);
}