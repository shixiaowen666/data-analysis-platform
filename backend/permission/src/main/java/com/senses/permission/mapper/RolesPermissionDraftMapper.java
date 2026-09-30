package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.RolesPermissionDraft;
import org.apache.ibatis.annotations.Select;

/**
 * 角色与权限管理关联草稿表;(roles_permission_draft)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Mapper
public interface RolesPermissionDraftMapper extends BaseMapper<RolesPermissionDraft>{
  @Select("select * from roles_permission_draft where role_id = #{roleId}")
  List<RolesPermissionDraft> selectListByRoleId(@Param("roleId") Long roleId);
}