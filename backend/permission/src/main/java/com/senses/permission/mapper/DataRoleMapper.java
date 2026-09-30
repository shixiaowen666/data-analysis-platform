package com.senses.permission.mapper;

import java.util.List;

import com.senses.permission.entity.DataRolesPermission;
import com.senses.permission.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.DataRole;
import org.apache.ibatis.annotations.Select;

/**
 * 数据角色表;(data_role)表数据库访问层
 *
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface DataRoleMapper extends BaseMapper<DataRole> {

    List<DataRole> treeList(@Param("deptId") Long deptId, @Param("filterVal") String filterVal, @Param("orId") Long orId, @Param("status") Integer status);


    List<DataRole> listByPage(@Param("keyWords") String keyWords);

    Long count(@Param("keyWords") String keyWords);

    List<DataRole> selectByDeptId(@Param("deptId") Long deptId);

    List<DataRole> selectByGroupId(@Param("groupId") Long groupId);

    List<DataRole> getBindUserListByPage(@Param("filterVal") String filterVal, @Param("id") Long id);

    Long countBindUserListByPage(@Param("filterVal") String filterVal, @Param("id") Long id);

    List<DataRolesPermission> getDataPermissionListByPage(@Param("dataRoleId") Long dataRoleId,@Param("keyWords") String keyWords);

    Long countDataPermissionList(@Param("dataRoleId") Long dataRoleId,@Param("keyWords") String keyWords);
    @Select({"<script>",
            "select dr.*,d.name as dept_name from data_role dr ",
            "join dept d on dr.dept_id = d.id ",
            "where dr.id in",
            "<foreach collection='dataRoleIds' item='item' open='(' separator=',' close=')'>",
            "#{item}",
            "</foreach>",
            "</script>"
    })
    List<DataRole> deptDataRoleListByIds(@Param("dataRoleIds")List<Long> dataRoleIds);

    @Select("select r.* from data_role r join users_data_roles udr on r.data_role_type = 1 and udr.user_id = #{userId} and udr.data_role_id = r.id")
    DataRole selectPersonDataRole(@Param("userId") Long userId);

    DataRole getDataRoleById(@Param("id") Long id);
}

