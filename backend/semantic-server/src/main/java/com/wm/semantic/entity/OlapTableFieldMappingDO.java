package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 元数据字段映射表
 */
@Data
@TableName("olap_table_field_mapping")
public class OlapTableFieldMappingDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String fieldKey;
    private String fieldName;
    private Long basicId;
    private String basicKey;
    private String basicType;
    private String basicTypeKey;
    private String basicName;
    private String fieldType;
    private String fieldTypeName;
    private String fieldNote;
    private Integer isCustomize;
    private Short pfStatus;
    private String summary;
    private String summaryKey;
    private String expression;
    private String innerFieldKey;
    private String lookBackMappingIds;
    private String lookBackMappingNames;
    private Integer lookBackFlag;
    private Integer height;
    private String engineInfos;
    private Short status;
    private String dateFormat;
    private String unit;
    private Long tenantId;
    private String createdBy;
    private Date createdAt;
    private String updatedBy;
    private Date updatedAt;
}
