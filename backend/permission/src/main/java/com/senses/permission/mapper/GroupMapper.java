package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.Group;
import com.senses.permission.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* 用户组表;(dept)表数据库访问层
* @author : shinan
* @date : 2022-12-8
*/
@Mapper
public interface GroupMapper extends BaseMapper<Group>{


    List<Group> listByPage(@Param("filterVal") String filterVal);

    Long count(@Param("filterVal") String filterVal);

    Group selectInfoByGroupId(@Param("groupId")Integer groupId);

    @Select("select * from `group` where status !=2 and group_name = #{groupName}")
    Group selectExist(@Param("groupName")String groupName);

    List<Group> listByUserIds(@Param("userIds")List<Long> userIds);

    @Select("select * from `group` g join groups_users gr on g.status = 1 and gr.user_id = #{userId} and g.id = gr.group_id ")
    List<Group> listByUserId(@Param("userId") Long userId);

    List<Group> listByUsernames(@Param("usernames")List<String> usernames);
    @Select("select g.*,u.id as user_id,u.username,u.name as cn_username from `group` g " +
            " join groups_users gr on g.status = 1 and g.id = gr.group_id " +
            " join user u on gr.user_id = u.id and u.status=1" +
            " order by g.id")
    List<Group> getGroupUsersTree();
}