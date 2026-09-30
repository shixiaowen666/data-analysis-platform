package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 分析数据表
 */
@Data
@TableName("olap_table_pro")
public class OlapTableProDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long metaTableId;
    private String dbName;
    private String tbName;
    private String cnName;
    private String tbCnName;
    private String note;
    private String theme;
    private String themeKey;
    private String type;
    private String typeKey;
    private Short modelType;
    private String tbType;
    private String tbTypeKey;
    private Integer status;
    private Long sourceId;
    private String createdBy;
    private Date createdAt;
    private String updatedBy;
    private Date updatedAt;
    private String querySql;
    private String viewSql;
    private Long tenantId;
}
