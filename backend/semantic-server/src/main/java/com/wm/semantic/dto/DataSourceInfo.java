package com.wm.semantic.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源信息
 *
 * type=1：单表，使用 tableId/dbName/tableName
 * type=2：模型，使用 modelId + factTableId/factDbName/factTableName + joinRelations
 *
 * 重构后由 TableSelectionService 一次性填充齐全：
 * 1. dimensionFields / indicatorFields 必须包含 srcField + tableId（决定列归属）
 * 2. 模型必须填充 factTableId / joinRelations，SQL 生成阶段不再回查元数据
 */
@Data
public class DataSourceInfo {
    /** 数据源类型：1-单表，2-data模型。 */
    private Integer type;
    /** 物理表ID，仅 type=1 时有值。 */
    private Long tableId;
    /** 数据源名称（type=1 是表名，type=2 是模型名）。 */
    private String tableName;
    /** 所属数据库名称（type=1 时使用）。 */
    private String dbName;
    /** 模型ID，仅 type=2 时有值。 */
    private Long modelId;

    /** 模型事实表ID，仅 type=2 时有值。 */
    private Long factTableId;
    /** 模型事实表所属库名，仅 type=2 时有值。 */
    private String factDbName;
    /** 模型事实表名，仅 type=2 时有值。 */
    private String factTableName;
    /** 模型 join 关系列表，仅 type=2 时有值。 */
    private List<ModelJoinInfo> joinRelations = new ArrayList<ModelJoinInfo>();

    /** 数据库方言标识（小写）：mysql/dm/postgresql/oracle/hive。 */
    private String dbDialect;

    /** 该表/模型关联的维度字段列表（含 tableId）。 */
    private List<FieldMappingInfo> dimensionFields = new ArrayList<FieldMappingInfo>();
    /** 该表/模型关联的指标字段列表（含 tableId、aggFunc、alias）。 */
    private List<FieldMappingInfo> indicatorFields = new ArrayList<FieldMappingInfo>();
    /** tb_type_key：0-物理表 1-视图。SQL 生成阶段据此决定 FROM 写法。 */
    private Integer tbTypeKey;
    /** 视图定义 SQL，仅 tb_type_key=1 时有值，替代 dbName/tableName 作为子查询。 */
    private String viewSql;
    /** meta_data_source.id，用于动态获取数据库连接。 */
    private Long metaDataSourceId;
    /** ptdate 分区字段映射，用于 timeRange WHERE 过滤。 */
    private FieldMappingInfo ptdateField;

    public static DataSourceInfo fromTable(Long tableId) {
        DataSourceInfo info = new DataSourceInfo();
        info.setType(1);
        info.setTableId(tableId);
        return info;
    }

    public static DataSourceInfo fromModel(Long modelId) {
        DataSourceInfo info = new DataSourceInfo();
        info.setType(2);
        info.setModelId(modelId);
        return info;
    }
}
