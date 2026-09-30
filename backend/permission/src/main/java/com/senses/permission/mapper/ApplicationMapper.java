package com.senses.permission.mapper;

import java.util.List;
import java.util.Set;

import com.senses.permission.model.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.Application;
import org.apache.ibatis.annotations.Select;

/**
 * 应用表;(application)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface ApplicationMapper extends BaseMapper<Application>{

    List<Application> listByPage(@Param("filterVal") String filterVal);

    Long count(@Param("filterVal")String filterVal);

    @Select("select * from application where status =1")
    List<Application> trueList();
    List<Application> selectByDeptId(@Param("deptId")Long deptId);
    List<Application> selectByGroupId(@Param("groupId")Long groupId);
    List<Application> selectByUserId(@Param("userId") Long userId,@Param("deptIds")List<Long> deptIds);
    @Select("select * from application where code = #{appCode}")
    Application selectByCode(@Param("appCode") String appCode);

    List<UserVO> selectAppAdmin(@Param("appCode") String appCode,@Param("deptIds") Set<Long> deptIds);
}