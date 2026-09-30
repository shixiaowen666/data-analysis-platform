package com.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.entity.OlapBasicPro;
import com.bi.vo.DimensionVO;
import com.bi.vo.MetricVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 指标基础信息 Mapper
 */
@Mapper
public interface OlapBasicProMapper extends BaseMapper<OlapBasicPro> {

    /**
     * 分页查询指标列表（关联扩展信息表获取 type、caliber 等）
     */
    @Select("<script>" +
            "SELECT p.id, p.key_str AS code, p.standard_name AS standardName, " +
            "p.chinese_name AS chineseName, p.english_name AS englishName, " +
            "p.alias AS alias, " +
            "CASE WHEN i.calculated_production IS NOT NULL THEN 'calc' WHEN i.derivative_production IS NOT NULL THEN 'derive' ELSE 'atom' END AS type, " +
            "i.caliber_description AS caliber, " +
            "i.unit AS unit, " +
            "i.decimal_places AS decimalPlaces, p.status, p.principal_name AS principalName, p.updated_at AS updatedAt " +
            "FROM olap_basic_pro p " +
            "LEFT JOIN olap_basic_pro_indicator i ON p.id = i.olap_basic_pro_id " +
            "<where> " +
            " p.category = 2 and p.tenant_id = #{tenantId} " +
            "<if test='type != null and type != \"\"'> " +
            "  <choose> " +
            "    <when test='type == \"calc\"'> AND i.calculated_production IS NOT NULL </when> " +
            "    <when test='type == \"derive\"'> AND i.derivative_production IS NOT NULL </when> " +
            "    <when test='type == \"atom\"'> AND i.calculated_production IS NULL AND i.derivative_production IS NULL </when> " +
            "  </choose> " +
            "</if> " +
            "<if test='status != null'> " +
            "  AND p.status = #{status} " +
            "</if> " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND (p.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "    OR p.english_name LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "</where> " +
            "ORDER BY p.updated_at DESC" +
            "</script>")
    IPage<MetricVO> selectMetricPage(Page<MetricVO> page,
                                     @Param("keyword") String keyword,
                                     @Param("type") String type,
                                     @Param("status") Integer status,
                                     @Param("tenantId") Long tenantId);

    /**
     * 查询所有已上线的指标列表（不分页）
     */
    @Select("<script>" +
            "SELECT p.id, p.key_str AS code, p.standard_name AS standardName, " +
            "p.chinese_name AS chineseName, p.english_name AS englishName, " +
            "p.alias AS alias, " +
            "CASE WHEN i.calculated_production IS NOT NULL THEN 'calc' WHEN i.derivative_production IS NOT NULL THEN 'derive' ELSE 'atom' END AS type, " +
            "i.caliber_description AS caliber, " +
            "i.unit AS unit, " +
            "i.decimal_places AS decimalPlaces, p.status, p.principal_name AS principalName, p.updated_at AS updatedAt " +
            "FROM olap_basic_pro p " +
            "LEFT JOIN olap_basic_pro_indicator i ON p.id = i.olap_basic_pro_id " +
            "<where> " +
            " p.category = 2 and p.tenant_id = #{tenantId} and p.status = 2 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND (p.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "    OR p.english_name LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "</where> " +
            "ORDER BY p.updated_at DESC" +
            "</script>")
    List<MetricVO> selectMetricList(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT p.id, p.key_str AS code, " +
            "p.chinese_name AS chineseName, p.english_name AS englishName, " +
            "p.standard_name AS standardName, p.alias AS alias, " +
            "p.category, " +
            "d.dimension_type AS dimensionType, " +
            "CASE d.dimension_type WHEN 1 THEN '标准维' WHEN 2 THEN '杂项维' ELSE '' END AS dimensionTypeName, " +
            "d.high_level_flag AS highLevelFlag, " +
            "CASE d.high_level_flag WHEN 1 THEN '高基维' WHEN 2 THEN '普通维' ELSE '' END AS highLevelFlagName, " +
            "d.collect_status AS collectStatus, " +
            "CASE d.collect_status WHEN 0 THEN '未采集' WHEN 1 THEN '全量采集' WHEN 2 THEN '部分采集' ELSE '' END AS collectStatusName, " +
            "p.status, p.principal_name AS principalName, " +
            "d.database_table_name AS databaseTableName, " +
            "d.column_name AS columnName, " +
            "d.value_field_name AS valueFieldName, " +
            "d.is_attributing AS isAttributing, " +
            "d.monitor, d.time_dynamic AS timeDynamic, " +
            "d.partition_field AS partitionField, " +
            "d.partition_format AS partitionFormat, " +
            "p.updated_at AS updatedAt " +
            "FROM olap_basic_pro p " +
            "INNER JOIN olap_basic_pro_dimension d ON p.id = d.olap_basic_pro_id " +
            "<where> " +
            " p.tenant_id = #{currentTenantId} and p.english_name = #{ptdate}" +
            "</where>" +
            "limit 1" +
            "</script>")
    DimensionVO selectPtdate(@Param("currentTenantId") Long currentTenantId, @Param("ptdate") String ptdate);
}
