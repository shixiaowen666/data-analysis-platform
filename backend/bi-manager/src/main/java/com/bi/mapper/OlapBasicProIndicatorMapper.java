package com.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bi.entity.OlapBasicProIndicator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 指标扩展信息 Mapper
 */
@Mapper
public interface OlapBasicProIndicatorMapper extends BaseMapper<OlapBasicProIndicator> {
    @Select("SELECT * FROM olap_basic_pro_indicator WHERE olap_basic_pro_id = #{id} LIMIT 1")
    OlapBasicProIndicator selectOneByBasicId(Long id);
}
