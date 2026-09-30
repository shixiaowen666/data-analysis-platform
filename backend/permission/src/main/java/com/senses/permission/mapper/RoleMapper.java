package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.Permission;
import com.senses.permission.entity.User;
import com.senses.permission.model.vo.RoleInfoVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.Role;
import org.apache.ibatis.annotations.Select;

/**
 * 角色表;(role)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role>{
    
    List<Role> listByPage(@Param("id") Long id,@Param("filterVal") String filterVal);

    Long count(@Param("id") Long id,@Param("filterVal") String filterVal);

    @Select("select * from role where app_id = #{appId} and name =#{name} and status != 2")
    Role selectByAppIdAndName(@Param("appId") Long appId, @Param("name")String name);

    List<User> getUserListByPage(@Param("roleId") Long roleId, @Param("filterVal")String filterVal);

    Long countUserList(@Param("roleId") Long roleId, @Param("filterVal")String filterVal);

    List<Permission> getPermissionListByPage(@Param("roleId") Long roleId,@Param("filterVal")String filterVal);

    Long countPermissionList(@Param("roleId") Long roleId,@Param("filterVal")String filterVal);

    @Select({"<script>",
            "select * from role where status=1 and id in",
            "<foreach collection='roleIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<Role> selectByIdsAndTrue(@Param("roleIds") List<Long> roleIds);

    List<Role> treeList(@Param("appId")Long appId, @Param("filterVal")String filterVal,@Param("status")Integer status);

    List<Role> selectByDeptId(@Param("deptId") Long deptId);

    List<Role> selectByGroupId(@Param("groupId")Long groupId);
    @Select("SELECT " +
            "r.* " +
            "FROM " +
            "role r " +
            "where r.app_id = #{appId} and r.status!=2")
    List<Role> noDeleteListByAppId(@Param("appId")Long appId);

    List<Permission> getAllPermissionList(@Param("roleId") Long roleId,@Param("filterVal")String filterVal,@Param("selectApprove")Integer selectApprove);
    @Select({"<script>",
            "select r.*,a.name as app_name from role r ",
            "join application a on r.app_id = a.id ",
            "where r.id in",
            "<foreach collection='roleIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<Role> appRoleListByIds(@Param("roleIds") List<Long> roleIds);
    @Select(" SELECT" +
            "   a.`code` as app_code," +
            "   ro.role_id," +
            "   ro.role_name," +
            "   ro.role_cn_name," +
            "   ro.role_type" +
            " from" +
            "   (" +
            "      SELECT" +
            "          r.app_id," +
            "          r.id as role_id," +
            "          r.`name` as role_name," +
            "          r.cn_name as role_cn_name," +
            "          r.type as role_type" +
            "       FROM" +
            "          role r" +
            "          join users_roles ur on r.`status`=1 and ur.role_id = r.id" +
            "          join `user` u on ur.user_id = u.id and u.id=#{userId}" +
            "       union" +
            "       SELECT" +
            "          r.app_id," +
            "          r.id as role_id," +
            "          r.`name` as role_name," +
            "          r.cn_name as role_cn_name," +
            "          r.type as role_type" +
            "       FROM" +
            "          role r" +
            "          join depts_roles dr on r.`status`=1 and r.id = dr.role_id" +
            "          join dept d on dr.dept_id=d.id" +
            "          join `user` u on u.dept_id = d.id and u.id=#{userId}" +
            "       union" +
            "       SELECT" +
            "          r.app_id," +
            "          r.id as role_id," +
            "          r.`name` as role_name," +
            "          r.cn_name as role_cn_name," +
            "          r.type as role_type" +
            "       from" +
            "          role r " +
            "          join groups_roles gr on r.`status`=1 and r.id = gr.role_id" +
            "          join `group` g on gr.group_id=g.id" +
            "          join groups_users gu on g.id = gu.group_id" +
            "          join `user` u on gu.user_id = u.id and u.id=#{userId}" +
            "     )ro" +
            "     join application a on ro.app_id = a.id" +
            "        order by a.`code`")
    List<RoleInfoVO> getAllUserRoleInfos(@Param("userId") Long userId);
}