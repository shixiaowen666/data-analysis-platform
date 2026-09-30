package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.*;
import com.senses.permission.model.param.DeptGroupNameUserParam;
import com.senses.permission.model.param.UserPersonDataRoleParam;
import com.senses.permission.model.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 用户表;(user)表数据库访问层
 *
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {


    List<User> listByPage(@Param("deptIds") Set<Integer> deptIds, @Param("status") Integer status, @Param("filterVal") String filterVal, @Param("orId") Long orId);


    Long count(@Param("deptIds") Set<Integer> deptIds, @Param("status") Integer status, @Param("filterVal") String filterVal, @Param("orId") Long orId);

    @Select("select u.*,d.name as dept_name from user u left join dept d on u.dept_id = d.id where username = #{username}")
    User selectByUsername(@Param("username") String username);

    @Select("select * from user where status !=2 and email = #{email}")
    User selectExistEmail(@Param("email") String email);

    User selectInfoByUserId(@Param("userId") Long userId);

    List<Role> roleTreeList(@Param("userId") Long userId,@Param("deptIds")List<Long> deptIds,@Param("approveStatus")Integer approveStatus);

    List<DataRole> dataRoleTreeList(@Param("userId") Long userId,@Param("deptIds")List<Long> deptIds);

    List<Application> getUserApplication(@Param("userId") Long userId,@Param("deptIds")List<Long> deptIds,@Param("approveStatus")Integer approveStatus);

    @Select({"<script>",
            "select * from user where status=2 and id in",
            "<foreach collection='userIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<User> deleteUserListByIds(@Param("userIds")List<Long> userIds);

    @Select("select * from user where status = 1")
    List<User> trueAllUserList();

    List<User> listByDeptsOrGroupsOrName(@Param("deptGroupNameUserParam")DeptGroupNameUserParam deptGroupNameUserParam);

    List<User> getInfoByIds(@Param("userIds")List<Long> userIds);

    List<User> getInfoByUsernames(@Param("userNames")List<String> userNames);
    @Select("select * from user where status != 2 and dept_id=#{deptId}")
    List<User> nodeleteListByDeptId(@Param("deptId") Long deptId);

    @Select("select u.* from user u join groups_users gu on u.id = gu.user_id where gu.group_id = #{groupId}")
    List<User> listByGroupId(@Param("groupId") Long groupId);

    List<DataRole> getMyDeptDataRoleTreeList(@Param("userId") Long userId, @Param("deptIds") List<Long> deptIds,@Param("approveStatus")Integer approveStatus);

    List<DataRole> getMyGroupDataRoleTreeList(@Param("userId") Long userId);

    List<DataRolesPermission> getMyDataRoleTreeList(@Param("userId") Long userId, @Param("param") UserPersonDataRoleParam userPersonDataRoleParam);

    @Select("select * from user where dept_id = #{deptId} and status !=2")
    List<UserVO> selectByDeptId(@Param("deptId") Long deptId);

    @Select("select * from user where sso_id = #{ssoId} and status != 2")
    User selectBySsoId(@Param("ssoId") String ssoId);

}