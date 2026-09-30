package com.chatbi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.chat.entity.OlapBasicPro;
import com.chatbi.chat.models.DimDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface OlapBasicProMapper extends BaseMapper<OlapBasicPro> {

    @Select("<script>" +
            "select obp.id as id, obp.english_name as dimKey, obp.chinese_name as dimName " +
            "from olap_basic_pro obp " +
            "where obp.status = 2 and obp.category = 1 " +
            "and id in " +
            "<foreach collection=\"dimIds\" item=\"item\" open=\"(\" close=\")\" separator=\",\">" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    List<DimDTO> getRequireDimInfo(@Param("dimIds") Set<Long> dimIds);
}
