package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.Permission;
import org.apache.ibatis.annotations.Select;

/**
 * 权限表;(permission)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission>{
    @Select({"<script>",
            "select * from permission where app_id = #{appId} and id in",
            "<foreach collection='permissionIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            " and status != 2",
            "</script>"
    })
    List<Permission> selectBatchByAppIdAndIds(@Param("appId") Long appId,@Param("permissionIds")List<Long> permissionIds);

    List<Permission> listByPage(@Param("appId")Long appId,@Param("status") Integer status,@Param("permissionType")Integer permissionType,@Param("filterVal")String filterVal);

    Long count(@Param("appId")Long appId,@Param("status") Integer status,@Param("permissionType")Integer permissionType,@Param("filterVal")String filterVal);

    @Select("select * from permission where app_id = #{appId} and type=#{type} and pid=#{pid} and name=#{name} and status!=2")
    Permission selectExist(@Param("appId")Long appId, @Param("type")Integer type,@Param("pid")Long pid, @Param("name")String name);
    @Select("select p.*,a.name as app_name from permission p join application a on p.app_id = a.id where p.id = #{id}")
    Permission getInfoById(@Param("id")Long id);

    List<Permission> listSelectedPageByRoleId(@Param("roleId")Long roleId);

    Long countSelectedPageByRoleId(@Param("roleId")Long roleId);
    @Select({"<script>",
            "select * from permission where pid in",
            "<foreach collection='pids' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            " and status != 2",
            "</script>"
    })
    List<Permission> listByPids(@Param("pids")List<Long> pids);

    List<Permission> trueListByPid(@Param("pid")Long pid,@Param("roleId")Long roleId);

    List<Permission> trueList(@Param("appId")Long appId,@Param("permissionType")Integer permissionType,@Param("filterVal")String filterVal);

    List<Permission> getRootMenus(@Param("userId")Long userId,@Param("userId")Long appId);


    List<Permission> selectByUserIdAndPidsAndType(@Param("appIds")List<Long> appIds, @Param("userId")Long userId, @Param("pids")List<Long> pids, @Param("deptIds")List<Long> deptIds, @Param("permissionType")int permissionType,@Param("buttonType")Integer buttonType);

    @Select({"<script>",
            "select p.*,a.name as app_name from permission p ",
             " join application a on p.app_id = a.id ",
            "where p.id in",
            "<foreach collection='ids' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<Permission> appPermissionListByIds(@Param("ids")List<Long> ids);

    List<Permission> getViewPermissionList(@Param("userId")Long userId, @Param("deptIds")List<Long> deptIds);
    @Select("select * from permission where app_id = #{appId} and type=#{type} and pid=#{pid} and sign=#{sign} and status!=2")
    Permission selectExsitSign(@Param("appId")Long appId, @Param("type")Integer type,@Param("pid")Long pid, @Param("sign")String sign);
}