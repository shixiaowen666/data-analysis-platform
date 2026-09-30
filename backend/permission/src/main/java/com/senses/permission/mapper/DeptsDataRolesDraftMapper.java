package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DeptsDataRolesDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 部门和数据角色关系草稿表;(depts_data_roles_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface DeptsDataRolesDraftMapper extends BaseMapper<DeptsDataRolesDraft>{
    @Select("select * from depts_data_roles_draft where dept_id = #{deptId}")
    List<DeptsDataRolesDraft> selectByDeptId(@Param("deptId") Long deptId);

    @Select("select * from depts_data_roles_draft where dept_id = #{deptId} and applicant_type = #{applicantType}")
    List<DeptsDataRolesDraft> selectByDeptIdAndApplicantType(@Param("deptId") Long deptId,@Param("applicantType") Integer applicantType);
}