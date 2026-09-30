package com.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bi.entity.OlapTablePro;
import com.bi.vo.TableMappingVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OlapTableProMapper extends BaseMapper<OlapTablePro> {
    @Select("<script>" +
            "select tbl.id                                     tableId,\n" +
            "       tbl.table_name                             tableName,\n" +
            "       tbl.table_comment                          tableComment,\n" +
            "       IF(otp.id is not null, '已注册', '未注册') ifRegister,\n" +
            "       count(cln.id)                              clnNum\n" +
            "from meta_table tbl\n" +
            "         inner join meta_column cln on cln.table_id = tbl.id\n" +
            "         left join olap_table_pro otp on otp.meta_table_id = tbl.id and otp.status != 0 \n" +
            "where tbl.source_id = #{sourceId} and tbl.tenant_id = #{currentTenantId}\n" +
            "<if test=\"keyword != null and keyword != ''\">\n" +
            "  and (tbl.table_name like CONCAT('%', #{keyword}, '%') or tbl.table_comment like CONCAT('%', #{keyword}, '%')) \n" +
            "</if>" +
            "group by tbl.id,\n" +
            "         tbl.table_name,\n" +
            "         tbl.table_comment,ifRegister" +
            "</script>")
    List<TableMappingVo> listTablesBySourceId(Long sourceId,String keyword, Long currentTenantId);
}
