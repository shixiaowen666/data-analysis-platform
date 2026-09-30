package com.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.entity.OlapBasicProDimension;
import com.bi.vo.DimensionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 维度扩展信息 Mapper
 */
@Mapper
public interface OlapBasicProDimensionMapper extends BaseMapper<OlapBasicProDimension> {

    /**
     * 分页查询维度列表（关联 olap_basic_pro 获取中文名称等信息）
     */
    @Select("<script>" +
            "SELECT p.id, p.key_str AS code, " +
            "p.chinese_name AS chineseName, p.english_name AS englishName, " +
            "p.standard_name AS standardName, p.alias AS alias, " +
            "p.category, p.abbreviation as abbreviation," +
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
            "  p.category = 1 " +
            "  AND p.tenant_id = #{tenantId} " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND (p.chinese_name LIKE CONCAT('%', #{keyword}, '%') OR p.english_name LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if> " +
            "<if test='dimensionType != null'> " +
            "  AND d.dimension_type = #{dimensionType} " +
            "</if> " +
            "<if test='status != null'> " +
            "  AND p.status = #{status} " +
            "</if> " +
            "</where> " +
            "ORDER BY p.updated_at DESC" +
            "</script>")
    IPage<DimensionVO> selectDimensionPage(Page<DimensionVO> page,
                                           @Param("keyword") String keyword,
                                           @Param("dimensionType") Integer dimensionType,
                                           @Param("status") Integer status,
                                           @Param("tenantId") Long tenantId);

    /**
     * 根据 olap_basic_pro_id 查询维度扩展信息
     */
    @Select("SELECT * FROM olap_basic_pro_dimension WHERE olap_basic_pro_id = #{id}")
    OlapBasicProDimension selectByBasicProId(@Param("id") Long id);

    /**
     * 查询所有已上线的维度列表（不分页）
     */
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
            "  p.category = 1 " +
            "  AND p.tenant_id = #{tenantId} " +
            "  AND p.status = 2 " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND p.chinese_name LIKE CONCAT('%', #{keyword}, '%') " +
            "</if> " +
            "</where> " +
            "ORDER BY p.updated_at DESC" +
            "</script>")
    List<DimensionVO> selectDimensionList(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    /**
     * 查询被计算指标引用的维度 ID 列表
     */
    @Select("<script>" +
            "SELECT DISTINCT i2.olap_basic_pro_id " +
            "FROM olap_basic_pro_dimension d " +
            "INNER JOIN olap_basic_pro p ON d.olap_basic_pro_id = p.id " +
            "INNER JOIN olap_basic_pro_indicator i ON p.id = i.olap_basic_pro_id " +
            "INNER JOIN olap_basic_pro_indicator i2 ON i2.category = 2 " +
            "WHERE p.category = 1 " +
            "AND i2.calculated_production IS NOT NULL " +
            "AND i2.calculated_production != '' " +
            "AND i2.calculated_production LIKE CONCAT('%', #{code}, '%') " +
            "AND p.status = 2 " +
            "</script>")
    List<Long> selectReferencedByCalculation(@Param("code") String code);


}
