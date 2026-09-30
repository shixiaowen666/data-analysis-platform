package com.wm.semantic.support.sql.model;

import com.wm.semantic.dto.DataSourceInfo;
import com.wm.semantic.dto.FieldMappingInfo;
import com.wm.semantic.dto.IndicatorMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单数据源 SQL 构建上下文（两层结构）
 *
 * 上下文负责承载从 SqlGenerationServiceImpl 到 builder 之间的标准输入。
 *
 * 两层结构核心：
 *  - 内层（BaseSqlBuilder 输出）："select tX.src as field_key from db.tb t0 [join ...]"，
 *    把所有"用户请求里出现过的"维度+指标字段从物理列名重命名成业务 field_key
 *  - 外层（SegmentSqlBuilder 输出）：在内层之上再包一层
 *    "select agg(mainsrc.field_key) as field_key, ... from (inner) mainsrc where ... group by ..."
 *
 * 因此外部消费端（SegmentSqlBuilder / WHERE 拼装）只需要 fieldId -> field_key 的映射，
 * 完全不感知物理别名 t0/t1。
 */
@Data
public class SqlBuildContext {

    /** 当前数据源（含 enrich 后的 dimensionFields/indicatorFields/joinRelations）。 */
    private DataSourceInfo source;

    /** 用户请求的全部维度字段 ID（按请求顺序）。 */
    private List<Long> dimensionIds;

    /** 维度字段映射（按请求顺序），含 tableId/srcField/fieldKey。 */
    private List<FieldMappingInfo> dimensionFields = new ArrayList<FieldMappingInfo>();

    /** 用户请求的全部指标元数据（按请求顺序）。 */
    private List<IndicatorMeta> requestIndicators;

    /** 当前数据源拥有的指标 ID 集合（用于"未拥有则 0 占位"）。 */
    private Set<Long> ownIndicatorIds;

    /**
     * 字段业务 key 映射：fieldId -> 内层重命名后用作 alias 的 field_key
     *
     * 由 SqlGenerationServiceImpl 在 buildContexts 阶段一次性生成，
     * BaseSqlBuilder / SegmentSqlBuilder / WHERE 拼装均从此读取，确保各处取到的 alias 完全一致。
     */
    private Map<Long, String> fieldKeyMap = new LinkedHashMap<Long, String>();

    /** 已构建好的 where 片段（不含 where 关键字，每行以 "  and ..." 开头）。 */
    private String whereCondition;

    /** 已构建好的 having 片段（不含 having 关键字，每行以 "  and ..." 开头）。 */
    private String havingCondition;

    /** ptdate 分区字段映射，用于 timeRange WHERE 过滤。 */
    private FieldMappingInfo ptdateField;

    /**
     * 需要参与内层投影、但不出现在 GROUP BY 和最终输出中的维度 ID 集合。
     * 衍生指标的 filter 维度依赖此机制。
     */
    private Set<Long> internalDimIds;
}
