package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.UsersRoles;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.GroupsRoles;
import org.apache.ibatis.annotations.Select;

/**
 * 角色与部门关联表;(groups_roles)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface GroupsRolesMapper extends BaseMapper<GroupsRoles>{

    @Select("select * from groups_roles where group_id = #{groupId}")
    List<GroupsRoles> selectListByGroupId(@Param("groupId")Long groupId);
    @Select("select " +
            "gr.* " +
            "from " +
            "groups_roles gr " +
            "join groups_users gu on gu.user_id=#{userId} and gr.group_id = gu.group_id ")
    List<GroupsRoles> selectTrueListByUserId(@Param("userId")Long userId);
    @Select("select * from groups_roles where role_id = #{roleId}")
    List<GroupsRoles> selectListByRoleId(@Param("roleId") Long roleId);
}