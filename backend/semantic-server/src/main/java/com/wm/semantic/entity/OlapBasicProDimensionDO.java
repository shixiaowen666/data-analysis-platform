package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 维度属性配置表
 * 记录维度在物理表中的字段映射关系
 */
@Data
@TableName("olap_basic_pro_dimension")
public class OlapBasicProDimensionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long olapBasicProId;
    private Integer dimensionType;
    private Integer highLevelFlag;
    private Long databaseTableId;
    private String databaseTableName;
    private Long columnId;
    private String columnKey;
    private String columnName;
    private String dimensionTranslation;
    private Long valueFieldId;
    private String valueFieldKey;
    private String valueFieldName;
    private String caliberDescription;
    private String monitor;
    private String dimFilter;
    private String timeDynamic;
    private String partitionField;
    private String partitionFormat;
    private Integer isAttributing;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOlapBasicProId() { return olapBasicProId; }
    public void setOlapBasicProId(Long olapBasicProId) { this.olapBasicProId = olapBasicProId; }
    public Integer getDimensionType() { return dimensionType; }
    public void setDimensionType(Integer dimensionType) { this.dimensionType = dimensionType; }
    public Integer getHighLevelFlag() { return highLevelFlag; }
    public void setHighLevelFlag(Integer highLevelFlag) { this.highLevelFlag = highLevelFlag; }
    public Long getDatabaseTableId() { return databaseTableId; }
    public void setDatabaseTableId(Long databaseTableId) { this.databaseTableId = databaseTableId; }
    public String getDatabaseTableName() { return databaseTableName; }
    public void setDatabaseTableName(String databaseTableName) { this.databaseTableName = databaseTableName; }
    public Long getColumnId() { return columnId; }
    public void setColumnId(Long columnId) { this.columnId = columnId; }
    public String getColumnKey() { return columnKey; }
    public void setColumnKey(String columnKey) { this.columnKey = columnKey; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDimensionTranslation() { return dimensionTranslation; }
    public void setDimensionTranslation(String dimensionTranslation) { this.dimensionTranslation = dimensionTranslation; }
    public Long getValueFieldId() { return valueFieldId; }
    public void setValueFieldId(Long valueFieldId) { this.valueFieldId = valueFieldId; }
    public String getValueFieldKey() { return valueFieldKey; }
    public void setValueFieldKey(String valueFieldKey) { this.valueFieldKey = valueFieldKey; }
    public String getValueFieldName() { return valueFieldName; }
    public void setValueFieldName(String valueFieldName) { this.valueFieldName = valueFieldName; }
    public String getCaliberDescription() { return caliberDescription; }
    public void setCaliberDescription(String caliberDescription) { this.caliberDescription = caliberDescription; }
    public String getMonitor() { return monitor; }
    public void setMonitor(String monitor) { this.monitor = monitor; }
    public String getDimFilter() { return dimFilter; }
    public void setDimFilter(String dimFilter) { this.dimFilter = dimFilter; }
    public String getTimeDynamic() { return timeDynamic; }
    public void setTimeDynamic(String timeDynamic) { this.timeDynamic = timeDynamic; }
    public String getPartitionField() { return partitionField; }
    public void setPartitionField(String partitionField) { this.partitionField = partitionField; }
    public String getPartitionFormat() { return partitionFormat; }
    public void setPartitionFormat(String partitionFormat) { this.partitionFormat = partitionFormat; }
    public Integer getIsAttributing() { return isAttributing; }
    public void setIsAttributing(Integer isAttributing) { this.isAttributing = isAttributing; }
}