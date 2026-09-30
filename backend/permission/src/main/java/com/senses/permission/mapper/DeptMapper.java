package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.Dept;
import com.senses.permission.entity.User;
import com.senses.permission.model.vo.DeptVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* 组织部门表;(dept)表数据库访问层
* @author : shinan
* @date : 2022-12-8
*/
@Mapper
public interface DeptMapper extends BaseMapper<Dept>{

    @Select("select * from dept where status = 1")
    List<Dept> trueList();
    @Select("select * from dept where name = #{name} and status != 2")
    Dept selectByName(@Param("name") String name);
    @Select("select * from dept where code = #{code}")
    Dept selectByCode(@Param("code")String code);
    List<Dept> selectByGroupId(@Param("groupId") Long groupId);

    @Select("select * from dept where pid = #{deptId} and status !=2")
    List<DeptVO> selectByPid(@Param("deptId")Long deptId);


    List<Dept> selectAdminPermissionDeptByAppCode(@Param("appCode") String appCode);

    List<Long> selectRelatedDeptIds(@Param("userIds") List<Long> userIds);
}