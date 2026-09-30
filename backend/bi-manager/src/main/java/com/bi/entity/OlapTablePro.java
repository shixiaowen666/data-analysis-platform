package com.bi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分析数据表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("olap_table_pro")
public class OlapTablePro extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 meta_table.id
     */
    private Long metaTableId;

    /**
     * 数据源ID
     */
    private Long sourceId;

    /**
     * 库名
     */
    private String dbName;

    /**
     * 表名
     */
    private String tbName;

    /**
     * 表中文名
     */
    private String cnName;

    /**
     * 表中文名冗余
     */
    private String tbCnName;

    /**
     * 表注释
     */
    private String note;

    /**
     * 主题
     */
    private String theme;

    /**
     * 主题 key
     */
    private String themeKey;

    /**
     * 业务类型fact/dim
     */
    private String type;

    /**
     * 业务类型 key：fact/dim
     */
    private String typeKey;

    /**
     * 0-OLAP模型 1-临查模型
     */
    private Integer modelType;

    /**
     * 0-物理表 1-视图
     */
    private String tbType;

    /**
     * 0-物理表 1-视图
     */
    private String tbTypeKey;

    /**
     * 0-删除 1-审批中 2-待上线 3-已上线 4-已下线 5-已驳回
     */
    private Integer status;

    /**
     * 实时查询 SQL
     */
    private String querySql;

    /**
     * 视图 SQL
     */
    private String viewSql;

    /**
     * 租户 ID
     */
    private Long tenantId;
}
