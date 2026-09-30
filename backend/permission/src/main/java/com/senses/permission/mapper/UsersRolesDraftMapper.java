package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.UsersRolesDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 用户和功能角色关系草稿表;(users_roles_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface UsersRolesDraftMapper extends BaseMapper<UsersRolesDraft>{
    @Select("select * from users_roles_draft where user_id = #{userId} and applicant_type=#{applicantType}")
    List<UsersRolesDraft> selectListByUserIdAndApplicantType(@Param("userId") Long userId,@Param("applicantType")Integer applicantType);
}