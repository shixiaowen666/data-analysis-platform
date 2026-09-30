package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.RolesPermission;
import org.apache.ibatis.annotations.Select;

/**
 * 角色与权限管理关联表;(roles_permission)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface RolesPermissionMapper extends BaseMapper<RolesPermission>{
    @Select("select * from roles_permission where permission_id = #{permissionId}")
    List<RolesPermission> listByPermissionId(@Param("permissionId") Long permissionId);

    @Select({"<script>",
            "select * from roles_permission where permission_id in",
            "<foreach collection='permissionIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<RolesPermission> listByPermissionIds(@Param("permissionIds") List<Long> permissionIds);
}