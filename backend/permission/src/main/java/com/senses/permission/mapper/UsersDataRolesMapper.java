package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.User;
import com.senses.permission.entity.UsersRoles;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.UsersDataRoles;
import org.apache.ibatis.annotations.Select;

/**
 * 用户与数据角色表;(users_data_roles)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface UsersDataRolesMapper extends BaseMapper<UsersDataRoles>{
    @Select("select udr.* from users_data_roles udr join data_role dr on dr.data_role_type = 0 and udr.data_role_id = dr.id where udr.user_id = #{userId}")
    List<UsersDataRoles> selectListByUserId(@Param("userId") Long userId);

    @Select("select * from users_data_roles where data_role_id = #{dataRoleId} and user_id = #{userId}")
    UsersDataRoles selectOnly(@Param("dataRoleId") Long dataRoleId, @Param("userId")Long userId);
    @Select("select * from users_data_roles udr join data_role r on r.data_role_type = 1 and udr.data_role_id = r.id where udr.user_id = #{userId}")
    UsersDataRoles selectPersonDataRole(@Param("userId") Long userId);
    @Select("select udr.* from users_data_roles udr where udr.user_id = #{userId}")
    List<UsersDataRoles> selectAllListByUserId(@Param("userId") Long userId);
}