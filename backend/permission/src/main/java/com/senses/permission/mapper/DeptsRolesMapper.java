package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DeptsRoles;
import org.apache.ibatis.annotations.Select;

/**
 * 角色与部门关联表;(depts_roles)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface DeptsRolesMapper extends BaseMapper<DeptsRoles>{

    @Select({"<script>",
            "select * from depts_roles where dept_id in",
            "<foreach collection='deptIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<DeptsRoles> selectListByDeptIds(@Param("deptIds") List<Long> deptIds);
    @Select("select * from depts_roles where role_id = #{roleId}")
    List<DeptsRoles> selectListByRoleId(@Param("roleId")Long roleId);

}