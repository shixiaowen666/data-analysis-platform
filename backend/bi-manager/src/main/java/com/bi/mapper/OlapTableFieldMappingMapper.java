package com.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bi.entity.OlapTableFieldMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OlapTableFieldMappingMapper extends BaseMapper<OlapTableFieldMapping> {
    @Select("<script>" +
            "select otp.source_id,\n" +
            "       otp.id                                     as soucreTableId,\n" +
            "       case when otp.tb_type = 0 then concat(otp.db_name,'.',(select table_name from meta_table where id = otp.meta_table_id))\n" +
            "           when otp.tb_type = 1 then concat(otp.db_name,'.',otp.tb_name) end soucreTableName,\n" +
            "       otfm.field_key                             as sourceField,otfm.summary_key as summary \n" +
            "from olap_table_field_mapping otfm\n" +
            "         inner join olap_table_pro otp on otp.id = otfm.table_id\n" +
            "where otfm.basic_id = #{id}" +
            "</script>")
    List<Map<String, Object>> getMappingListByDimId(Long id);
}
