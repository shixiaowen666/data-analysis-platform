-- 查询可与当前选择组合的维度
SELECT DISTINCT map.basic_id, basic.chinese_name, basic.english_name
FROM olap_table_field_mapping map
JOIN olap_basic_pro basic ON map.basic_id = basic.id
WHERE map.status = 1
  AND basic.category = 1
  AND basic.status = 2
  <if test="keyword != null and keyword != ''">
    AND basic.chinese_name LIKE CONCAT('%', #{keyword}, '%')
  </if>
  <if test="dimensionIds != null and !dimensionIds.isEmpty()">
    AND map.basic_id NOT IN
    <foreach collection="dimensionIds" open="(" close=")" separator="," item="id">#{id}</foreach>
  </if>
  AND map.table_id IN (
      SELECT t.id FROM olap_table_pro t
      WHERE TRUE
      <if test="dimensionIds != null and !dimensionIds.isEmpty()">
        AND t.id IN (
            SELECT fm.table_id FROM olap_table_field_mapping fm
            WHERE fm.basic_id IN
            <foreach collection="dimensionIds" open="(" close=")" separator="," item="id">#{id}</foreach>
              AND fm.status = 1
            GROUP BY fm.table_id
            HAVING COUNT(DISTINCT fm.basic_id) = #{dimensionSize}
        )
      </if>
      <if test="metricIds != null and !metricIds.isEmpty()">
        AND t.id IN (
            SELECT fm.table_id FROM olap_table_field_mapping fm
            JOIN olap_basic_pro b ON fm.basic_id = b.id
            WHERE fm.basic_id IN
            <foreach collection="metricIds" open="(" close=")" separator="," item="id">#{id}</foreach>
              AND fm.status = 1 AND b.category = 2 AND b.status = 2
            GROUP BY fm.table_id
            HAVING COUNT(DISTINCT fm.basic_id) = #{metricSize}
        )
      </if>
  )
