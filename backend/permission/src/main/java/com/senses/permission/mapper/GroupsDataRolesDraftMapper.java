package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.GroupsDataRolesDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 数据角色与部门关联草稿表;(groups_data_roles_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface GroupsDataRolesDraftMapper extends BaseMapper<GroupsDataRolesDraft>{
    @Select("select * from groups_data_roles_draft where group_id = #{groupId}")
    List<GroupsDataRolesDraft> selectByGroupId(@Param("groupId") Long groupId);
}