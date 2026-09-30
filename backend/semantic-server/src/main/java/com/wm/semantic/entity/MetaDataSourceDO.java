package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 数据源注册表
 */
@Data
@TableName("meta_data_source")
public class MetaDataSourceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String defaultDb;
    private String schemaName;
    private String jdbcUrl;
    private Integer status;
    private Long tenantId;
    private Long createdBy;
    private Date createdAt;
    private Long updatedBy;
    private Date updatedAt;
}
