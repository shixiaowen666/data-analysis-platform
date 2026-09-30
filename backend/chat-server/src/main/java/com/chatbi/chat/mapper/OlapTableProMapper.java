package com.chatbi.chat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OlapTableProMapper {

    @Select("SELECT id FROM olap_table_pro WHERE tb_name = #{tableName}")
    Long getTableIdByName(@Param("tableName") String tableName);

    @Select("SELECT basic_key, basic_type_key FROM olap_table_field_mapping " +
            "WHERE table_id = #{tableId} AND status = 1 AND basic_key IS NOT NULL AND basic_id IS NOT NULL")
    List<Map<String, Object>> getTableFields(@Param("tableId") Long tableId);
}
