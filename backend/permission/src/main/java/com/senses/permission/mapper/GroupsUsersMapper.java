package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.GroupsUsers;
import org.apache.ibatis.annotations.Select;

/**
 * 用户组与用户关系表;(groups_users)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface GroupsUsersMapper extends BaseMapper<GroupsUsers>{

    @Select("select u.* from groups_users gu " +
            " join `group` g on gu.group_id = g.id and g.id=#{groupId}" +
            " join `user` u on gu.user_id = u.id")
    List<User> truelistByGroupId(@Param("groupId")Long groupId);
}