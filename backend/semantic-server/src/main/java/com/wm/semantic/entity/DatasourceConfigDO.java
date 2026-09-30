package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("datasource_config")
public class DatasourceConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String datasourceName;
    private String engine;
    private String driverClass;
    private String url;
    private String username;
    private String password;
    private Integer status;
    private Long tenantId;
    private Date createTime;
    private Date modifyTime;
}
