package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 数据角色权限草稿表;
 * @author : liaojinlei
 * @date : 2023-1-12
 */
@Schema(description = "数据角色权限草稿表")
@TableName("data_roles_permission_draft")
@Data
public class DataRolesPermissionDraft{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 数据角色ID */
    @Schema(description = "数据角色ID")
    @TableField("data_role_id")
    private Long dataRoleId;
    
    /** 引擎ID */
    @Schema(description = "引擎ID")
    @TableField("engine_id")
    private Long engineId;
    
    /** 引擎 */
    @Schema(description = "引擎")
    @TableField("engine")
    private String engine;
    
    /** 数据源ID */
    @Schema(description = "数据源ID")
    @TableField("datasource_id")
    private Long datasourceId;
    
    /** 表名 */
    @Schema(description = "表名")
    @TableField("table_name")
    private String tableName;
    
    /** 列名 */
    @Schema(description = "列名")
    @TableField("column_name")
    private String columnName;
    
    /** 操作权限0读1写2读写 */
    @Schema(description = "操作权限0读1写2读写")
    @TableField("action")
    private Integer action;
    
    /** 数据源 */
    @Schema(description = "数据源")
    @TableField("datasource_name")
    private String datasourceName;
    
    /** 数据库 */
    @Schema(description = "数据库")
    @TableField("database_name")
    private String databaseName;

    /** 创建日期 */
    @Schema(description = "创建日期")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    /** 创建人 */
    @Schema(description = "创建人")
    @TableField("created_user")
    private String createdUser;
    /** 修改人 */
    @Schema(description = "修改人")
    private String modifyUser;
    /** 更新时间 */
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

    /** 截止时间 */
    @Schema(description = "截止时间")
    @TableField("deadline_time")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date deadlineTime;
}