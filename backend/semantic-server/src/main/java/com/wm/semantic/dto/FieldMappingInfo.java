package com.wm.semantic.dto;

import lombok.Data;

/**
 * 字段映射信息
 * 对应 olap_table_field_mapping 表
 */
@Data
public class FieldMappingInfo {
    /** 字段对应的 basic_id。 */
    private Long basicId;
    /** 该字段所属物理表ID（model 场景下用于决定使用 fact 还是 dim 的表别名）。 */
    private Long tableId;
    /** 业务标识 key，对应 basic_key，用作 SQL 输出列别名和 filter 引用。 */
    private String basicKey;
    /** 物理表中的真实字段名，对应 field_key（原表字段名）。 */
    private String srcField;
    /** 字段格式类型，对应 field_type，例如 date/number/timestamp/varchar。 */
    private String srcFieldType;
    /** 字段级转换表达式，对应 expression。 */
    private String expression;
    /** 聚合函数：sum/count/avg/max/min/countDistinct，仅指标字段使用。 */
    private String aggFunc;
    /** 业务字段输出别名（指标专用，便于 SQL 输出列命名）。 */
    private String alias;
    /** OlapBasicProDO.englishName，用于 ptdate 等系统维度匹配。 */
    private String basicKeyStr;
    /** basic_type：维度/维度id/指标/分区字段/其他。 */
    private String basicType;
    /** 源字段日期格式：yyyyMMdd、yyyy-MM-dd 等，NULL 表示标准日期类型。 */
    private String dateFormat;
    /** 单位，优先从映射表取值，为空时由 indicator 表兜底。 */
    private String unit;
    /** 时间派生粒度：week / month / quarter / year，null 表示非时间派生维度。 */
    private String timeGranularity;
    /** 数据库方言标识（小写）：mysql/dm/postgresql/oracle/hive。 */
    private String dbDialect;
}
