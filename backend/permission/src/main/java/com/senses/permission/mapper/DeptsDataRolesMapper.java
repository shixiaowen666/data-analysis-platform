package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DeptsDataRoles;
import org.apache.ibatis.annotations.Select;

/**
 * 数据角色与部门关联表;(depts_data_roles)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface DeptsDataRolesMapper extends BaseMapper<DeptsDataRoles>{

    @Select({"<script>",
            "select ddr.* from depts_data_roles ddr join data_role dr on dr.data_role_type =0 and ddr.data_role_id = dr.id where ddr.dept_id in",
            "<foreach collection='deptIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<DeptsDataRoles> selectListBydeptIds(@Param("deptIds") List<Long> deptIds);

}