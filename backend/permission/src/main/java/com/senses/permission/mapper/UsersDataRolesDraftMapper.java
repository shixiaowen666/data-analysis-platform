package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.UsersDataRolesDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 用户和数据角色的关系草稿表;(users_data_roles_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface UsersDataRolesDraftMapper extends BaseMapper<UsersDataRolesDraft>{
    @Select("select * from users_data_roles_draft where user_id = #{userId}")
    List<UsersDataRolesDraft> selectListByUserId(@Param("userId") Long userId);
}