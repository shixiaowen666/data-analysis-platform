package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.Group;
import com.senses.permission.entity.GroupsRoles;
import com.senses.permission.entity.User;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.GroupsDataRoles;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据角色与部门关联表;(groups_data_roles)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface GroupsDataRolesMapper extends BaseMapper<GroupsDataRoles>{

    @Select("select * from groups_data_roles where group_id = #{groupId}")
    List<GroupsDataRoles> listByGroupId(@Param("groupId") Long groupId);

    @Select("select " +
            "gr.* " +
            "from " +
            "groups_data_roles gr " +
            "join data_role dr on dr.data_role_type=0 and gr.data_role_id = dr.id "+
            "join groups_users gu on gu.user_id=#{userId} and gr.group_id = gu.group_id ")
    List<GroupsDataRoles> selectTrueListByUserId(@Param("userId")Long userId);

}