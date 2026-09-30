package com.bi.mapper;

import com.bi.vo.CompatibleFieldRow;
import com.bi.vo.FieldTreeRow;
import com.bi.vo.IndicatorInfoRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 指标/维度可用性互滤查询
 */
@Mapper
public interface OlapFieldRelationMapper {

    String BASE_FROM =
            " FROM olap_table_field_mapping a " +
            " INNER JOIN olap_table_pro b ON a.table_id = b.id " +
            " INNER JOIN olap_basic_pro c ON a.basic_id = c.id ";

    String BASE_WHERE =
            " WHERE a.status = 1 " +
            "   AND b.status = 3 " +
            "   AND c.status = 2 " +
            "   AND a.tenant_id = #{tenantId} ";

    @Select("<script>" +
            "SELECT DISTINCT a.table_id " + BASE_FROM + BASE_WHERE +
            " AND c.category = 2 AND c.id = #{metricId} " +
            "</script>")
    List<Long> selectFactTableIdsByMetricId(@Param("metricId") Long metricId,
                                            @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT " +
            "  CASE WHEN b.type_key = 'fact' THEN a.table_id ELSE m.fact_table_id END AS fact_table_id " +
            BASE_FROM +
            " LEFT JOIN olap_data_model_dimension dmd ON b.type_key = 'dim' AND a.table_id = dmd.dim_table_id " +
            " LEFT JOIN olap_data_model m ON dmd.model_id = m.id AND m.status = 1 " +
            BASE_WHERE +
            " AND c.category = 1 AND c.id = #{dimensionId} " +
            " AND (b.type_key = 'fact' OR m.fact_table_id IS NOT NULL) " +
            "</script>")
    List<Long> selectFactTableIdsByDimensionId(@Param("dimensionId") Long dimensionId,
                                               @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  d.dimension_type AS dimensionType, d.partition_field AS partitionField, d.time_dynamic AS timeDynamic, " +
            "  NULL AS calculatedProduction, NULL AS derivativeProduction " +
            BASE_FROM +
            " LEFT JOIN olap_basic_pro_dimension d ON c.id = d.olap_basic_pro_id " +
            BASE_WHERE +
            " AND c.category = 1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            " ORDER BY c.chinese_name ASC " +
            "</script>")
    List<CompatibleFieldRow> selectAllOnlineDimensions(@Param("keyword") String keyword,
                                                       @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  NULL AS dimensionType, NULL AS partitionField, NULL AS timeDynamic, " +
            "  i.calculated_production AS calculatedProduction, i.derivative_production AS derivativeProduction, " +
            "  CASE i.unit WHEN '1' THEN 1 WHEN '2' THEN 2 ELSE NULL END AS unitType " +
            BASE_FROM +
            " LEFT JOIN olap_basic_pro_indicator i ON c.id = i.olap_basic_pro_id " +
            BASE_WHERE +
            " AND c.category = 2 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            " ORDER BY c.chinese_name ASC " +
            "</script>")
    List<CompatibleFieldRow> selectAllOnlineMetrics(@Param("keyword") String keyword,
                                                    @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  d.dimension_type AS dimensionType, d.partition_field AS partitionField, d.time_dynamic AS timeDynamic, " +
            "  NULL AS calculatedProduction, NULL AS derivativeProduction " +
            BASE_FROM +
            " LEFT JOIN olap_basic_pro_dimension d ON c.id = d.olap_basic_pro_id " +
            BASE_WHERE +
            " AND c.category = 1 " +
            " AND ( " +
            "   a.table_id IN " +
            "   <foreach collection='factTableIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "   OR a.table_id IN ( " +
            "     SELECT dmd.dim_table_id FROM olap_data_model m " +
            "     INNER JOIN olap_data_model_dimension dmd ON m.id = dmd.model_id " +
            "     WHERE m.status = 1 AND m.fact_table_id IN " +
            "     <foreach collection='factTableIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "   ) " +
            " ) " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            " ORDER BY c.chinese_name ASC " +
            "</script>")
    List<CompatibleFieldRow> selectCompatibleDimensions(@Param("factTableIds") Collection<Long> factTableIds,
                                                      @Param("keyword") String keyword,
                                                      @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  NULL AS dimensionType, NULL AS partitionField, NULL AS timeDynamic, " +
            "  i.calculated_production AS calculatedProduction, i.derivative_production AS derivativeProduction, " +
            "  CASE i.unit WHEN '1' THEN 1 WHEN '2' THEN 2 ELSE NULL END AS unitType " +
            BASE_FROM +
            " LEFT JOIN olap_basic_pro_indicator i ON c.id = i.olap_basic_pro_id " +
            BASE_WHERE +
            " AND c.category = 2 " +
            " AND a.table_id IN " +
            " <foreach collection='factTableIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            " ORDER BY c.chinese_name ASC " +
            "</script>")
    List<CompatibleFieldRow> selectCompatibleMetrics(@Param("factTableIds") Collection<Long> factTableIds,
                                                     @Param("keyword") String keyword,
                                                     @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  d.dimension_type AS dimensionType, d.partition_field AS partitionField, d.time_dynamic AS timeDynamic, " +
            "  NULL AS calculatedProduction, NULL AS derivativeProduction " +
            BASE_FROM +
            " LEFT JOIN olap_basic_pro_dimension d ON c.id = d.olap_basic_pro_id " +
            BASE_WHERE +
            " AND c.category = 1 AND c.id IN " +
            " <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "</script>")
    List<CompatibleFieldRow> selectDimensionsByIds(@Param("ids") Collection<Long> ids,
                                                   @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  NULL AS dimensionType, NULL AS partitionField, NULL AS timeDynamic, " +
            "  i.calculated_production AS calculatedProduction, i.derivative_production AS derivativeProduction, " +
            "  CASE i.unit WHEN '1' THEN 1 WHEN '2' THEN 2 ELSE NULL END AS unitType " +
            BASE_FROM +
            " LEFT JOIN olap_basic_pro_indicator i ON c.id = i.olap_basic_pro_id " +
            BASE_WHERE +
            " AND c.category = 2 AND c.id IN " +
            " <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "</script>")
    List<CompatibleFieldRow> selectMetricsByIds(@Param("ids") Collection<Long> ids,
                                                @Param("tenantId") Long tenantId);

    // ====== 计算指标 / 派生指标查询（不依赖 mapping 表） ======

    @Select("<script>" +
            "SELECT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  NULL AS dimensionType, NULL AS partitionField, NULL AS timeDynamic, " +
            "  i.calculated_production AS calculatedProduction, i.derivative_production AS derivativeProduction, " +
            "  CASE i.unit WHEN '1' THEN 1 WHEN '2' THEN 2 ELSE NULL END AS unitType " +
            "FROM olap_basic_pro c " +
            "JOIN olap_basic_pro_indicator i ON c.id = i.olap_basic_pro_id " +
            "WHERE c.category = 2 AND c.status = 2 AND c.tenant_id = #{tenantId} " +
            "  AND (i.calculated_production IS NOT NULL OR i.derivative_production IS NOT NULL) " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            " ORDER BY c.chinese_name ASC " +
            "</script>")
    List<CompatibleFieldRow> selectCalcDeriveMetrics(@Param("keyword") String keyword,
                                                      @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT c.id, c.key_str AS code, c.chinese_name AS chineseName, c.english_name AS englishName, " +
            "  NULL AS dimensionType, NULL AS partitionField, NULL AS timeDynamic, " +
            "  i.calculated_production AS calculatedProduction, i.derivative_production AS derivativeProduction, " +
            "  CASE i.unit WHEN '1' THEN 1 WHEN '2' THEN 2 ELSE NULL END AS unitType " +
            "FROM olap_basic_pro c " +
            "JOIN olap_basic_pro_indicator i ON c.id = i.olap_basic_pro_id " +
            "WHERE c.category = 2 AND c.status = 2 AND c.tenant_id = #{tenantId} " +
            "  AND (i.calculated_production IS NOT NULL OR i.derivative_production IS NOT NULL) " +
            "  AND c.id IN " +
            "  <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "</script>")
    List<CompatibleFieldRow> selectCalcDeriveMetricsByIds(@Param("ids") Collection<Long> ids,
                                                           @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT i.olap_basic_pro_id AS olapBasicProId, " +
            "  i.calculated_production AS calculatedProduction, " +
            "  i.derivative_production AS derivativeProduction " +
            "FROM olap_basic_pro_indicator i " +
            "JOIN olap_basic_pro c ON i.olap_basic_pro_id = c.id " +
            "WHERE i.olap_basic_pro_id = #{id} AND c.status = 2 AND c.tenant_id = #{tenantId} " +
            "</script>")
    IndicatorInfoRow selectIndicatorInfoById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT DISTINCT a.basic_id " +
            "FROM olap_table_field_mapping a " +
            "INNER JOIN olap_table_pro b ON a.table_id = b.id " +
            "INNER JOIN olap_basic_pro c ON a.basic_id = c.id " +
            "WHERE a.status = 1 AND b.status = 3 AND c.status = 2 AND c.category = 2 " +
            "  AND a.table_id IN " +
            "  <foreach collection='factTableIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "  AND a.tenant_id = #{tenantId} " +
            "</script>")
    List<Long> selectAvailableMetricIdsByFactTables(@Param("factTableIds") Collection<Long> factTableIds,
                                                     @Param("tenantId") Long tenantId);

//    @Select("<script>" +
//            "SELECT DISTINCT c.id, c.chinese_name AS chineseName, c.english_name AS englishName, " +
//            "  d.partition_field AS partitionField, d.time_dynamic AS timeDynamic " +
//            BASE_FROM +
//            " LEFT JOIN olap_basic_pro_dimension d ON c.id = d.olap_basic_pro_id " +
//            BASE_WHERE +
//            " AND c.category = 1 " +
//            "<if test='keyword != null and keyword != \"\"'>" +
//            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
//            "</if>" +
//            "<if test='key != null and key != \"\"'>" +
//            " AND c.english_name LIKE CONCAT('%', #{key}, '%') " +
//            "</if>" +
//            " ORDER BY c.chinese_name ASC " +
//            "</script>")
//    List<FieldTreeRow> selectFieldTreeDimensions(@Param("keyword") String keyword,
//                                                 @Param("key") String key,
//                                                 @Param("tenantId") Long tenantId);
//
//    @Select("<script>" +
//            "SELECT DISTINCT c.id, c.chinese_name AS chineseName, c.english_name AS englishName, " +
//            "  c.olap_label_name AS olapLabelName " +
//            BASE_FROM +
//            BASE_WHERE +
//            " AND c.category = 2 " +
//            "<if test='keyword != null and keyword != \"\"'>" +
//            " AND c.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
//            "</if>" +
//            "<if test='key != null and key != \"\"'>" +
//            " AND c.english_name LIKE CONCAT('%', #{key}, '%') " +
//            "</if>" +
//            " ORDER BY c.olap_label_name ASC, c.chinese_name ASC " +
//            "</script>")
//    List<FieldTreeRow> selectFieldTreeMetrics(@Param("keyword") String keyword,
//                                              @Param("key") String key,
//                                              @Param("tenantId") Long tenantId);
}
