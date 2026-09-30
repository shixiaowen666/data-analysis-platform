package com.chatbi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.chat.entity.AiBody;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiBodyMapper extends BaseMapper<AiBody> {

    AiBody getAiBodyByCode(String code);

    @Select("SELECT \n" +
            "\tt.id,\n" +
            "  t.tb_name AS tableName,\n" +
            "  t.note  AS tbDescription,\n" +
            "  f.basic_key AS columnName,\n" +
            "  f.field_type AS dataType,\n" +
            "  CASE WHEN f.basic_type_key = 'dim' THEN TRUE ELSE FALSE END AS isDimension,\n" +
            "  CASE WHEN f.basic_type_key = 'index' THEN TRUE ELSE FALSE END AS isMeasure,\n" +
            "  f.basic_name AS columnDescription,\n" +
            "  f.summary AS summary\n" +
            "FROM dcar_ai_body_relation r\n" +
            "JOIN olap_table_pro t ON r.relation_id = t.id\n" +
            "LEFT JOIN olap_table_field_mapping f ON t.id = f.table_id\n" +
            "WHERE r.code = #{code} AND f.status = 1 AND f.basic_id IS NOT NULL AND f.basic_id != '' and f.basic_key IS NOT NULL AND f.basic_key != ''\n" )
    List<Map<String, Object>> getResolverTableSummaries(@Param("code") String code);

    @Select("SELECT relation_id FROM dcar_ai_body_relation " +
            "WHERE code = #{code} AND relation_type = 0")
    List<Long> getPreferredTableIds(@Param("code") String code);

    @Select("SELECT relation_id FROM dcar_ai_body_relation " +
            "WHERE code = #{code} AND relation_type = 1")
    List<Long> getPreferredModelIds(@Param("code") String code);

    @Select("SELECT DISTINCT " +
            "b.chinese_name AS dimension_name, " +
            "b.english_name AS dimension_code, " +
            "COALESCE(d.caliber_description, f.basic_name) AS description, " +
            "COALESCE(b.data_type, '') AS data_type, " +
            "d.dimension_values AS possible_values " +
            "FROM olap_table_field_mapping f " +
            "JOIN olap_basic_pro b ON f.basic_id = b.id AND b.category = 1 " +
            "LEFT JOIN olap_basic_pro_dimension d ON b.id = d.olap_basic_pro_id " +
            "WHERE f.table_id IN (" +
            "  SELECT r.relation_id FROM dcar_ai_body_relation r " +
            "  WHERE r.code = #{code} AND r.relation_type = 0 " +
            "  UNION " +
            "  SELECT m.fact_table_id FROM dcar_ai_body_relation r " +
            "  JOIN olap_data_model m ON r.relation_id = m.id " +
            "  WHERE r.code = #{code} AND r.relation_type = 1 " +
            "  UNION " +
            "  SELECT md.dim_table_id FROM dcar_ai_body_relation r " +
            "  JOIN olap_data_model_dimension md ON r.relation_id = md.model_id " +
            "  WHERE r.code = #{code} AND r.relation_type = 1 " +
            ") AND f.status = 1 AND f.basic_type_key IN ('dim', 'dimid')")
    List<Map<String, Object>> getResolverDimensions(@Param("code") String code);

    @Select("SELECT DISTINCT " +
            "b.id AS metric_id, " +
            "b.chinese_name AS metric_name, " +
            "b.english_name AS metric_code, " +
            "COALESCE(i.caliber_description, f.basic_name) AS description, " +
            "COALESCE(i.unit, '') AS unit, " +
            "COALESCE(b.data_type, '') AS data_type " +
            "FROM olap_table_field_mapping f " +
            "JOIN olap_basic_pro b ON f.basic_id = b.id AND b.category = 2 " +
            "LEFT JOIN olap_basic_pro_indicator i ON b.id = i.olap_basic_pro_id " +
            "WHERE f.table_id IN (" +
            "  SELECT r.relation_id FROM dcar_ai_body_relation r " +
            "  WHERE r.code = #{code} AND r.relation_type = 0 " +
            "  UNION " +
            "  SELECT m.fact_table_id FROM dcar_ai_body_relation r " +
            "  JOIN olap_data_model m ON r.relation_id = m.id " +
            "  WHERE r.code = #{code} AND r.relation_type = 1 " +
            ") AND f.status = 1 AND f.basic_type_key = 'index'")
    List<Map<String, Object>> getResolverIndicators(@Param("code") String code);

    /**
     * 查引用过给定原子指标的计算/派生指标（已上线）。
     * 派生指标在SQL内完成过滤（单值引用）；计算指标只圈定"至少引用一个"的候选，全引用校验在Java层。
     */
    @Select("<script>" +
            "SELECT DISTINCT " +
            "  b.id, " +
            "  b.chinese_name AS metric_name, " +
            "  b.english_name AS metric_code, " +
            "  i.caliber_description AS description, " +
            "  i.unit, " +
            "  b.data_type, " +
            "  i.calculated_production, i.derivative_production " +
            "FROM olap_basic_pro b " +
            "JOIN olap_basic_pro_indicator i ON b.id = i.olap_basic_pro_id " +
            "WHERE b.category = 2 AND b.status = 2 AND (" +
            "  (i.derivative_production IS NOT NULL AND i.derivative_production != '' " +
            "   AND CAST(JSON_UNQUOTE(JSON_EXTRACT(i.derivative_production, '$.indicatorId')) AS UNSIGNED) IN " +
            "     <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>) " +
            "  OR " +
            "  (i.calculated_production IS NOT NULL AND i.calculated_production != '' AND (" +
            "     <foreach collection='ids' item='id' separator=' OR '>JSON_CONTAINS(i.calculated_production->'$.indicatorList', JSON_OBJECT('id', #{id}))</foreach>" +
            "  ))" +
            ")" +
            "</script>")
    List<Map<String, Object>> getResolverCalcIndicators(@Param("ids") List<Long> ids);

    /** 全量：所有已上线原子指标（有映射行，不限表） */
    @Select("SELECT DISTINCT " +
            "b.id AS metric_id, " +
            "b.chinese_name AS metric_name, " +
            "b.english_name AS metric_code, " +
            "COALESCE(i.caliber_description, f.basic_name) AS description, " +
            "COALESCE(i.unit, '') AS unit, " +
            "COALESCE(b.data_type, '') AS data_type " +
            "FROM olap_table_field_mapping f " +
            "JOIN olap_basic_pro b ON f.basic_id = b.id AND b.category = 2 " +
            "LEFT JOIN olap_basic_pro_indicator i ON b.id = i.olap_basic_pro_id " +
            "WHERE f.status = 1 AND f.basic_type_key = 'index' AND b.status = 2")
    List<Map<String, Object>> getAllResolverIndicators();

    /** 全量：所有已上线计算/派生指标 */
    @Select("SELECT DISTINCT " +
            "  b.id, " +
            "  b.chinese_name AS metric_name, " +
            "  b.english_name AS metric_code, " +
            "  i.caliber_description AS description, " +
            "  i.unit, " +
            "  b.data_type, " +
            "  i.calculated_production, i.derivative_production " +
            "FROM olap_basic_pro b " +
            "JOIN olap_basic_pro_indicator i ON b.id = i.olap_basic_pro_id " +
            "WHERE b.category = 2 AND b.status = 2 AND (" +
            "  (i.calculated_production IS NOT NULL AND i.calculated_production != '') " +
            "  OR (i.derivative_production IS NOT NULL AND i.derivative_production != ''))")
    List<Map<String, Object>> getAllResolverCalcIndicators();

    /** 全量：所有已上线维度 */
    @Select("SELECT DISTINCT " +
            "b.id AS dimension_id, " +
            "b.chinese_name AS dimension_name, " +
            "b.english_name AS dimension_code, " +
            "COALESCE(d.caliber_description, f.basic_name) AS description, " +
            "COALESCE(b.data_type, '') AS data_type, " +
            "d.dimension_values AS possible_values " +
            "FROM olap_table_field_mapping f " +
            "JOIN olap_basic_pro b ON f.basic_id = b.id AND b.category = 1 " +
            "LEFT JOIN olap_basic_pro_dimension d ON b.id = d.olap_basic_pro_id " +
            "WHERE f.status = 1 AND f.basic_type_key IN ('dim', 'dimid') AND b.status = 2")
    List<Map<String, Object>> getAllResolverDimensions();

    /** 全量：所有表及字段映射 */
    @Select("SELECT " +
            "  t.id, " +
            "  t.tb_name AS tableName, " +
            "  t.note  AS tbDescription, " +
            "  f.basic_id AS basicId, " +
            "  f.basic_key AS columnName, " +
            "  f.field_type AS dataType, " +
            "  CASE WHEN f.basic_type_key = 'dim' THEN TRUE ELSE FALSE END AS isDimension, " +
            "  CASE WHEN f.basic_type_key = 'index' THEN TRUE ELSE FALSE END AS isMeasure, " +
            "  f.basic_name AS columnDescription, " +
            "  f.summary AS summary " +
            "FROM olap_table_pro t " +
            "LEFT JOIN olap_table_field_mapping f ON t.id = f.table_id " +
            "WHERE f.status = 1 AND f.basic_id IS NOT NULL AND f.basic_id != '' " +
            "AND f.basic_key IS NOT NULL AND f.basic_key != ''")
    List<Map<String, Object>> getAllResolverTableSummaries();

    /** 全量：所有知识库条目 */
    @Select("SELECT id, ai_body_id, knowledge_element FROM ai_body_knowledge_info")
    List<Map<String, Object>> getAllKnowledgeElements();
}
