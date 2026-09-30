package com.chatbi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.chat.entity.OlapReportGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OlapReportGroupMapper extends BaseMapper<OlapReportGroup> {

    @Select("SELECT id, group_code, group_name, group_config FROM olap_report_group " +
            "WHERE group_code = #{code} AND tenant_id = #{tenantId} AND status = 2 LIMIT 1")
    OlapReportGroup getOnlineByCode(@Param("code") String code, @Param("tenantId") Long tenantId);
}
