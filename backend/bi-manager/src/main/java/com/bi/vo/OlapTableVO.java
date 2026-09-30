package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析表列表项
 */
@Data
public class OlapTableVO {

    private Long id;

    /**
     * 关联 meta_table.id
     */
    private Long metaTableId;

    /**
     * 表英文名
     */
    private String tbName;

    /**
     * 表中文名
     */
    private String cnName;

    /**
     * 数据源 ID
     */
    private Long sourceId;

    /**
     * 数据源名称
     */
    private String sourceName;

    /**
     * 存储类型 key：0-物理表 1-视图
     */
    private String tbTypeKey;

    /**
     * 存储类型名称
     */
    private String tbTypeName;

    /**
     * 业务类型
     */
    private String type;

    /**
     * 业务类型 key：fact/dim
     */
    private String typeKey;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 字段映射进度
     */
    private Integer mappedCount;

    /**
     * 总字段数
     */
    private Integer totalCount;

    private LocalDateTime updatedAt;
}
