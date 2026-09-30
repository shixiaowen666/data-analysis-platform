package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DeptsRolesDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 角色与部门关联草稿表;(depts_roles_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface DeptsRolesDraftMapper extends BaseMapper<DeptsRolesDraft>{
    @Select("select * from depts_roles_draft where dept_id = #{deptId}")
    List<DeptsRolesDraft> selectByDeptId(@Param("deptId") Long deptId);
}